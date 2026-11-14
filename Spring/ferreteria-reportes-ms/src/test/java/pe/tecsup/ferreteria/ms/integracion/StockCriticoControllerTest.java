package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StockCriticoControllerTest extends IntegracionBase {
    @Test void incluyeAgotadosYMinimoExcluyeInactivosYStockSuficiente() throws Exception {
        mvc.perform(get("/api/reportes/stock-critico").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total_criticos").value(3))
            .andExpect(jsonPath("$.agotados").value(1)).andExpect(jsonPath("$.productos_activos").value(4))
            .andExpect(jsonPath("$.productos[0].codigo").value("CLA-001"))
            .andExpect(jsonPath("$.productos[0].estado").value("AGOTADO"))
            .andExpect(jsonPath("$.productos[2].codigo").value("LIM-001"));
    }
    @Test void calculaReposicionYCostoConProveedorOpcional() throws Exception {
        mvc.perform(get("/api/reportes/stock-critico").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.costo_reposicion_estimado").value(110))
            .andExpect(jsonPath("$.productos[0].cantidad_sugerida").value(8))
            .andExpect(jsonPath("$.productos[0].costo_reposicion").value(24))
            .andExpect(jsonPath("$.productos[0].proveedor").doesNotExist())
            .andExpect(jsonPath("$.productos[1].categoria").value("Herramientas"))
            .andExpect(jsonPath("$.productos[1].proveedor").value("Proveedor de prueba"));
    }
    @Test void consultarNoModificaElInventario() throws Exception {
        var antes=jdbc.queryForList("select id,stock_actual,precio_venta from productos_producto order by id");
        mvc.perform(get("/api/reportes/stock-critico").header("Authorization",admin())).andExpect(status().isOk());
        assertEquals(antes,jdbc.queryForList("select id,stock_actual,precio_venta from productos_producto order by id"));
        assertEquals(0,eventos());
    }
    @Test void sinProductosCriticosDevuelveListaVacia() throws Exception {
        jdbc.update("update productos_producto set stock_actual=100");
        mvc.perform(get("/api/reportes/stock-critico").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total_criticos").value(0))
            .andExpect(jsonPath("$.productos").isEmpty()).andExpect(jsonPath("$.costo_reposicion_estimado").value(0));
    }
}
