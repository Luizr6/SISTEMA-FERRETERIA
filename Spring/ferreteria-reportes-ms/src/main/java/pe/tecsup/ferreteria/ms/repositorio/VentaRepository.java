package pe.tecsup.ferreteria.ms.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import pe.tecsup.ferreteria.ms.dominio.django.Venta;

/**
 * Solo lectura: extiende {@link Repository} (no JpaRepository) para que no exista ningún método save/delete.
 */
public interface VentaRepository extends Repository<Venta, Long> {

    /** Ventas del rango [desde, hasta) con vendedor, cliente y detalle cargados en una sola consulta. */
    @Query("""
            select distinct v from Venta v
              join fetch v.vendedor
              left join fetch v.cliente
              left join fetch v.detalles d
              left join fetch d.producto
            where v.fecha >= :desde and v.fecha < :hasta
            order by v.fecha
            """)
    List<Venta> buscarDelPeriodo(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Una venta con todo lo que necesita su comprobante. */
    @Query("""
            select distinct v from Venta v
              join fetch v.vendedor
              left join fetch v.cliente
              left join fetch v.detalles d
              left join fetch d.producto
            where v.id = :id
            """)
    Optional<Venta> buscarConDetalle(@Param("id") Long id);

    /** Ventas recientes que todavía no tienen comprobante (las procesa la tarea programada). */
    @Query(value = """
            select v.id from ventas_venta v
            where v.fecha >= :desde and v.numero is not null
              and not exists (select 1 from ms_comprobante c where c.venta_id = v.id)
            order by v.id
            limit 200
            """, nativeQuery = true)
    List<Long> idsSinComprobante(@Param("desde") Instant desde);
}
