package pe.tecsup.ferreteria.ms.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import pe.tecsup.ferreteria.ms.dominio.facturacion.Comprobante;

/** Datos del comprobante sin el XML completo (el XML se baja aparte). */
public record ComprobanteResponse(
        Long id,
        Long ventaId,
        String tipo,
        String codigoTipo,
        String numero,
        Instant fechaEmision,
        String clienteTipoDoc,
        String clienteDocumento,
        String clienteNombre,
        BigDecimal opGravada,
        BigDecimal igv,
        BigDecimal total,
        String totalEnLetras,
        String hash,
        String textoQr,
        String estado,
        Instant fechaAnulacion,
        String motivoAnulacion) {

    public static ComprobanteResponse de(Comprobante c, String letras, String textoQr) {
        return new ComprobanteResponse(c.getId(), c.getVentaId(), c.getTipo(), c.getCodigoTipo(), c.getNumero(),
                c.getFechaEmision(), c.getClienteTipoDoc(), c.getClienteDocumento(), c.getClienteNombre(),
                c.getOpGravada(), c.getIgv(), c.getTotal(), letras, c.getHash(), textoQr, c.getEstado(),
                c.getFechaAnulacion(), c.getMotivoAnulacion());
    }
}
