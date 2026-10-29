package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Reporte gerencial de un día (montos en soles; subtotal sin IGV, total con IGV). */
public record ReporteVentasDia(
        LocalDate fecha,
        String zonaHoraria,
        long ventasCompletadas,
        long ventasAnuladas,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        BigDecimal ticketPromedio,
        BigDecimal montoAnulado,
        List<Fila> porMetodoPago,
        List<Fila> porVendedor,
        List<Fila> porOrigen,
        List<VentasPorHora> porHora,
        List<ProductoVendido> topProductos,
        Instant generadoEn) {

    /** Agrupación genérica: método de pago, vendedor u origen. */
    public record Fila(String clave, long cantidad, BigDecimal total) { }

    public record VentasPorHora(int hora, long cantidad, BigDecimal total) { }

    public record ProductoVendido(String codigo, String nombre, long unidades, BigDecimal importe) { }
}
