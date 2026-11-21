package pe.tecsup.ferreteria.ms.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;
import pe.tecsup.ferreteria.ms.dominio.django.Venta;
import pe.tecsup.ferreteria.ms.dominio.facturacion.Comprobante;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.servicio.AuditoriaService;
import pe.tecsup.ferreteria.ms.servicio.facturacion.ComprobantePdfService;
import pe.tecsup.ferreteria.ms.servicio.facturacion.ComprobanteService;
import pe.tecsup.ferreteria.ms.servicio.facturacion.NumeroALetras;
import pe.tecsup.ferreteria.ms.web.dto.ComprobanteResponse;
import pe.tecsup.ferreteria.ms.web.dto.Pagina;

/**
 * Facturación electrónica (simulada).
 * <pre>
 * GET  /api/comprobantes/venta/{id}                       datos del comprobante (lo emite si no existe)
 * GET  /api/comprobantes/venta/{id}/pdf?formato=A5|TICKET representación impresa (A5 o ticket de 80 mm)
 * GET  /api/comprobantes/venta/{id}/xml                   XML UBL 2.1
 * GET  /api/comprobantes?estado=&tipo=&desde=&hasta=      registro de comprobantes (solo ADMIN)
 * POST /api/comprobantes/procesar                         emite pendientes y da de baja anulados (solo ADMIN)
 * </pre>
 * Los de una venta los puede pedir cualquier usuario con sesión (Administrador o Vendedor),
 * igual que en Django; el resto es solo del Administrador.
 */
@RestController
@RequestMapping("/api/comprobantes")
public class ComprobanteController {

    private final ComprobanteService comprobantes;
    private final ComprobantePdfService pdf;
    private final AuditoriaService auditoria;
    private final FerreteriaProperties props;

    public ComprobanteController(ComprobanteService comprobantes, ComprobantePdfService pdf,
                                 AuditoriaService auditoria, FerreteriaProperties props) {
        this.comprobantes = comprobantes;
        this.pdf = pdf;
        this.auditoria = auditoria;
        this.props = props;
    }

    @GetMapping("/venta/{ventaId}")
    public ComprobanteResponse deVenta(@PathVariable Long ventaId) {
        return respuesta(comprobantes.obtenerOEmitir(ventaId));
    }

    @GetMapping("/venta/{ventaId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long ventaId,
                                      @RequestParam(defaultValue = "A5") String formato,
                                      @RequestParam(defaultValue = "false") boolean descargar) {
        Comprobante c = comprobantes.obtenerOEmitir(ventaId);
        Venta v = comprobantes.venta(ventaId);
        String f = formato.toUpperCase(Locale.ROOT);
        if (!f.equals("A5") && !f.equals("TICKET")) {
            throw new IllegalArgumentException("formato debe ser A5 o TICKET.");
        }
        byte[] archivo = pdf.generar(c, v, f);
        String nombre = (f.equals("TICKET") ? "ticket_" : "comprobante_") + c.getNumero() + ".pdf";
        ContentDisposition disp = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(nombre).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("X-Comprobante", c.getNumero())
                .body(archivo);
    }

    @GetMapping("/venta/{ventaId}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long ventaId) {
        Comprobante c = comprobantes.obtenerOEmitir(ventaId);
        String nombre = props.negocio().ruc() + "-" + c.getCodigoTipo() + "-" + c.getNumero() + ".xml";
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(c.getXml().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping
    public Pagina<ComprobanteResponse> registro(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate hoy = LocalDate.now(props.zona());
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate ini = desde != null ? desde : fin.minusDays(30);
        return Pagina.de(comprobantes.buscar(estado, tipo, ini, fin, page, size).map(this::respuesta));
    }

    @PostMapping("/procesar")
    public Map<String, Object> procesar(@RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario,
                                        HttpServletRequest req) {
        int emitidos = comprobantes.emitirPendientes(props.diasPendientes());
        int anulados = comprobantes.sincronizarAnulaciones();
        auditoria.registrarInterno("COMPROBANTES_PROCESADOS",
                "Proceso manual: " + emitidos + " emitido(s), " + anulados + " dado(s) de baja", usuario, req.getRemoteAddr(),
                NivelEvento.INFO, "facturacion");
        return Map.of("emitidos", emitidos, "anulados", anulados);
    }

    private ComprobanteResponse respuesta(Comprobante c) {
        return ComprobanteResponse.de(c, NumeroALetras.soles(c.getTotal()), comprobantes.textoQr(c));
    }
}
