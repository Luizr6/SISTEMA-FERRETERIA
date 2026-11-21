package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.Fila;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.ProductoVendido;

/**
 * Ventas de un rango de fechas (hora de Lima). Montos con IGV salvo {@code subtotal} y {@code porCategoria},
 * que van sin IGV (valor de venta de los productos).
 */
public record ReporteVentasPeriodo(
        LocalDate desde,
        LocalDate hasta,
        long ventasCompletadas,
        long ventasAnuladas,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        BigDecimal ticketPromedio,
        BigDecimal montoAnulado,
        List<Fila> porDia,
        List<Fila> porMetodoPago,
        List<Fila> porVendedor,
        List<Fila> porOrigen,
        List<Fila> porCategoria,
        List<ProductoVendido> topProductos,
        /** Solo cuando se pide con detalle (Excel). */
        List<VentaFila> ventas,
        Instant generadoEn) {

    public record VentaFila(Long id, String numero, Instant fecha, String cliente, String vendedor,
                            String metodoPago, String origen, String estado, BigDecimal subtotal,
                            BigDecimal igv, BigDecimal total) { }
}
