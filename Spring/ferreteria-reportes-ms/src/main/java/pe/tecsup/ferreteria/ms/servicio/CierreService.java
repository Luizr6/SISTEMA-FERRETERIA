package pe.tecsup.ferreteria.ms.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;
import pe.tecsup.ferreteria.ms.dominio.cierre.CierreDiario;
import pe.tecsup.ferreteria.ms.dominio.django.Caja;
import pe.tecsup.ferreteria.ms.repositorio.CierreDiarioRepository;
import pe.tecsup.ferreteria.ms.repositorio.ComprobanteRepository;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.web.dto.CierreResponse;
import pe.tecsup.ferreteria.ms.web.dto.ReporteCajas;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia;

/**
 * Cierre del día: junta en un solo registro lo vendido, el estado de las cajas, los comprobantes emitidos
 * y el stock crítico, y deja alertas para el Administrador (cajas sin cerrar, faltantes, productos agotados).
 */
@Service
public class CierreService {

    private static final Logger log = LoggerFactory.getLogger(CierreService.class);
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final ReporteService reportes;
    private final CierreDiarioRepository repo;
    private final ComprobanteRepository comprobantes;
    private final AuditoriaService auditoria;
    private final ZoneId zona;

    public CierreService(ReporteService reportes, CierreDiarioRepository repo, ComprobanteRepository comprobantes,
                         AuditoriaService auditoria, FerreteriaProperties props) {
        this.reportes = reportes;
        this.repo = repo;
        this.comprobantes = comprobantes;
        this.auditoria = auditoria;
        this.zona = props.zona();
    }

    @Transactional
    public CierreResponse ejecutar(LocalDate fecha, String origen, UsuarioToken quien) {
        ReporteVentasDia v = reportes.ventasDelDia(fecha);
        ReporteCajas c = reportes.cajas(fecha, fecha);
        var stock = reportes.stockCritico();
        long criticos = stock.totalCriticos();
        long agotados = stock.agotados();
        List<Caja> abiertas = reportes.cajasAbiertas();

        BigDecimal efectivo = v.porMetodoPago().stream().filter(f -> "EFECTIVO".equals(f.clave()))
                .map(ReporteVentasDia.Fila::total).reduce(CERO, BigDecimal::add);
        BigDecimal otros = v.total().subtract(efectivo).setScale(2, RoundingMode.HALF_UP);
        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        long emitidos = comprobantes.countByFechaEmisionGreaterThanEqualAndFechaEmisionLessThan(desde, hasta);

        List<String> alertas = new ArrayList<>();
        for (Caja a : abiertas) {
            alertas.add("Caja de " + a.getUsuario().getNombreVisible() + " sigue abierta (desde el "
                    + a.getFechaApertura().atZone(zona).toLocalDate() + ").");
        }
        for (ReporteCajas.CajaFila f : c.detalle()) {
            if (f.diferencia() != null && f.diferencia().signum() < 0) {
                alertas.add("Faltante de S/ " + f.diferencia().negate().toPlainString() + " en la caja de " + f.vendedor() + ".");
            }
        }
        if (agotados > 0) {
            alertas.add(agotados + " producto(s) agotado(s).");
        }
        if (v.ventasCompletadas() > emitidos) {
            alertas.add((v.ventasCompletadas() - emitidos) + " venta(s) sin comprobante emitido todavía.");
        }

        CierreDiario cierre = repo.findByFecha(fecha).orElseGet(() -> new CierreDiario(fecha));
        cierre.ventas(v.ventasCompletadas(), v.ventasAnuladas(), v.total(), v.ticketPromedio(), efectivo, otros);
        cierre.cajas(c.cajas() - c.abiertas(), abiertas.size(), c.totalFaltante(), c.totalSobrante());
        cierre.resto(emitidos, criticos, String.join("\n", alertas), origen);
        CierreDiario guardado = repo.save(cierre);

        auditoria.registrarInterno("CIERRE_DIARIO", "Cierre del " + fecha + ": " + v.ventasCompletadas()
                + " venta(s) por S/ " + v.total().toPlainString() + ", " + alertas.size() + " alerta(s)", quien, null,
                alertas.isEmpty() ? NivelEvento.INFO : NivelEvento.ADVERTENCIA, "cierre");
        log.info("Cierre del {} generado ({}): {} alertas", fecha, origen, alertas.size());
        return CierreResponse.de(guardado);
    }

    @Transactional(readOnly = true)
    public List<CierreResponse> ultimos() {
        return repo.findTop60ByOrderByFechaDesc().stream().map(CierreResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public CierreResponse delDia(LocalDate fecha) {
        return repo.findByFecha(fecha).map(CierreResponse::de)
                .orElseThrow(() -> new java.util.NoSuchElementException("Todavía no hay cierre del " + fecha + "."));
    }
}
