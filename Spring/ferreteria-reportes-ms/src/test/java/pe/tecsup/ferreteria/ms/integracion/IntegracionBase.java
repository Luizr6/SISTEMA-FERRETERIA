package pe.tecsup.ferreteria.ms.integracion;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Contexto completo, sin mocks. Cada prueba revierte sus datos al terminar. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/datos-integracion.sql")
abstract class IntegracionBase {
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    protected static final String API_KEY = "laboratorio-api-key";
    protected static final String SECRET = "laboratorio-clave-ficticia-de-pruebas-2026";

    protected String admin() { return token("ADMIN", "access", SECRET, 3600); }
    protected String vendedor() { return token("VENDEDOR", "access", SECRET, 3600); }
    protected String token(String rol, String tipo, String secret, long vida) {
        return "Bearer " + Jwts.builder().claim("token_type", tipo)
                .claim("user_id", "1").claim("username", "admin_lab").claim("rol", rol)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(vida)))
                .signWith(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), Jwts.SIG.HS256)
                .compact();
    }
    protected String evento(String tipo, String usuario) {
        return "{\"tipo\":\"" + tipo + "\",\"descripcion\":\"Evento del laboratorio\","
                + "\"usuario\":\"" + usuario + "\",\"modulo\":\"cuentas\",\"datos\":{\"intento\":1}}";
    }
    protected long eventos() {
        return jdbc.queryForObject("select count(*) from ms_evento_auditoria", Long.class);
    }
}
