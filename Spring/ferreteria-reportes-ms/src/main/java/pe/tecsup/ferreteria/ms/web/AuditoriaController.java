package pe.tecsup.ferreteria.ms.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.servicio.AuditoriaService;
import pe.tecsup.ferreteria.ms.web.dto.EventoRequest;
import pe.tecsup.ferreteria.ms.web.dto.EventoResponse;
import pe.tecsup.ferreteria.ms.web.dto.Pagina;
import pe.tecsup.ferreteria.ms.web.dto.ResumenAuditoria;

/**
 * Bitácora independiente.
 * <pre>
 * POST /api/auditoria/eventos     registrar (Django con X-API-Key, o cualquier usuario con JWT)
 * GET  /api/auditoria/eventos     consultar con filtros y paginación (solo ADMIN)
 * GET  /api/auditoria/eventos/{id}
 * GET  /api/auditoria/resumen?dias=7
 * GET  /api/auditoria/tipos       tipos de evento sugeridos
 * </pre>
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse registrar(@Valid @RequestBody EventoRequest evento,
                                    @RequestAttribute(UsuarioToken.ATRIBUTO) UsuarioToken quien,
                                    HttpServletRequest req) {
        return auditoria.registrar(evento, quien, req.getRemoteAddr());
    }

    @GetMapping("/eventos")
    public Pagina<EventoResponse> buscar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) NivelEvento nivel,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "fecha", "id"));
        return Pagina.de(auditoria.buscar(tipo, nivel, usuario, modulo, desde, hasta, pagina));
    }

    @GetMapping("/eventos/{id}")
    public EventoResponse obtener(@PathVariable Long id) {
        return auditoria.obtener(id);
    }

    @GetMapping("/resumen")
    public ResumenAuditoria resumen(@RequestParam(defaultValue = "7") int dias) {
        return auditoria.resumen(dias);
    }

    @GetMapping("/tipos")
    public List<String> tipos() {
        return AuditoriaService.TIPOS_CONOCIDOS;
    }
}
