package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class SeguridadControllerTest extends IntegracionBase {
    @ParameterizedTest @ValueSource(strings={"/api/reportes/stock-critico","/api/reportes/ventas-dia","/api/auditoria/eventos","/api/auditoria/resumen","/api/auditoria/tipos"})
    void exigeJwtParaRutasProtegidas(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value(401));
    }
    @ParameterizedTest @ValueSource(strings={"/api/reportes/stock-critico","/api/reportes/ventas-dia","/api/auditoria/eventos","/api/auditoria/resumen"})
    void vendedorNoPuedeLeerInformacionGerencial(String ruta) throws Exception {
        mvc.perform(get(ruta).header("Authorization",vendedor())).andExpect(status().isForbidden());
    }
    @Test void rechazaTokensVencidosRefreshYFirmaAjena() throws Exception {
        for(String t:new String[]{token("ADMIN","access",SECRET,-120),token("ADMIN","refresh",SECRET,3600),
            token("ADMIN","access","otra-clave-ficticia-de-mas-de-32-caracteres",3600),"Bearer basura"}) {
            mvc.perform(get("/api/reportes/stock-critico").header("Authorization",t)).andExpect(status().isUnauthorized());
        }
    }
    @Test void apiKeyNoDaAccesoALecturaYClaveIncorrectaNoInserta() throws Exception {
        mvc.perform(get("/api/auditoria/eventos").header("X-API-Key",API_KEY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auditoria/eventos").header("X-API-Key","incorrecta")
            .contentType(MediaType.APPLICATION_JSON).content(evento("LOGIN_EXITOSO","ana")))
            .andExpect(status().isUnauthorized());
        assertEquals(0,eventos());
    }
    @Test void saludEsPublicaYConsultaLaBase() throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ok")).andExpect(jsonPath("$.productos_activos").value(4));
    }
}
