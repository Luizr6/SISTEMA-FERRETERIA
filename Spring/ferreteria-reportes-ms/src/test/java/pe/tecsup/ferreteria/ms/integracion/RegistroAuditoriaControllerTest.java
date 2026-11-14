package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RegistroAuditoriaControllerTest extends IntegracionBase {
    @Test void apiKeyRegistraEventoYPersisteJson() throws Exception {
        mvc.perform(post("/api/auditoria/eventos").header("X-API-Key",API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(evento("LOGIN_EXITOSO","ana")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.origen").value("DJANGO")).andExpect(jsonPath("$.nivel").value("INFO"))
            .andExpect(jsonPath("$.datos.intento").value(1));
        assertEquals(1,eventos());
        assertEquals("ana",jdbc.queryForObject("select usuario from ms_evento_auditoria",String.class));
    }
    @Test void quintoFalloElevaNivelConHistorialPersistido() throws Exception {
        for(int i=1;i<=5;i++) {
            mvc.perform(post("/api/auditoria/eventos").header("X-API-Key",API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(evento("LOGIN_FALLIDO", i%2==0 ? "ANA" : "ana")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nivel").value(i<5?"ADVERTENCIA":"CRITICO"));
        }
        assertEquals(5,eventos());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ms_evento_auditoria where nivel='CRITICO'",Long.class));
    }
    @Test void fallosDeOtroUsuarioOAntiguosNoActivanAlerta() throws Exception {
        jdbc.update("insert into ms_evento_auditoria(fecha,tipo,nivel,origen,usuario,descripcion) values (DATEADD('MINUTE',-11,CURRENT_TIMESTAMP),'LOGIN_FALLIDO','ADVERTENCIA','DJANGO','ana','Antiguo')");
        for(int i=0;i<4;i++) jdbc.update("insert into ms_evento_auditoria(fecha,tipo,nivel,origen,usuario,descripcion) values (CURRENT_TIMESTAMP,'LOGIN_FALLIDO','ADVERTENCIA','DJANGO','otro','Otro usuario')");
        mvc.perform(post("/api/auditoria/eventos").header("X-API-Key",API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content(evento("LOGIN_FALLIDO","ana")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.nivel").value("ADVERTENCIA"));
    }
    @Test void datosInvalidosNoSeGuardan() throws Exception {
        mvc.perform(post("/api/auditoria/eventos").header("X-API-Key",API_KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\":\"minusculas\",\"descripcion\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.tipo").exists());
        assertEquals(0,eventos());
    }
    @Test void jwtDeVendedorPermiteRegistrarSuEvento() throws Exception {
        mvc.perform(post("/api/auditoria/eventos").header("Authorization",vendedor()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\":\"LOGIN_EXITOSO\",\"descripcion\":\"Ingreso del usuario\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario").value("admin_lab"))
            .andExpect(jsonPath("$.usuario_id").value(1)).andExpect(jsonPath("$.origen").value("API"));
    }
}
