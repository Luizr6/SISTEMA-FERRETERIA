package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ConsultaAuditoriaControllerTest extends IntegracionBase {
    private long registrar(String tipo,String usuario) throws Exception {
        String body=mvc.perform(post("/api/auditoria/eventos").header("X-API-Key",API_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(evento(tipo,usuario)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
    @Test void consultaPorIdYDevuelve404SiNoExiste() throws Exception {
        long id=registrar("LOGIN_EXITOSO","ana");
        mvc.perform(get("/api/auditoria/eventos/"+id).header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.usuario").value("ana"));
        mvc.perform(get("/api/auditoria/eventos/999999").header("Authorization",admin()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value(404));
    }
    @Test void aplicaFiltrosCombinadosYPaginacion() throws Exception {
        registrar("LOGIN_FALLIDO","Ana"); registrar("LOGIN_EXITOSO","otro"); registrar("LOGIN_FALLIDO","ANA");
        mvc.perform(get("/api/auditoria/eventos").header("Authorization",admin())
            .param("tipo","login_fallido").param("usuario","ana").param("nivel","ADVERTENCIA")
            .param("modulo","cuentas").param("page","0").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.resultados.length()").value(1)).andExpect(jsonPath("$.total_paginas").value(2));
    }
    @Test void resumenAgrupaEventosYTiposDisponibles() throws Exception {
        registrar("LOGIN_EXITOSO","ana"); registrar("LOGIN_FALLIDO","ana");
        mvc.perform(get("/api/auditoria/resumen").param("dias","7").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total_eventos").value(2))
            .andExpect(jsonPath("$.por_tipo.LOGIN_EXITOSO").value(1))
            .andExpect(jsonPath("$.por_nivel.ADVERTENCIA").value(1));
        mvc.perform(get("/api/auditoria/tipos").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasItem("PRECIO_MODIFICADO")));
    }
    @Test void filtroFechaIncluyeSoloElDiaDeLima() throws Exception {
        long id=registrar("LOGIN_EXITOSO","ana");
        jdbc.update("update ms_evento_auditoria set fecha='2026-10-28 04:59:59+00' where id=?",id);
        mvc.perform(get("/api/auditoria/eventos").header("Authorization",admin())
            .param("desde","2026-10-27").param("hasta","2026-10-27"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/auditoria/eventos").header("Authorization",admin())
            .param("desde","2026-10-28").param("hasta","2026-10-28"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
}
