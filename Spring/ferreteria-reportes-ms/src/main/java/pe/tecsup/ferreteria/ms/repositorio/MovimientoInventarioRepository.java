package pe.tecsup.ferreteria.ms.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import pe.tecsup.ferreteria.ms.dominio.django.MovimientoInventario;
import pe.tecsup.ferreteria.ms.dominio.django.Producto;

/** Solo lectura: kardex de Django. */
public interface MovimientoInventarioRepository extends Repository<MovimientoInventario, Long> {

    @Query("""
            select m from MovimientoInventario m left join fetch m.usuario
            where m.producto.id = :producto and m.fecha >= :desde and m.fecha < :hasta
            order by m.fecha, m.id
            """)
    List<MovimientoInventario> kardex(@Param("producto") Long productoId,
                                      @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Último movimiento antes del rango: da el stock inicial del kardex. */
    Optional<MovimientoInventario> findFirstByProductoIdAndFechaBeforeOrderByFechaDescIdDesc(Long productoId, Instant fecha);

    @Query("select p from Producto p join fetch p.categoria where lower(p.codigo) = lower(:codigo)")
    Optional<Producto> productoPorCodigo(@Param("codigo") String codigo);
}
