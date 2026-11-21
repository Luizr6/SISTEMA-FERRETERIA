package pe.tecsup.ferreteria.ms.servicio.facturacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Leyenda "SON: ..." del comprobante y utilidades del ticket (sin base de datos). */
class NumeroALetrasTest {

    @Test
    void montosComunes() {
        assertEquals("OCHENTA Y DOS CON 60/100 SOLES", NumeroALetras.soles(new BigDecimal("82.60")));
        assertEquals("CIEN CON 00/100 SOLES", NumeroALetras.soles(new BigDecimal("100")));
        assertEquals("CIENTO DIECIOCHO CON 00/100 SOLES", NumeroALetras.soles(new BigDecimal("118.00")));
        assertEquals("CERO CON 50/100 SOLES", NumeroALetras.soles(new BigDecimal("0.50")));
    }

    @Test
    void milesYApocope() {
        assertEquals("MIL DOSCIENTOS CINCUENTA CON 00/100 SOLES", NumeroALetras.soles(new BigDecimal("1250")));
        assertEquals("VEINTIÚN MIL CON 00/100 SOLES", NumeroALetras.soles(new BigDecimal("21000")));
        assertEquals("TREINTA Y UN MIL QUINIENTOS CON 05/100 SOLES", NumeroALetras.soles(new BigDecimal("31500.05")));
        assertEquals("UN MILLÓN CON 00/100 SOLES", NumeroALetras.soles(new BigDecimal("1000000")));
    }

    @Test
    void codigosSunat() {
        assertEquals("1", ComprobanteService.codigoDocumento("DNI"));
        assertEquals("6", ComprobanteService.codigoDocumento("RUC"));
        assertEquals("0", ComprobanteService.codigoDocumento(null));
        assertEquals("KGM", ComprobanteService.unidadSunat("KG"));
        assertEquals("NIU", ComprobanteService.unidadSunat("UND"));
    }

    @Test
    void hashEsEstableYEscapaXml() {
        assertEquals(ComprobanteService.hash("<a/>"), ComprobanteService.hash("<a/>"));
        assertEquals(44, ComprobanteService.hash("x").length()); // SHA-256 en Base64
        assertEquals("C&amp;R &lt;1&gt;", ComprobanteService.esc("C&R <1>"));
    }

    @Test
    void partirRenglonesDelTicket() {
        List<String> r = ComprobantePdfService.partir("Cemento Portland Tipo I bolsa de 42.5 kg marca Sol", 20);
        assertTrue(r.stream().allMatch(l -> l.length() <= 20));
        assertEquals("Cemento Portland Tipo I bolsa de 42.5 kg marca Sol", String.join(" ", r));
    }
}
