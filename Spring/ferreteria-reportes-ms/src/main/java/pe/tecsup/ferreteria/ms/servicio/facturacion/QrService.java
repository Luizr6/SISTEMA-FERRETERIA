package pe.tecsup.ferreteria.ms.servicio.facturacion;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Genera el código QR (PNG) que va impreso en el comprobante. */
@Service
public class QrService {

    public byte[] png(String contenido, int pixeles) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, pixeles, pixeles,
                    Map.of(EncodeHintType.MARGIN, 1,
                            EncodeHintType.CHARACTER_SET, "UTF-8",
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el código QR: " + e.getMessage(), e);
        }
    }
}
