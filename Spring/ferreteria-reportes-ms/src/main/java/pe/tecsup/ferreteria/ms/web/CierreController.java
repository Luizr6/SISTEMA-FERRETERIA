package pe.tecsup.ferreteria.ms.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.servicio.CierreService;
import pe.tecsup.ferreteria.ms.servicio.facturacion.ComprobanteService;
import pe.tecsup.ferreteria.ms.web.dto.CierreResponse;

/**
 * Cierres del día (solo ADMIN).
 * <pre>
 * GET  /api/cierres                 últimos 60 cierres
 * GET  /api/cierres/{fecha}         un cierre (AAAA-MM-DD)
 * POST /api/cierres/ejecutar?fecha= ejecutarlo ahora (la tarea programada lo hace sola a las 23:00)
 * </pre>
 */
@RestController
@RequestMapping("/api/cierres")
public class CierreController {

    private final CierreService cierres;
    private final ComprobanteService comprobantes;
    private final FerreteriaProperties props;

    public CierreController(CierreService cierres, ComprobanteService comprobantes, FerreteriaProperties props) {
        this.cierres = cierres;
        this.comprobantes = comprobantes;
        this.props = props;
    }

    @GetMapping
    public List<CierreResponse> ultimos() {
        return cierres.ultimos();
    }

    @GetMapping("/{fecha}")
    public CierreResponse delDia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return cierres.delDia(fecha);
    }

    @PostMapping("/ejecutar")
    public CierreResponse ejecutar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken usuario) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(props.zona());
        if (dia.isAfter(LocalDate.now(props.zona()))) {
            throw new IllegalArgumentException("No se puede cerrar un día que todavía no llega.");
        }
        comprobantes.emitirPendientes(props.diasPendientes());
        return cierres.ejecutar(dia, "MANUAL", usuario);
    }
}
