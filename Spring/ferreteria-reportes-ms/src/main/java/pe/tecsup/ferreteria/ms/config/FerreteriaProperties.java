package pe.tecsup.ferreteria.ms.config;

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
        String zonaHoraria) {

    public record Jwt(String secret) { }

    public record Auditoria(String apiKey, int intentosFallidosAlerta, int ventanaMinutos) { }

    public record Cors(List<String> origenes) { }

    public record Negocio(String nombre, String ruc) { }

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? "America/Lima" : zonaHoraria);
    }
}
