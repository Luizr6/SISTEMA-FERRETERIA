package pe.tecsup.ferreteria.ms.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores con el mismo formato que la API de Django: {codigo, mensaje, detalle} (RF27). */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, List<String>> detalle = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            detalle.computeIfAbsent(aSnake(f.getField()), k -> new java.util.ArrayList<>()).add(f.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "Datos inválidos.", detalle);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> peticionMala(Exception e) {
        String msg = e instanceof MethodArgumentTypeMismatchException m
                ? "El parámetro '" + m.getName() + "' tiene un formato inválido (fechas: AAAA-MM-DD)."
                : e instanceof IllegalArgumentException ? e.getMessage() : "El cuerpo de la petición no es un JSON válido.";
        return error(HttpStatus.BAD_REQUEST, msg, Map.of("detail", msg));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> baseDeDatos(DataAccessException e) {
        log.error("Error de base de datos", e);
        String msg = "No se pudo consultar la base de datos de la ferretería.";
        return error(HttpStatus.SERVICE_UNAVAILABLE, msg, Map.of("detail", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception e) {
        // 404 de ruta inexistente, 405 método no permitido, etc. (excepciones propias de Spring)
        if (e instanceof ErrorResponse er) {
            HttpStatus estado = HttpStatus.valueOf(er.getStatusCode().value());
            String msg = estado == HttpStatus.NOT_FOUND ? "Ruta no encontrada." : estado.getReasonPhrase();
            return error(estado, msg, Map.of("detail", msg));
        }
        log.error("Error no controlado", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servicio de reportes.", Map.of("detail", "Revisa el log del servidor."));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje, Object detalle) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", estado.value());
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("detalle", detalle);
        return ResponseEntity.status(estado).body(cuerpo);
    }

    private static String aSnake(String campo) {
        return campo.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
