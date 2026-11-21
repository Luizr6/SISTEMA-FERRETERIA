package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import pe.tecsup.ferreteria.ms.dominio.cierre.CierreDiario;

public record CierreResponse(
        Long id,
        LocalDate fecha,
        long cantidadVentas,
        long ventasAnuladas,
        BigDecimal totalVendido,
        BigDecimal ticketPromedio,
        BigDecimal totalEfectivo,
        BigDecimal totalOtrosMedios,
        long comprobantesEmitidos,
        long cajasCerradas,
        long cajasAbiertas,
        BigDecimal faltantes,
        BigDecimal sobrantes,
        long productosCriticos,
        List<String> alertas,
        String origen,
        Instant generadoEn) {

    public static CierreResponse de(CierreDiario c) {
        List<String> alertas = c.getAlertas() == null || c.getAlertas().isBlank() ? List.of()
                : Arrays.stream(c.getAlertas().split("\n")).filter(s -> !s.isBlank()).toList();
        return new CierreResponse(c.getId(), c.getFecha(), c.getCantidadVentas(), c.getVentasAnuladas(),
                c.getTotalVendido(), c.getTicketPromedio(), c.getTotalEfectivo(), c.getTotalOtrosMedios(),
                c.getComprobantesEmitidos(), c.getCajasCerradas(), c.getCajasAbiertas(), c.getFaltantes(),
                c.getSobrantes(), c.getProductosCriticos(), alertas, c.getOrigen(), c.getGeneradoEn());
    }
}
