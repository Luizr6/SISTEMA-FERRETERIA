package pe.tecsup.ferreteria.ms.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades {@code ferreteria.*} de application.properties. */
@ConfigurationProperties(prefix = "ferreteria")
public record FerreteriaProperties(
        Jwt jwt,
        Auditoria auditoria,
        Cors cors,
        Negocio negocio,
        String zonaHoraria,
        BigDecimal igvTasa,
        Tareas tareas) {

    public record Jwt(String secret) { }

    public record Auditoria(String apiKey, int intentosFallidosAlerta, int ventanaMinutos) { }

    public record Cors(List<String> origenes) { }

    public record Negocio(String nombre, String ruc, String direccion, String telefono) { }

    /** Procesos programados: emisión automática de comprobantes y cierre del día. */
    public record Tareas(boolean activas, int diasPendientes) { }

    /** Tasa del IGV (0.18 por defecto, igual que IGV_TASA de Django). */
    public BigDecimal igv() {
        return igvTasa == null ? new BigDecimal("0.18") : igvTasa;
    }

    public boolean tareasActivas() {
        return tareas == null || tareas.activas();
    }

    public int diasPendientes() {
        return tareas == null || tareas.diasPendientes() <= 0 ? 7 : tareas.diasPendientes();
    }

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? "America/Lima" : zonaHoraria);
    }
}
