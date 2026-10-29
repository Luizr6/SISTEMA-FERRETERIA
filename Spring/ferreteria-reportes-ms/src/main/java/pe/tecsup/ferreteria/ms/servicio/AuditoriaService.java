package pe.tecsup.ferreteria.ms.servicio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;
import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.auditoria.EventoAuditoria;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;
import pe.tecsup.ferreteria.ms.repositorio.EventoAuditoriaRepository;
import pe.tecsup.ferreteria.ms.seguridad.UsuarioToken;
import pe.tecsup.ferreteria.ms.web.dto.EventoRequest;
import pe.tecsup.ferreteria.ms.web.dto.EventoResponse;
import pe.tecsup.ferreteria.ms.web.dto.ResumenAuditoria;

/** Registro y consulta de la bitácora independiente del microservicio. */
@Service
public class AuditoriaService {

    /** Tipos sugeridos (se aceptan otros con el formato MAYUSCULAS_CON_GUION_BAJO). */
    public static final List<String> TIPOS_CONOCIDOS = List.of(
            "LOGIN_EXITOSO", "LOGIN_FALLIDO", "CONTRASENA_CAMBIADA",
            "PEDIDO_APROBADO", "PEDIDO_RECHAZADO", "VENTA_REGISTRADA", "VENTA_ANULADA",
            "PRECIO_MODIFICADO", "STOCK_AJUSTADO", "COMPRA_RECIBIDA", "IMPORTACION_CSV",
            "REGISTRO_CREADO", "REGISTRO_EDITADO", "REGISTRO_ELIMINADO", "REPORTE_EXPORTADO");

    /** Si el emisor no indica nivel, estos tipos se guardan como ADVERTENCIA. */
    private static final Set<String> ADVERTENCIA_POR_DEFECTO = Set.of(
            "LOGIN_FALLIDO", "VENTA_ANULADA", "PRECIO_MODIFICADO", "STOCK_AJUSTADO", "REGISTRO_ELIMINADO");

    private final EventoAuditoriaRepository repo;
    private final ObjectMapper json;
    private final FerreteriaProperties props;
    private final ZoneId zona;

    public AuditoriaService(EventoAuditoriaRepository repo, ObjectMapper json, FerreteriaProperties props) {
        this.repo = repo;
        this.json = json;
        this.props = props;
        this.zona = props.zona();
    }

    @Transactional
    public EventoResponse registrar(EventoRequest r, UsuarioToken quien, String ipRemota) {
        String tipo = r.tipo().toUpperCase(Locale.ROOT);
        NivelEvento nivel = r.nivel() != null ? r.nivel()
                : ADVERTENCIA_POR_DEFECTO.contains(tipo) ? NivelEvento.ADVERTENCIA : NivelEvento.INFO;
        String origen = r.origen() != null ? r.origen().toUpperCase(Locale.ROOT) : (quien.servicio() ? "DJANGO" : "API");
        String usuario = r.usuario() != null ? r.usuario() : (quien.servicio() ? null : quien.username());
        String descripcion = r.descripcion();

        // Varios intentos fallidos del mismo usuario en poco tiempo => CRITICO (posible fuerza bruta)
        if ("LOGIN_FALLIDO".equals(tipo) && usuario != null) {
            var cfg = props.auditoria();
            Instant ventana = Instant.now().minus(Duration.ofMinutes(cfg.ventanaMinutos()));
            long previos = repo.countByTipoAndUsuarioIgnoreCaseAndFechaAfter(tipo, usuario, ventana);
            if (previos + 1 >= cfg.intentosFallidosAlerta()) {
                nivel = NivelEvento.CRITICO;
                descripcion = recortar(descripcion + " (" + (previos + 1) + " intentos fallidos en "
                        + cfg.ventanaMinutos() + " min)", 500);
            }
        }

        EventoAuditoria e = new EventoAuditoria(tipo, nivel, recortar(origen, 20), descripcion);
        if (r.fecha() != null) {
            e.setFecha(r.fecha());
        }
        e.setModulo(r.modulo());
        e.setUsuario(usuario);
        e.setUsuarioId(r.usuarioId() != null ? r.usuarioId() : (quien.servicio() ? null : quien.id()));
        e.setEntidad(r.entidad());
        e.setEntidadId(r.entidadId());
        e.setIp(r.ip() != null ? r.ip() : ipRemota);
        if (r.datos() != null && !r.datos().isEmpty()) {
            try {
                e.setDatos(json.writeValueAsString(r.datos()));
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("El campo datos no es un JSON válido.");
            }
        }
        return aRespuesta(repo.save(e));
    }

    /** Evento generado por el propio microservicio (por ejemplo, al exportar un PDF). */
    @Transactional
    public void registrarInterno(String tipo, String descripcion, UsuarioToken quien, String ip) {
        EventoAuditoria e = new EventoAuditoria(tipo, NivelEvento.INFO, "MS", recortar(descripcion, 500));
        e.setModulo("reportes");
        e.setUsuario(quien.username());
        e.setUsuarioId(quien.id());
        e.setIp(ip);
        repo.save(e);
    }

    @Transactional(readOnly = true)
    public Page<EventoResponse> buscar(String tipo, NivelEvento nivel, String usuario, String modulo,
                                       LocalDate desde, LocalDate hasta, Pageable pagina) {
        Specification<EventoAuditoria> filtros = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tipo != null && !tipo.isBlank()) {
                p.add(cb.equal(root.get("tipo"), tipo.toUpperCase(Locale.ROOT)));
            }
            if (nivel != null) {
                p.add(cb.equal(root.get("nivel"), nivel));
            }
            if (usuario != null && !usuario.isBlank()) {
                p.add(cb.like(cb.lower(root.<String>get("usuario")), "%" + usuario.toLowerCase(Locale.ROOT) + "%"));
            }
            if (modulo != null && !modulo.isBlank()) {
                p.add(cb.equal(root.get("modulo"), modulo));
            }
            if (desde != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("fecha"), desde.atStartOfDay(zona).toInstant()));
            }
            if (hasta != null) {
                p.add(cb.lessThan(root.<Instant>get("fecha"), hasta.plusDays(1).atStartOfDay(zona).toInstant()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return repo.findAll(filtros, pagina).map(this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public EventoResponse obtener(Long id) {
        return repo.findById(id).map(this::aRespuesta)
                .orElseThrow(() -> new NoSuchElementException("No existe el evento " + id + "."));
    }

    @Transactional(readOnly = true)
    public ResumenAuditoria resumen(int dias) {
        int d = Math.max(1, Math.min(dias, 365));
        Instant desde = LocalDate.now(zona).minusDays(d - 1L).atStartOfDay(zona).toInstant();

        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (Object[] fila : repo.contarPorTipo(desde)) {
            porTipo.put((String) fila[0], (Long) fila[1]);
        }
        Map<String, Long> porNivel = new LinkedHashMap<>();
        for (NivelEvento n : NivelEvento.values()) {
            porNivel.put(n.name(), 0L);
        }
        for (Object[] fila : repo.contarPorNivel(desde)) {
            porNivel.put(((NivelEvento) fila[0]).name(), (Long) fila[1]);
        }
        long total = porTipo.values().stream().mapToLong(Long::longValue).sum();
        List<EventoResponse> criticos = repo.findTop10ByNivelOrderByFechaDesc(NivelEvento.CRITICO)
                .stream().map(this::aRespuesta).toList();
        return new ResumenAuditoria(d, desde, total, porTipo, porNivel, criticos);
    }

    private EventoResponse aRespuesta(EventoAuditoria e) {
        JsonNode datos = null;
        if (e.getDatos() != null) {
            try {
                datos = json.readTree(e.getDatos());
            } catch (JsonProcessingException ex) {
                datos = json.getNodeFactory().textNode(e.getDatos());
            }
        }
        return new EventoResponse(e.getId(), e.getFecha(), e.getTipo(), e.getNivel(), e.getOrigen(), e.getModulo(),
                e.getUsuario(), e.getUsuarioId(), e.getEntidad(), e.getEntidadId(), e.getDescripcion(), datos, e.getIp());
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
