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
import pe.tecsup.ferreteria.ms.servicio.ExcelService;
import pe.tecsup.ferreteria.ms.servicio.PdfService;
import pe.tecsup.ferreteria.ms.servicio.ReporteService;
import pe.tecsup.ferreteria.ms.web.dto.Kardex;
import pe.tecsup.ferreteria.ms.web.dto.ReporteCajas;
import pe.tecsup.ferreteria.ms.web.dto.ReporteStockCritico;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasPeriodo;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia;

/**
 * Reportes gerenciales (solo ADMIN, con el JWT de Django).
 * <pre>
 * GET /api/reportes/ventas-dia?fecha=2026-10-27        JSON
 * GET /api/reportes/ventas-dia/pdf?fecha=2026-10-27    PDF
 * GET /api/reportes/stock-critico                      JSON
 * GET /api/reportes/stock-critico/pdf                  PDF
 * GET /api/reportes/ventas?desde=&hasta=                ventas de un rango (por día, vendedor, categoría…)
 * GET /api/reportes/ventas/pdf | /ventas/excel
 * GET /api/reportes/cajas?desde=&hasta=                 arqueos: faltantes y sobrantes por vendedor
 * GET /api/reportes/cajas/pdf | /cajas/excel
 * GET /api/reportes/kardex?codigo=&desde=&hasta=        kardex de un producto
 * GET /api/reportes/kardex/excel
 * </pre>
 * Sin ?fecha se usa el día de hoy (hora de Lima). Sin rango: los últimos 30 días.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reportes;
    private final PdfService pdf;
    private final ExcelService excel;
    private final AuditoriaService auditoria;
    private final FerreteriaProperties props;

    public ReporteController(ReporteService reportes, PdfService pdf, ExcelService excel,
                             AuditoriaService auditoria, FerreteriaProperties props) {
        this.reportes = reportes;
        this.pdf = pdf;
        this.excel = excel;
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

    // ------------------------------------------------------------------ ventas por rango

    @GetMapping("/ventas")
    public ReporteVentasPeriodo ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate fin = fechaOHoy(hasta);
        return reportes.ventasDelPeriodo(desde != null ? desde : fin.minusDays(29), fin, false);
    }

    @GetMapping("/ventas/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate fin = fechaOHoy(hasta);
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        byte[] archivo = pdf.ventasDelPeriodo(reportes.ventasDelPeriodo(ini, fin, false));
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Ventas del " + ini + " al " + fin + " exportadas a PDF", usuario, req.getRemoteAddr());
        return comoPdf(archivo, "ventas_" + ini + "_" + fin + ".pdf");
    }

    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate fin = fechaOHoy(hasta);
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        byte[] archivo = excel.ventas(reportes.ventasDelPeriodo(ini, fin, true));
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Ventas del " + ini + " al " + fin + " exportadas a Excel", usuario, req.getRemoteAddr());
        return comoExcel(archivo, "ventas_" + ini + "_" + fin + ".xlsx");
    }

    // ------------------------------------------------------------------ cajas

    @GetMapping("/cajas")
    public ReporteCajas cajas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate fin = fechaOHoy(hasta);
        return reportes.cajas(desde != null ? desde : fin.minusDays(29), fin);
    }

    @GetMapping("/cajas/pdf")
    public ResponseEntity<byte[]> cajasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate fin = fechaOHoy(hasta);
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        byte[] archivo = pdf.cajas(reportes.cajas(ini, fin));
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Arqueos del " + ini + " al " + fin + " exportados a PDF", usuario, req.getRemoteAddr());
        return comoPdf(archivo, "cajas_" + ini + "_" + fin + ".pdf");
    }

    @GetMapping("/cajas/excel")
    public ResponseEntity<byte[]> cajasExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate fin = fechaOHoy(hasta);
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        byte[] archivo = excel.cajas(reportes.cajas(ini, fin));
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Arqueos del " + ini + " al " + fin + " exportados a Excel", usuario, req.getRemoteAddr());
        return comoExcel(archivo, "cajas_" + ini + "_" + fin + ".xlsx");
    }

    // ------------------------------------------------------------------ kardex

    @GetMapping("/kardex")
    public Kardex kardex(
            @RequestParam String codigo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate fin = fechaOHoy(hasta);
        return reportes.kardex(codigo, desde != null ? desde : fin.minusDays(29), fin);
    }

    @GetMapping("/kardex/excel")
    public ResponseEntity<byte[]> kardexExcel(
            @RequestParam String codigo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario, HttpServletRequest req) {
        LocalDate fin = fechaOHoy(hasta);
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        Kardex k = reportes.kardex(codigo, ini, fin);
        auditoria.registrarInterno("REPORTE_EXPORTADO", "Kardex de " + k.codigo() + " exportado a Excel", usuario, req.getRemoteAddr());
        return comoExcel(excel.kardex(k), "kardex_" + k.codigo() + "_" + ini + "_" + fin + ".xlsx");
    }

    private LocalDate fechaOHoy(LocalDate fecha) {
        return fecha != null ? fecha : LocalDate.now(props.zona());
    }

    private static ResponseEntity<byte[]> comoExcel(byte[] archivo, String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(archivo);
    }

    private static ResponseEntity<byte[]> comoPdf(byte[] archivo, String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nombre).build().toString())
                .body(archivo);
    }
}
