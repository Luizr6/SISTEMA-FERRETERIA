package pe.tecsup.ferreteria.ms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de facturación, reportes y auditoría del SISTEMA-FERRETERIA.
 * <ul>
 *   <li>Facturación: emite el comprobante electrónico (XML UBL 2.1, hash, QR, PDF A5 y ticket de 80 mm)
 *       de cada venta registrada en Django. Tabla propia {@code ms_comprobante}.</li>
 *   <li>Reportes gerenciales en JSON, PDF y Excel: ventas, cajas, kardex y stock crítico.</li>
 *   <li>Procesos programados: facturación automática cada minuto y cierre del día a las 23:00
 *       (tabla propia {@code ms_cierre_diario}).</li>
 *   <li>Auditoría independiente: tabla propia {@code ms_evento_auditoria}.</li>
 * </ul>
 * Django es el núcleo transaccional (ventas, stock, usuarios): este servicio lee sus tablas sin modificarlas
 * y escribe solo en las suyas (prefijo {@code ms_}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FerreteriaMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FerreteriaMsApplication.class, args);
    }
}
