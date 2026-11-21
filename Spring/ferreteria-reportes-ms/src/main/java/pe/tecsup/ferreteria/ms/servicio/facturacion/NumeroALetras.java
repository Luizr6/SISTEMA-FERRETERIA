package pe.tecsup.ferreteria.ms.servicio.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 82.60 -> "OCHENTA Y DOS CON 60/100 SOLES" (leyenda obligatoria del comprobante). */
public final class NumeroALetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private NumeroALetras() { }

    public static String soles(BigDecimal monto) {
        BigDecimal m = (monto == null ? BigDecimal.ZERO : monto).setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centimos = m.subtract(BigDecimal.valueOf(entero)).movePointRight(2).intValue();
        return enLetras(entero) + " CON " + String.format("%02d", centimos) + "/100 SOLES";
    }

    static String enLetras(long n) {
        if (n == 0) {
            return "CERO";
        }
        long millones = n / 1_000_000;
        long miles = (n % 1_000_000) / 1000;
        long resto = n % 1000;
        StringBuilder sb = new StringBuilder();
        if (millones > 0) {
            sb.append(millones == 1 ? "UN MILLÓN" : apocopar(menorMil((int) millones)) + " MILLONES").append(' ');
        }
        if (miles > 0) {
            sb.append(miles == 1 ? "MIL" : apocopar(menorMil((int) miles)) + " MIL").append(' ');
        }
        if (resto > 0) {
            sb.append(menorMil((int) resto));
        }
        return sb.toString().trim();
    }

    private static String menorMil(int n) {
        if (n == 100) {
            return "CIEN";
        }
        int c = n / 100;
        int r = n % 100;
        StringBuilder sb = new StringBuilder();
        if (c > 0) {
            sb.append(CENTENAS[c]);
        }
        if (r > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (r < 30) {
                sb.append(UNIDADES[r]);
            } else {
                sb.append(DECENAS[r / 10]);
                if (r % 10 > 0) {
                    sb.append(" Y ").append(UNIDADES[r % 10]);
                }
            }
        }
        return sb.toString();
    }

    /** "VEINTIUNO MIL" -> "VEINTIÚN MIL", "TREINTA Y UNO MIL" -> "TREINTA Y UN MIL" */
    private static String apocopar(String texto) {
        if (texto.endsWith("VEINTIUNO")) {
            return texto.substring(0, texto.length() - "VEINTIUNO".length()) + "VEINTIÚN";
        }
        if (texto.endsWith("UNO")) {
            return texto.substring(0, texto.length() - 1);
        }
        return texto;
    }
}
