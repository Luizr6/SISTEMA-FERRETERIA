package pe.tecsup.ferreteria.ms.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Kardex (tarjeta de existencias) de un producto en un rango de fechas. */
public record Kardex(
        String codigo,
        String producto,
        String categoria,
        String unidadMedida,
        LocalDate desde,
        LocalDate hasta,
        int stockInicial,
        long entradas,
        long salidas,
        int stockFinal,
        int stockActual,
        List<Movimiento> movimientos,
        Instant generadoEn) {

    public record Movimiento(Instant fecha, String tipo, String motivo, String usuario,
                             int entrada, int salida, int saldo) { }
}
