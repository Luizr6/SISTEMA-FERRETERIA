package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Arqueos de caja de un rango de fechas: cuánto debía haber, cuánto se contó y la diferencia. */
public record ReporteCajas(
        LocalDate desde,
        LocalDate hasta,
        long cajas,
        long abiertas,
        long conFaltante,
        BigDecimal totalFaltante,
        BigDecimal totalSobrante,
        BigDecimal totalVendido,
        List<PorVendedor> porVendedor,
        List<CajaFila> detalle,
        Instant generadoEn) {

    public record CajaFila(Long id, String vendedor, String estado, Instant fechaApertura, Instant fechaCierre,
                           BigDecimal montoInicial, BigDecimal ventasEfectivo, BigDecimal ventasOtrosMedios,
                           BigDecimal ingresos, BigDecimal egresos, BigDecimal esperado, BigDecimal contado,
                           BigDecimal diferencia, String observacion) { }

    public record PorVendedor(String vendedor, long cajas, long conFaltante, BigDecimal faltante,
                              BigDecimal sobrante, BigDecimal vendido) { }
}
