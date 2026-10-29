package pe.tecsup.ferreteria.ms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservicio complementario del SISTEMA-FERRETERIA.
 * <ul>
 *   <li>Reportes gerenciales en JSON y PDF (solo lectura sobre la base de Django).</li>
 *   <li>Auditoría independiente: guarda eventos en su propia tabla {@code ms_evento_auditoria}.</li>
 * </ul>
 * Django sigue siendo la única fuente de verdad del negocio (ventas, stock, usuarios).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FerreteriaMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FerreteriaMsApplication.class, args);
    }
}
