package pe.tecsup.ferreteria.ms.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.servicio.AuditoriaService;
import pe.tecsup.ferreteria.ms.servicio.PdfService;
import pe.tecsup.ferreteria.ms.servicio.ReporteService;
import pe.tecsup.ferreteria.ms.web.dto.ReporteStockCritico;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia;

/**
 * Reportes gerenciales (solo ADMIN, con el JWT de Django).
 * <pre>
 * GET /api/reportes/ventas-dia?fecha=2026-10-27        JSON
 * GET /api/reportes/ventas-dia/pdf?fecha=2026-10-27    PDF
 * GET /api/reportes/stock-critico                      JSON
 * GET /api/reportes/stock-critico/pdf                  PDF
 * </pre>
 * Sin ?fecha se usa el día de hoy (hora de Lima).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reportes;
    private final PdfService pdf;
    private final AuditoriaService auditoria;
    private final FerreteriaProperties props;

    public ReporteController(ReporteService reportes, PdfService pdf, AuditoriaService auditoria, FerreteriaProperties props) {
        this.reportes = reportes;
        this.pdf = pdf;
        this.auditoria = auditoria;
        this.props = props;
    }

    @GetMapping("/ventas-dia")
    public ReporteVentasDia ventasDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reportes.ventasDelDia(fechaOHoy(fecha));
    }

    @GetMapping("/ventas-dia/pdf")
    public ResponseEntity<byte[]> ventasDelDiaPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate dia = fechaOHoy(fecha);
        byte[] archivo = pdf.ventasDelDia(reportes.ventasDelDia(dia));
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Reporte de ventas del " + dia + " exportado a PDF", usuario, req.getRemoteAddr());
        return comoPdf(archivo, "ventas_" + dia + ".pdf");
    }

    @GetMapping("/stock-critico")
    public ReporteStockCritico stockCritico() {
        return reportes.stockCritico();
    }

    @GetMapping("/stock-critico/pdf")
    public ResponseEntity<byte[]> stockCriticoPdf(@RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario,
                                                  HttpServletRequest req) {
        byte[] archivo = pdf.stockCritico(reportes.stockCritico());
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Reporte de stock crítico exportado a PDF", usuario, req.getRemoteAddr());
        return comoPdf(archivo, "stock_critico_" + LocalDate.now(props.zona()) + ".pdf");
    }

    private LocalDate fechaOHoy(LocalDate fecha) {
        return fecha != null ? fecha : LocalDate.now(props.zona());
    }

    private static ResponseEntity<byte[]> comoPdf(byte[] archivo, String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nombre).build().toString())
                .body(archivo);
    }
}
