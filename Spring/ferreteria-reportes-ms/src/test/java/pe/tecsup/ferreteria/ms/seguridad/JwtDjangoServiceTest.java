package pe.tecsup.ferreteria.ms.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;

/**
 * Genera tokens con la misma forma que SimpleJWT de Django (HS256, user_id como texto)
 * y comprueba que el microservicio los acepta o rechaza correctamente.
 */
class JwtDjangoServiceTest {

    private static final String SECRET = "dev-solo-local-cambiar-en-produccion-ferreteria-2026";
    private final JwtDjangoService servicio = new JwtDjangoService(SECRET);

    private static String token(String secreto, String tipo, String rol, long segundosDeVida) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .claim("token_type", tipo)
                .claim("user_id", "1")
                .claim("username", "demo_admin")
                .claim("rol", rol)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + segundosDeVida * 1000))
                .signWith(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void aceptaTokenDeAccesoDeDjango() {
        UsuarioToken u = servicio.validar(token(SECRET, "access", "ADMIN", 3600));
        assertEquals(1L, u.id());
        assertEquals("demo_admin", u.username());
        assertTrue(u.esAdmin());
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        String falso = token("otra-clave-distinta-de-mas-de-32-caracteres!!", "access", "ADMIN", 3600);
        assertThrows(JwtDjangoService.TokenInvalidoException.class, () -> servicio.validar(falso));
    }

    @Test
    void rechazaTokenDeRenovacion() {
        assertThrows(JwtDjangoService.TokenInvalidoException.class,
                () -> servicio.validar(token(SECRET, "refresh", "ADMIN", 3600)));
    }

    @Test
    void rechazaTokenVencido() {
        assertThrows(JwtDjangoService.TokenInvalidoException.class,
                () -> servicio.validar(token(SECRET, "access", "ADMIN", -120)));
    }

    @Test
    void exigeClaveDeAlMenos32Caracteres() {
        assertThrows(IllegalStateException.class, () -> new JwtDjangoService("corta"));
    }
}
