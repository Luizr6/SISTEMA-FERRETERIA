package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Productos activos con stock igual o menor al mínimo, con la cantidad y costo sugeridos para reponer. */
public record ReporteStockCritico(
        long totalCriticos,
        long agotados,
        long productosActivos,
        BigDecimal costoReposicionEstimado,
        List<Item> productos,
        Instant generadoEn) {

    public record Item(
            String codigo,
            String nombre,
            String categoria,
            String proveedor,
            String unidadMedida,
            int stockActual,
            int stockMinimo,
            String estado,
            int cantidadSugerida,
            BigDecimal costoUnitario,
            BigDecimal costoReposicion) { }
}
