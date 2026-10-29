package pe.tecsup.ferreteria.ms.web.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;

/**
 * Cuerpo de POST /api/auditoria/eventos (JSON en snake_case).
 * <pre>
 * {"tipo": "PRECIO_MODIFICADO", "descripcion": "MAR-001: S/ 32.00 a S/ 35.00",
 *  "usuario": "demo_admin", "usuario_id": 1, "modulo": "productos",
 *  "entidad": "Producto", "entidad_id": "7", "datos": {"antes": "32.00", "despues": "35.00"},
 *  "ip": "127.0.0.1", "origen": "DJANGO"}
 * </pre>
 */
public record EventoRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}", message = "usa MAYUSCULAS_CON_GUION_BAJO (3 a 40 caracteres)")
        String tipo,
        @NotBlank @Size(max = 500)
        String descripcion,
        NivelEvento nivel,
        @Size(max = 20) String origen,
        @Size(max = 40) String modulo,
        @Size(max = 150) String usuario,
        Long usuarioId,
        @Size(max = 60) String entidad,
        @Size(max = 40) String entidadId,
        Map<String, Object> datos,
        @Size(max = 45) String ip,
        /** Opcional: si Django manda la hora exacta del hecho; si no, se usa la de llegada. */
        Instant fecha) { }
