package pe.tecsup.ferreteria.ms.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;

/**
 * Reglas de acceso:
 * <ul>
 *   <li>POST /api/auditoria/eventos: API key de servicio (cabecera X-API-Key) o cualquier JWT válido.</li>
 *   <li>Resto de /api/**: JWT de Django con rol ADMIN (reportes gerenciales y lectura de la bitácora).</li>
 * </ul>
 */
@Component
public class AutenticacionInterceptor implements HandlerInterceptor {

    private final JwtDjangoService jwt;
    private final FerreteriaProperties props;
    private final ObjectMapper json;

    public AutenticacionInterceptor(JwtDjangoService jwt, FerreteriaProperties props, ObjectMapper json) {
        this.jwt = jwt;
        this.props = props;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true; // preflight de CORS
        }
        String ruta = req.getRequestURI();
        boolean registrarEvento = "POST".equalsIgnoreCase(req.getMethod()) && ruta.endsWith("/api/auditoria/eventos");

        if (registrarEvento && apiKeyValida(req.getHeader("X-API-Key"))) {
            req.setAttribute(UsuarioToken.ATRIBUTO, UsuarioToken.servicioDjango());
            return true;
        }

        String cabecera = req.getHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            return rechazar(res, HttpStatus.UNAUTHORIZED, "Las credenciales de autenticación no se proveyeron.");
        }
        UsuarioToken usuario;
        try {
            usuario = jwt.validar(cabecera.substring(7).trim());
        } catch (JwtDjangoService.TokenInvalidoException e) {
            return rechazar(res, HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        if (!registrarEvento && !usuario.esAdmin()) {
            return rechazar(res, HttpStatus.FORBIDDEN, "Esta acción es exclusiva del Administrador.");
        }
        req.setAttribute(UsuarioToken.ATRIBUTO, usuario);
        return true;
    }

    /** Comparación en tiempo constante para no filtrar la clave por tiempos de respuesta. */
    private boolean apiKeyValida(String recibida) {
        String esperada = props.auditoria().apiKey();
        if (recibida == null || esperada == null || esperada.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), esperada.getBytes(StandardCharsets.UTF_8));
    }

    /** Mismo formato de error que la API de Django: {codigo, mensaje, detalle}. */
    private boolean rechazar(HttpServletResponse res, HttpStatus estado, String mensaje) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", estado.value());
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("detalle", Map.of("detail", mensaje));
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(res.getOutputStream(), cuerpo);
        return false;
    }
}
