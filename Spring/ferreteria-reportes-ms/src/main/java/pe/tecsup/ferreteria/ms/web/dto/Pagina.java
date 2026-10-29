package pe.tecsup.ferreteria.ms.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Respuesta paginada estable (no se expone PageImpl de Spring directamente). */
public record Pagina<T>(List<T> resultados, int pagina, int tamano, long total, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> p) {
        return new Pagina<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
