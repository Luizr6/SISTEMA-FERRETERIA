package pe.tecsup.ferreteria.ms.servicio;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.servicio.facturacion.ComprobanteService;

/**
 * Procesos que corren solos, sin que nadie los pida:
 * <ul>
 *   <li>Cada minuto: emite los comprobantes de las ventas nuevas y da de baja los de ventas anuladas.</li>
 *   <li>Todos los días a las 23:00 (hora de Lima): cierre del día.</li>
 * </ul>
 * Se desactivan con {@code ferreteria.tareas.activas=false}.
 */
@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);
    private static final UsuarioToken SISTEMA = new UsuarioToken(null, "tarea-programada", "SISTEMA", true);

    private final ComprobanteService comprobantes;
    private final CierreService cierres;
    private final FerreteriaProperties props;

    public TareasProgramadas(ComprobanteService comprobantes, CierreService cierres, FerreteriaProperties props) {
        this.comprobantes = comprobantes;
        this.cierres = cierres;
        this.props = props;
    }

    @Scheduled(initialDelayString = "${ferreteria.tareas.espera-inicial-ms:20000}",
               fixedDelayString = "${ferreteria.tareas.cada-ms:60000}")
    public void procesarComprobantes() {
        if (!props.tareasActivas()) {
            return;
        }
        try {
            int emitidos = comprobantes.emitirPendientes(props.diasPendientes());
            int anulados = comprobantes.sincronizarAnulaciones();
            if (emitidos + anulados > 0) {
                log.info("Facturación automática: {} comprobante(s) emitido(s), {} dado(s) de baja", emitidos, anulados);
            }
        } catch (RuntimeException e) {
            // Base apagada o tablas de Django aún no migradas: se reintenta en el siguiente ciclo
            log.warn("Facturación automática no disponible: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${ferreteria.tareas.cierre-cron:0 0 23 * * *}", zone = "${ferreteria.zona-horaria:America/Lima}")
    public void cierreDelDia() {
        if (!props.tareasActivas()) {
            return;
        }
        try {
            comprobantes.emitirPendientes(1);
            cierres.ejecutar(LocalDate.now(props.zona()), "PROGRAMADO", SISTEMA);
        } catch (RuntimeException e) {
            log.error("No se pudo generar el cierre del día", e);
        }
    }
}
