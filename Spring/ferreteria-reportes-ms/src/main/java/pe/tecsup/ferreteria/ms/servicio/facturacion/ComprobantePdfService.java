package pe.tecsup.ferreteria.ms.servicio.facturacion;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.django.DetalleVenta;
import pe.tecsup.ferreteria.ms.dominio.django.Venta;
import pe.tecsup.ferreteria.ms.dominio.facturacion.Comprobante;

/**
 * Representación impresa del comprobante en dos formatos:
 * <ul>
 *   <li>{@code A5}: hoja para ver, descargar o enviar por WhatsApp.</li>
 *   <li>{@code TICKET}: rollo de 80 mm para la impresora térmica de la caja.</li>
 * </ul>
 * Ambos llevan el código QR y el código hash, como exige SUNAT en la representación impresa.
 */
@Service
public class ComprobantePdfService {

    /** 80 mm en puntos (1 mm = 2.8346 pt) */
    private static final float ANCHO_TICKET = 226.77f;
    private static final int COLUMNAS_TICKET = 42;

    private static final Color NAVY = new Color(15, 30, 61);
    private static final Color GRIS = new Color(233, 237, 242);
    private static final Color GRIS_TEXTO = new Color(85, 85, 85);
    private static final Color ROJO = new Color(176, 0, 32);

    private static final Font TITULO = new Font(Font.HELVETICA, 13, Font.BOLD, NAVY);
    private static final Font NORMAL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
    private static final Font PEQUENA = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, GRIS_TEXTO);
    private static final Font CABECERA = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TOTAL = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);
    private static final Font ANULADO = new Font(Font.HELVETICA, 9, Font.BOLD, ROJO);

    private static final Font MONO = new Font(Font.COURIER, 7.5f, Font.NORMAL, Color.BLACK);
    private static final Font MONO_NEGRITA = new Font(Font.COURIER, 7.5f, Font.BOLD, Color.BLACK);
    private static final Font TICKET_NEGOCIO = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FerreteriaProperties props;
    private final ComprobanteService comprobantes;
    private final QrService qr;
    private final ZoneId zona;

    public ComprobantePdfService(FerreteriaProperties props, ComprobanteService comprobantes, QrService qr) {
        this.props = props;
        this.comprobantes = comprobantes;
        this.qr = qr;
        this.zona = props.zona();
    }

    public byte[] generar(Comprobante c, Venta v, String formato) {
        return "TICKET".equalsIgnoreCase(formato) ? ticket(c, v) : a5(c, v);
    }

    // ------------------------------------------------------------------ A5

    private byte[] a5(Comprobante c, Venta v) {
        FerreteriaProperties.Negocio n = props.negocio();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle(nombreTipo(c) + " " + c.getNumero());
            doc.addAuthor(n.nombre());
            doc.open();

            // Cabecera: negocio a la izquierda, recuadro del comprobante a la derecha
            PdfPTable cabecera = new PdfPTable(new float[] {1.55f, 1});
            cabecera.setWidthPercentage(100);
            PdfPCell negocio = sinBorde();
            negocio.addElement(new Paragraph(n.nombre(), TITULO));
            negocio.addElement(new Paragraph(texto(n.direccion()), NORMAL));
            negocio.addElement(new Paragraph("Tel.: " + texto(n.telefono()), NORMAL));
            cabecera.addCell(negocio);

            PdfPTable recuadro = new PdfPTable(1);
            recuadro.addCell(centrada("RUC " + n.ruc(), NEGRITA, null));
            recuadro.addCell(centrada(nombreTipo(c).toUpperCase(Locale.ROOT) + "\nELECTRÓNICA", NEGRITA, GRIS));
            recuadro.addCell(centrada(c.getNumero(), NEGRITA, null));
            PdfPCell contenedor = new PdfPCell(recuadro);
            contenedor.setBorderColor(NAVY);
            contenedor.setBorderWidth(1);
            contenedor.setPadding(0);
            cabecera.addCell(contenedor);
            doc.add(cabecera);

            // Datos de la venta
            PdfPTable info = new PdfPTable(new float[] {1, 2.3f, 1, 2});
            info.setWidthPercentage(100);
            info.setSpacingBefore(12);
            par(info, "Fecha:", FECHA_HORA.format(c.getFechaEmision().atZone(zona)));
            par(info, "Vendedor:", v.getVendedor().getNombreVisible());
            par(info, "Cliente:", c.getClienteNombre());
            par(info, etiquetaDocumento(c) + ":", c.getClienteDocumento());
            par(info, "Pago:", metodo(v.getMetodoPago()));
            par(info, "Venta:", "N.° " + v.getId() + " · " + legible(v.getOrigen()));
            doc.add(info);

            // Detalle
            PdfPTable detalle = new PdfPTable(new float[] {0.7f, 0.7f, 4, 1.2f, 1.3f});
            detalle.setWidthPercentage(100);
            detalle.setSpacingBefore(10);
            detalle.setHeaderRows(1);
            for (String t : new String[] {"Cant.", "Und.", "Descripción", "P. Unit.", "Importe"}) {
                PdfPCell h = new PdfPCell(new Phrase(t, CABECERA));
                h.setBackgroundColor(NAVY);
                h.setBorderColor(NAVY);
                h.setPadding(4);
                h.setHorizontalAlignment(t.startsWith("P.") || t.equals("Importe") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                detalle.addCell(h);
            }
            for (DetalleVenta d : v.getDetalles()) {
                fila(detalle, String.valueOf(d.getCantidad()), Element.ALIGN_CENTER);
                fila(detalle, d.getProducto().getUnidadMedida(), Element.ALIGN_CENTER);
                fila(detalle, d.getProducto().getNombre() + "  (" + d.getProducto().getCodigo() + ")", Element.ALIGN_LEFT);
                fila(detalle, numero(d.getPrecioUnitario()), Element.ALIGN_RIGHT);
                fila(detalle, numero(d.getSubtotal()), Element.ALIGN_RIGHT);
            }
            doc.add(detalle);

            // Totales a la derecha y QR a la izquierda
            PdfPTable pie = new PdfPTable(new float[] {1, 1.3f});
            pie.setWidthPercentage(100);
            pie.setSpacingBefore(8);
            Image imagen = Image.getInstance(qr.png(comprobantes.textoQr(c), 300));
            imagen.scaleAbsolute(78, 78);
            PdfPCell celdaQr = new PdfPCell(imagen, false);
            celdaQr.setBorder(Rectangle.NO_BORDER);
            pie.addCell(celdaQr);

            PdfPTable totales = new PdfPTable(new float[] {1.4f, 1});
            totales.addCell(derecha("Op. gravada", NORMAL));
            totales.addCell(derecha(soles(c.getOpGravada()), NORMAL));
            totales.addCell(derecha("IGV (" + porcentajeIgv() + "%)", NORMAL));
            totales.addCell(derecha(soles(c.getIgv()), NORMAL));
            PdfPCell et = derecha("TOTAL", TOTAL);
            et.setBorder(Rectangle.TOP);
            et.setBorderColor(NAVY);
            totales.addCell(et);
            PdfPCell val = derecha(soles(c.getTotal()), TOTAL);
            val.setBorder(Rectangle.TOP);
            val.setBorderColor(NAVY);
            totales.addCell(val);
            PdfPCell celdaTotales = new PdfPCell(totales);
            celdaTotales.setBorder(Rectangle.NO_BORDER);
            pie.addCell(celdaTotales);
            doc.add(pie);

            Paragraph son = new Paragraph("SON: " + NumeroALetras.soles(c.getTotal()), NEGRITA);
            son.setSpacingBefore(6);
            doc.add(son);
            doc.add(new Paragraph("Código hash: " + c.getHash(), PEQUENA));

            if (c.esAnulado()) {
                Paragraph anulado = new Paragraph("COMPROBANTE ANULADO"
                        + (c.getFechaAnulacion() != null ? " el " + FECHA_HORA.format(c.getFechaAnulacion().atZone(zona)) : "")
                        + (c.getMotivoAnulacion() != null ? " - " + c.getMotivoAnulacion() : ""), ANULADO);
                anulado.setSpacingBefore(8);
                doc.add(anulado);
            }

            Paragraph leyenda = new Paragraph("Representación impresa de la " + nombreTipo(c).toLowerCase(Locale.ROOT)
                    + " electrónica. Comprobante SIMULADO con fines académicos: no fue enviado a SUNAT.", PEQUENA);
            leyenda.setSpacingBefore(10);
            doc.add(leyenda);
            doc.add(new Paragraph("Emitido por el servicio de facturación (Spring Boot). ¡Gracias por su compra!", PEQUENA));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el comprobante: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ Ticket 80 mm

    private byte[] ticket(Comprobante c, Venta v) {
        FerreteriaProperties.Negocio n = props.negocio();
        String raya = "-".repeat(COLUMNAS_TICKET);

        List<String> cuerpo = new ArrayList<>();
        cuerpo.add("Fecha   : " + FECHA_HORA.format(c.getFechaEmision().atZone(zona)));
        envolver(cuerpo, "Cliente : " + c.getClienteNombre());
        cuerpo.add(String.format("%-8s: %s", etiquetaDocumento(c), c.getClienteDocumento()));
        envolver(cuerpo, "Vendedor: " + v.getVendedor().getNombreVisible());
        cuerpo.add("Pago    : " + metodo(v.getMetodoPago()));
        cuerpo.add(raya);
        cuerpo.add(String.format("%-5s%-27s%10s", "CANT", "DESCRIPCION", "IMPORTE"));
        for (DetalleVenta d : v.getDetalles()) {
            List<String> nombre = partir(d.getProducto().getNombre(), 27);
            cuerpo.add(String.format("%-5s%-27s%10s", d.getCantidad(), nombre.get(0), numero(d.getSubtotal())));
            for (int i = 1; i < nombre.size(); i++) {
                cuerpo.add("     " + nombre.get(i));
            }
            cuerpo.add("     P.U. " + numero(d.getPrecioUnitario()) + " " + texto(d.getProducto().getUnidadMedida()).toLowerCase(Locale.ROOT));
        }
        cuerpo.add(raya);
        cuerpo.add(String.format("%30s%12s", "OP. GRAVADA S/", numero(c.getOpGravada())));
        cuerpo.add(String.format("%30s%12s", "IGV " + porcentajeIgv() + "% S/", numero(c.getIgv())));

        List<String> son = new ArrayList<>();
        envolver(son, "SON: " + NumeroALetras.soles(c.getTotal()));

        // Altura según el contenido (el rollo se corta donde termina)
        float alto = 150 + (cuerpo.size() + son.size()) * 9.5f + 110 + (c.esAnulado() ? 30 : 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ANCHO_TICKET, alto), 9, 9, 10, 10);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle("Ticket " + c.getNumero());
            doc.open();
            centrado(doc, n.nombre(), TICKET_NEGOCIO);
            centrado(doc, "RUC " + n.ruc(), MONO);
            centrado(doc, texto(n.direccion()), MONO);
            centrado(doc, "Tel. " + texto(n.telefono()), MONO);
            linea(doc, raya, MONO);
            centrado(doc, nombreTipo(c).toUpperCase(Locale.ROOT) + " ELECTRONICA", MONO_NEGRITA);
            centrado(doc, c.getNumero(), MONO_NEGRITA);
            linea(doc, raya, MONO);
            for (String l : cuerpo) {
                linea(doc, l, MONO);
            }
            linea(doc, String.format("%30s%12s", "TOTAL S/", numero(c.getTotal())), MONO_NEGRITA);
            for (String l : son) {
                linea(doc, l, MONO);
            }
            if (c.esAnulado()) {
                centrado(doc, "*** COMPROBANTE ANULADO ***", MONO_NEGRITA);
            }
            Image imagen = Image.getInstance(qr.png(comprobantes.textoQr(c), 300));
            imagen.scaleAbsolute(82, 82);
            imagen.setAlignment(Element.ALIGN_CENTER);
            doc.add(imagen);
            Paragraph hash = new Paragraph("Hash: " + c.getHash(), new Font(Font.COURIER, 6, Font.NORMAL, Color.BLACK));
            hash.setAlignment(Element.ALIGN_CENTER);
            doc.add(hash);
            centrado(doc, "Representacion impresa de la", MONO);
            centrado(doc, nombreTipo(c).toLowerCase(Locale.ROOT) + " electronica (SIMULADA)", MONO);
            centrado(doc, "Gracias por su compra!", MONO_NEGRITA);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el ticket: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ utilidades

    private static void centrado(Document doc, String texto, Font f) throws Exception {
        Paragraph p = new Paragraph(texto, f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(f.getSize() + 2);
        doc.add(p);
    }

    private static void linea(Document doc, String texto, Font f) throws Exception {
        Paragraph p = new Paragraph(texto, f);
        p.setLeading(9.5f);
        doc.add(p);
    }

    /** Parte un texto largo en renglones del ancho del ticket. */
    private static void envolver(List<String> destino, String texto) {
        destino.addAll(partir(texto, COLUMNAS_TICKET));
    }

    static List<String> partir(String texto, int ancho) {
        List<String> r = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto == null ? new String[0] : texto.trim().split("\\s+")) {
            while (palabra.length() > ancho) { // palabra más larga que el renglón
                if (actual.length() > 0) {
                    r.add(actual.toString());
                    actual.setLength(0);
                }
                r.add(palabra.substring(0, ancho));
                palabra = palabra.substring(ancho);
            }
            if (actual.length() > 0 && actual.length() + 1 + palabra.length() > ancho) {
                r.add(actual.toString());
                actual.setLength(0);
            }
            if (actual.length() > 0) {
                actual.append(' ');
            }
            actual.append(palabra);
        }
        if (actual.length() > 0 || r.isEmpty()) {
            r.add(actual.toString());
        }
        return r;
    }

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private static PdfPCell centrada(String texto, Font f, Color fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        if (fondo != null) {
            c.setBackgroundColor(fondo);
        }
        return c;
    }

    private static PdfPCell derecha(String texto, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(2);
        return c;
    }

    private static void par(PdfPTable t, String etiqueta, String valor) {
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, NEGRITA));
        e.setBorder(Rectangle.NO_BORDER);
        e.setPadding(1.5f);
        t.addCell(e);
        PdfPCell v = new PdfPCell(new Phrase(texto(valor), NORMAL));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(1.5f);
        t.addCell(v);
    }

    private static void fila(PdfPTable t, String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto(texto), NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(204, 204, 204));
        c.setPadding(3);
        t.addCell(c);
    }

    private static String nombreTipo(Comprobante c) {
        return "FACTURA".equals(c.getTipo()) ? "Factura" : "Boleta de venta";
    }

    private static String etiquetaDocumento(Comprobante c) {
        return switch (c.getClienteTipoDoc()) {
            case "1" -> "DNI";
            case "6" -> "RUC";
            case "4" -> "CE";
            default -> "Doc.";
        };
    }

    private static String metodo(String m) {
        return m == null ? "" : switch (m) {
            case "EFECTIVO" -> "Efectivo";
            case "TARJETA" -> "Tarjeta";
            case "YAPE" -> "Yape";
            case "PLIN" -> "Plin";
            case "TRANSFERENCIA" -> "Transferencia";
            default -> legible(m);
        };
    }

    private static String legible(String clave) {
        if (clave == null || clave.isEmpty()) {
            return "";
        }
        String s = clave.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String porcentajeIgv() {
        return props.igv().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static String numero(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(v == null ? BigDecimal.ZERO : v);
    }

    private static String soles(BigDecimal v) {
        return "S/ " + numero(v);
    }
}
