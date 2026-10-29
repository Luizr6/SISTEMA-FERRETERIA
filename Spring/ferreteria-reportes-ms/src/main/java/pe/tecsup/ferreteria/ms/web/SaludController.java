package pe.tecsup.ferreteria.ms.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.ferreteria.ms.repositorio.ProductoRepository;

/** GET /api/salud (público): comprueba que el servicio responde y que llega a la base de Django. */
@RestController
public class SaludController {

    private final ProductoRepository productos;

    public SaludController(ProductoRepository productos) {
        this.productos = productos;
    }

    @GetMapping("/api/salud")
    public Map<String, Object> salud() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servicio", "ferreteria-reportes-ms");
        try {
            r.put("base_de_datos", "ok");
            r.put("productos_activos", productos.contarActivos());
            r.put("estado", "ok");
        } catch (RuntimeException e) {
            r.put("base_de_datos", "sin conexión");
            r.put("estado", "degradado");
        }
        return r;
    }
}
