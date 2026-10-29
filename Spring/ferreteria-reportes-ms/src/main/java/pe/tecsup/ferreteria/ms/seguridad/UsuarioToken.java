package pe.tecsup.ferreteria.ms.seguridad;

/**
 * Quién hace la petición: un usuario de Django (por su JWT) o el propio backend de Django
 * (por la API key de servicio).
 */
public record UsuarioToken(Long id, String username, String rol, boolean servicio) {

    public static final String ATRIBUTO = "ferreteria.usuario";

    public static UsuarioToken servicioDjango() {
        return new UsuarioToken(null, "servicio-django", "SERVICIO", true);
    }

    public boolean esAdmin() {
        return "ADMIN".equals(rol);
    }
}
