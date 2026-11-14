package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class VentasControllerTest extends IntegracionBase {
    @Test void calculaTotalesSinIncluirAnuladasNiVentasDeOtroDia() throws Exception {
        mvc.perform(get("/api/reportes/ventas-dia").param("fecha","2026-10-27").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ventas_completadas").value(2))
            .andExpect(jsonPath("$.ventas_anuladas").value(1))
            .andExpect(jsonPath("$.subtotal").value(150)).andExpect(jsonPath("$.igv").value(27))
            .andExpect(jsonPath("$.total").value(177)).andExpect(jsonPath("$.ticket_promedio").value(88.50))
            .andExpect(jsonPath("$.monto_anulado").value(236));
    }
    @Test void integraRelacionesYRespetaLosLimitesDelDiaDeLima() throws Exception {
        mvc.perform(get("/api/reportes/ventas-dia").param("fecha","2026-10-27").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.zona_horaria").value("America/Lima"))
            .andExpect(jsonPath("$.por_vendedor[0].clave").value("Ana Prueba"))
            .andExpect(jsonPath("$.por_vendedor[0].total").value(177))
            .andExpect(jsonPath("$.por_metodo_pago.length()").value(2))
            .andExpect(jsonPath("$.por_origen.length()").value(2))
            .andExpect(jsonPath("$.por_hora[0].total").value(118))
            .andExpect(jsonPath("$.por_hora[23].total").value(59))
            .andExpect(jsonPath("$.top_productos[0].codigo").value("CLA-001"))
            .andExpect(jsonPath("$.top_productos[0].unidades").value(10));
    }
    @Test void diaSinVentasDevuelveCeros() throws Exception {
        mvc.perform(get("/api/reportes/ventas-dia").param("fecha","2020-01-01").header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.ventas_completadas").value(0))
            .andExpect(jsonPath("$.top_productos").isEmpty());
    }
    @Test void fechaInvalidaDevuelveErrorEstandar() throws Exception {
        mvc.perform(get("/api/reportes/ventas-dia").param("fecha","ayer").header("Authorization",admin()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value(400))
            .andExpect(jsonPath("$.detalle").exists());
    }
}
