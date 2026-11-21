package pe.tecsup.ferreteria.ms.repositorio;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import pe.tecsup.ferreteria.ms.dominio.django.Caja;
import pe.tecsup.ferreteria.ms.dominio.django.MovimientoCaja;

/** Solo lectura sobre las cajas de Django. */
public interface CajaRepository extends Repository<Caja, Long> {

    /** Cajas abiertas en el rango [desde, hasta), con el vendedor cargado. */
    @Query("""
            select c from Caja c join fetch c.usuario
            where c.fechaApertura >= :desde and c.fechaApertura < :hasta
            order by c.fechaApertura
            """)
    List<Caja> delPeriodo(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Cajas que siguen abiertas (de cualquier día). */
    @Query("select c from Caja c join fetch c.usuario where c.estado = 'ABIERTA' order by c.fechaApertura")
    List<Caja> abiertas();

    @Query("select m from MovimientoCaja m where m.cajaId in :ids order by m.fecha")
    List<MovimientoCaja> movimientosDe(@Param("ids") List<Long> ids);

    /** Total vendido en una caja (ventas completadas), por método de pago: [metodo, total, cantidad]. */
    @Query("""
            select v.metodoPago, sum(v.total), count(v) from Venta v
            where v.cajaId = :caja and v.estado = 'COMPLETADA'
            group by v.metodoPago
            """)
    List<Object[]> ventasPorMetodo(@Param("caja") Long cajaId);
}
