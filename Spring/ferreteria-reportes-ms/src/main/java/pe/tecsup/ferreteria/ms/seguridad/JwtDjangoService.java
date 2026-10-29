package pe.tecsup.ferreteria.ms.seguridad;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;

/**
 * Valida los tokens de acceso que emite Django REST Framework SimpleJWT.
 * SimpleJWT firma con HS256 usando el SECRET_KEY de Django, por eso ambos servicios comparten esa clave.
 * Claims que usamos: token_type=access, user_id (texto), username y rol (agregados en cuentas/serializers.py).
 */
@Service
public class JwtDjangoService {

    private final SecretKey clave;

    @Autowired
    public JwtDjangoService(FerreteriaProperties props) {
        this(props.jwt().secret());
    }

    /** Constructor usado también en las pruebas unitarias. */
    public JwtDjangoService(String secreto) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ferreteria.jwt.secret debe ser el SECRET_KEY de Django (mínimo 32 caracteres para HS256).");
        }
        // Se fija HmacSHA256 explícitamente: Keys.hmacShaKeyFor elegiría HS384/HS512 según el largo de la clave,
        // pero SimpleJWT siempre firma con HS256.
        this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * @return el usuario del token
     * @throws TokenInvalidoException si la firma no coincide, expiró o no es un token de acceso
     */
    public UsuarioToken validar(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(clave)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(c.get("token_type", String.class))) {
                throw new TokenInvalidoException("Se esperaba un token de acceso, no de renovación.");
            }
            Object userId = c.get("user_id");
            if (userId == null) {
                throw new TokenInvalidoException("El token no contiene user_id.");
            }
            String rol = c.get("rol", String.class);
            return new UsuarioToken(
                    Long.valueOf(userId.toString()),       // SimpleJWT 5.4+ lo envía como texto: "1"
                    c.get("username", String.class),
                    rol == null ? "VENDEDOR" : rol,
                    false);
        } catch (TokenInvalidoException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Token inválido o expirado.");
        }
    }

    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException(String mensaje) {
            super(mensaje);
        }
    }
}
