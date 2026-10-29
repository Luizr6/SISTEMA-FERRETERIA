package pe.tecsup.ferreteria.ms.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import pe.tecsup.ferreteria.ms.dominio.django.Producto;

/** Solo lectura. */
public interface ProductoRepository extends Repository<Producto, Long> {

    /** Productos activos con stock igual o menor al mínimo (mismo criterio que el dashboard de Django). */
    @Query("""
            select p from Producto p
              join fetch p.categoria
              left join fetch p.proveedor
            where p.activo = true and p.stockActual <= p.stockMinimo
            order by p.stockActual asc, p.nombre asc
            """)
    List<Producto> buscarStockCritico();

    @Query("select count(p) from Producto p where p.activo = true")
    long contarActivos();
}
