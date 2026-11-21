package pe.tecsup.ferreteria.ms.servicio.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.django.Cliente;
import pe.tecsup.ferreteria.ms.dominio.django.DetalleVenta;
import pe.tecsup.ferreteria.ms.dominio.django.Venta;
import pe.tecsup.ferreteria.ms.dominio.facturacion.Comprobante;
import pe.tecsup.ferreteria.ms.repositorio.ComprobanteRepository;
import pe.tecsup.ferreteria.ms.repositorio.VentaRepository;

/**
 * Facturación: convierte una venta de Django en un comprobante electrónico.
 * <ol>
 *   <li>Decide el tipo: FACTURA (01) si la serie empieza con F (cliente con RUC); si no, BOLETA (03).</li>
 *   <li>Arma el XML en formato UBL 2.1, el estándar que pide SUNAT.</li>
 *   <li>Calcula el código hash (SHA-256 del XML) que va impreso en el comprobante.</li>
 *   <li>Guarda el registro en {@code ms_comprobante}.</li>
 * </ol>
 * La serie y el número los asigna Django al registrar la venta, así una venta nunca se detiene
 * aunque este servicio esté apagado: la tarea programada emite los pendientes al volver.
 * Simulado: no se firma con certificado digital ni se envía a SUNAT.
 */
@Service
public class ComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ComprobanteRepository repo;
    private final VentaRepository ventas;
    private final FerreteriaProperties props;
    private final ZoneId zona;

    public ComprobanteService(ComprobanteRepository repo, VentaRepository ventas, FerreteriaProperties props) {
        this.repo = repo;
        this.ventas = ventas;
        this.props = props;
        this.zona = props.zona();
    }

    /** Devuelve el comprobante de la venta; si todavía no existe, lo emite en ese momento. */
    public Comprobante obtenerOEmitir(Long ventaId) {
        return repo.findByVentaId(ventaId).orElseGet(() -> {
            try {
                return emitir(ventaId);
            } catch (DataIntegrityViolationException e) {
                // La tarea programada lo emitió al mismo tiempo: se usa el que quedó guardado
                return repo.findByVentaId(ventaId).orElseThrow(() -> e);
            }
        });
    }

    public Venta venta(Long ventaId) {
        return ventas.buscarConDetalle(ventaId)
                .orElseThrow(() -> new NoSuchElementException("No existe la venta " + ventaId + "."));
    }

    /** Emite el comprobante de una venta (una sola vez por venta). */
    public synchronized Comprobante emitir(Long ventaId) {
        var existente = repo.findByVentaId(ventaId);
        if (existente.isPresent()) {
            return existente.get();
        }
        Venta v = venta(ventaId);
        if (v.getNumero() == null || !v.getNumero().contains("-")) {
            throw new IllegalArgumentException("La venta " + ventaId + " todavía no tiene número de comprobante.");
        }
        String[] partes = v.getNumero().split("-", 2);
        String serie = partes[0].trim().toUpperCase(Locale.ROOT);
        int correlativo;
        try {
            correlativo = Integer.parseInt(partes[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El número " + v.getNumero() + " no tiene el formato SERIE-NÚMERO.");
        }
        boolean factura = serie.startsWith("F");
        Comprobante c = new Comprobante(v.getId(), factura ? "FACTURA" : "BOLETA", factura ? "01" : "03",
                serie, correlativo, v.getFecha());

        Cliente cli = v.getCliente();
        String documento = cli != null && cli.getNumeroDocumento() != null && !cli.getNumeroDocumento().isBlank()
                ? cli.getNumeroDocumento().trim() : "-";
        String tipoDoc = "-".equals(documento) ? "0" : codigoDocumento(cli.getTipoDocumento());
        c.cliente(tipoDoc, documento, cli != null ? recortar(cli.getNombre(), 150) : "CLIENTES VARIOS");
        c.montos(dos(v.getSubtotal()), dos(v.getIgv()), dos(v.getTotal()));

        String xml = xmlUbl(c, v);
        c.documento(xml, hash(xml));
        if (!v.esCompletada()) {
            c.anular(v.getFechaAnulacion(), v.getMotivoAnulacion());
        }
        Comprobante guardado = repo.saveAndFlush(c);
        log.info("Comprobante {} emitido (venta {})", guardado.getNumero(), ventaId);
        return guardado;
    }

    /** Emite los comprobantes de las ventas recientes que aún no lo tienen. Devuelve cuántos emitió. */
    public int emitirPendientes(int dias) {
        Instant desde = LocalDate.now(zona).minusDays(dias).atStartOfDay(zona).toInstant();
        int emitidos = 0;
        for (Long id : ventas.idsSinComprobante(desde)) {
            try {
                emitir(id);
                emitidos++;
            } catch (RuntimeException e) {
                log.warn("No se pudo emitir el comprobante de la venta {}: {}", id, e.getMessage());
            }
        }
        return emitidos;
    }

    /** Da de baja los comprobantes cuya venta se anuló en Django. Devuelve cuántos anuló. */
    public int sincronizarAnulaciones() {
        int anulados = 0;
        for (Long id : repo.idsPorAnular()) {
            Comprobante c = repo.findById(id).orElse(null);
            if (c == null) {
                continue;
            }
            Venta v = ventas.buscarConDetalle(c.getVentaId()).orElse(null);
            c.anular(v != null ? v.getFechaAnulacion() : null, v != null ? v.getMotivoAnulacion() : null);
            repo.save(c);
            anulados++;
        }
        return anulados;
    }

    public Page<Comprobante> buscar(String estado, String tipo, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Instant ini = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        return repo.buscar(filtro(estado), filtro(tipo), ini, fin,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
    }

    /**
     * Texto del QR con el formato que define SUNAT:
     * RUC | TIPO | SERIE | NÚMERO | IGV | TOTAL | FECHA | TIPO DOC CLIENTE | DOC CLIENTE | HASH |
     */
    public String textoQr(Comprobante c) {
        return String.join("|", props.negocio().ruc(), c.getCodigoTipo(), c.getSerie(),
                String.format("%06d", c.getCorrelativo()), c.getIgv().toPlainString(), c.getTotal().toPlainString(),
                FECHA.format(c.getFechaEmision().atZone(zona)), c.getClienteTipoDoc(), c.getClienteDocumento(),
                c.getHash()) + "|";
    }

    // ------------------------------------------------------------------ XML UBL 2.1

    String xmlUbl(Comprobante c, Venta v) {
        ZonedDateTime fecha = c.getFechaEmision().atZone(zona);
        FerreteriaProperties.Negocio n = props.negocio();
        BigDecimal tasa = props.igv();
        StringBuilder x = new StringBuilder(4096);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
         .append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"\n")
         .append("         xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n")
         .append("         xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">\n")
         .append("  <!-- Comprobante SIMULADO: sin firma digital y no enviado a SUNAT -->\n")
         .append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n")
         .append("  <cbc:CustomizationID>2.0</cbc:CustomizationID>\n");
        tag(x, 2, "cbc:ID", c.getNumero());
        tag(x, 2, "cbc:IssueDate", FECHA.format(fecha));
        tag(x, 2, "cbc:IssueTime", HORA.format(fecha));
        x.append("  <cbc:InvoiceTypeCode listID=\"0101\">").append(c.getCodigoTipo()).append("</cbc:InvoiceTypeCode>\n");
        x.append("  <cbc:Note languageLocaleID=\"1000\">").append(esc(NumeroALetras.soles(c.getTotal()))).append("</cbc:Note>\n");
        tag(x, 2, "cbc:DocumentCurrencyCode", "PEN");
        tag(x, 2, "cbc:LineCountNumeric", String.valueOf(v.getDetalles().size()));

        // Emisor
        x.append("  <cac:AccountingSupplierParty><cac:Party>\n")
         .append("    <cac:PartyIdentification><cbc:ID schemeID=\"6\">").append(esc(n.ruc())).append("</cbc:ID></cac:PartyIdentification>\n")
         .append("    <cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(n.nombre())).append("</cbc:RegistrationName>\n")
         .append("      <cac:RegistrationAddress><cac:AddressLine><cbc:Line>").append(esc(valor(n.direccion())))
         .append("</cbc:Line></cac:AddressLine></cac:RegistrationAddress></cac:PartyLegalEntity>\n")
         .append("  </cac:Party></cac:AccountingSupplierParty>\n");

        // Cliente
        x.append("  <cac:AccountingCustomerParty><cac:Party>\n")
         .append("    <cac:PartyIdentification><cbc:ID schemeID=\"").append(c.getClienteTipoDoc()).append("\">")
         .append(esc(c.getClienteDocumento())).append("</cbc:ID></cac:PartyIdentification>\n")
         .append("    <cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(c.getClienteNombre()))
         .append("</cbc:RegistrationName></cac:PartyLegalEntity>\n")
         .append("  </cac:Party></cac:AccountingCustomerParty>\n");

        // Forma de pago
        x.append("  <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Contado</cbc:PaymentMeansID></cac:PaymentTerms>\n");

        // IGV total
        x.append("  <cac:TaxTotal>\n");
        monto(x, 4, "cbc:TaxAmount", c.getIgv());
        x.append("    <cac:TaxSubtotal>\n");
        monto(x, 6, "cbc:TaxableAmount", c.getOpGravada());
        monto(x, 6, "cbc:TaxAmount", c.getIgv());
        x.append("      <cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name>")
         .append("<cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>\n")
         .append("    </cac:TaxSubtotal>\n  </cac:TaxTotal>\n");

        // Totales
        x.append("  <cac:LegalMonetaryTotal>\n");
        monto(x, 4, "cbc:LineExtensionAmount", c.getOpGravada());
        monto(x, 4, "cbc:TaxInclusiveAmount", c.getTotal());
        monto(x, 4, "cbc:PayableAmount", c.getTotal());
        x.append("  </cac:LegalMonetaryTotal>\n");

        // Líneas (precios de Django sin IGV)
        int i = 1;
        for (DetalleVenta d : v.getDetalles()) {
            BigDecimal valorVenta = dos(d.getSubtotal());
            BigDecimal igvLinea = valorVenta.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioConIgv = d.getPrecioUnitario().multiply(BigDecimal.ONE.add(tasa)).setScale(2, RoundingMode.HALF_UP);
            x.append("  <cac:InvoiceLine>\n");
            tag(x, 4, "cbc:ID", String.valueOf(i++));
            x.append("    <cbc:InvoicedQuantity unitCode=\"").append(unidadSunat(d.getProducto().getUnidadMedida()))
             .append("\">").append(d.getCantidad()).append("</cbc:InvoicedQuantity>\n");
            monto(x, 4, "cbc:LineExtensionAmount", valorVenta);
            x.append("    <cac:PricingReference><cac:AlternativeConditionPrice>");
            x.append("<cbc:PriceAmount currencyID=\"PEN\">").append(precioConIgv.toPlainString()).append("</cbc:PriceAmount>");
            x.append("<cbc:PriceTypeCode>01</cbc:PriceTypeCode></cac:AlternativeConditionPrice></cac:PricingReference>\n");
            x.append("    <cac:TaxTotal>");
            x.append("<cbc:TaxAmount currencyID=\"PEN\">").append(igvLinea.toPlainString()).append("</cbc:TaxAmount>");
            x.append("</cac:TaxTotal>\n");
            x.append("    <cac:Item><cbc:Description>").append(esc(d.getProducto().getNombre())).append("</cbc:Description>")
             .append("<cac:SellersItemIdentification><cbc:ID>").append(esc(d.getProducto().getCodigo()))
             .append("</cbc:ID></cac:SellersItemIdentification></cac:Item>\n");
            x.append("    <cac:Price><cbc:PriceAmount currencyID=\"PEN\">").append(dos(d.getPrecioUnitario()).toPlainString())
             .append("</cbc:PriceAmount></cac:Price>\n");
            x.append("  </cac:InvoiceLine>\n");
        }
        x.append("</Invoice>\n");
        return x.toString();
    }

    static String hash(String xml) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Catálogo 06 de SUNAT */
    static String codigoDocumento(String tipo) {
        if (tipo == null) {
            return "0";
        }
        return switch (tipo.toUpperCase(Locale.ROOT)) {
            case "DNI" -> "1";
            case "RUC" -> "6";
            case "CE" -> "4";
            default -> "0";
        };
    }

    /** Catálogo 03 de SUNAT (unidades de medida) */
    static String unidadSunat(String unidad) {
        if (unidad == null) {
            return "NIU";
        }
        return switch (unidad.toUpperCase(Locale.ROOT)) {
            case "KG" -> "KGM";
            case "M", "METRO", "METROS" -> "MTR";
            case "L", "LITRO", "LITROS" -> "LTR";
            case "CAJA" -> "BX";
            case "BOLSA" -> "BG";
            case "ROLLO" -> "RO";
            case "GALON", "GALÓN" -> "GLL";
            default -> "NIU"; // unidad (UND, PZA…)
        };
    }

    private static void tag(StringBuilder x, int sangria, String nombre, String valor) {
        x.append(" ".repeat(sangria)).append('<').append(nombre).append('>').append(esc(valor))
         .append("</").append(nombre).append(">\n");
    }

    private static void monto(StringBuilder x, int sangria, String nombre, BigDecimal valor) {
        x.append(" ".repeat(sangria)).append('<').append(nombre).append(" currencyID=\"PEN\">")
         .append(dos(valor).toPlainString()).append("</").append(nombre).append(">\n");
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static BigDecimal dos(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String valor(String s) {
        return s == null ? "" : s;
    }

    private static String recortar(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }

    private static String filtro(String s) {
        return s == null || s.isBlank() ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
