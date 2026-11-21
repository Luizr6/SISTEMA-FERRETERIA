package pe.tecsup.ferreteria.ms.repositorio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.tecsup.ferreteria.ms.dominio.facturacion.Comprobante;

/** Tabla propia ms_comprobante: aquí sí se escribe. */
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByVentaId(Long ventaId);

    /** estado y tipo vacíos ('') = sin filtrar. */
    @Query("""
            select c from Comprobante c
            where (:estado = '' or c.estado = :estado)
              and (:tipo = '' or c.tipo = :tipo)
              and c.fechaEmision >= :desde and c.fechaEmision < :hasta
            order by c.fechaEmision desc
            """)
    Page<Comprobante> buscar(@Param("estado") String estado, @Param("tipo") String tipo,
                             @Param("desde") Instant desde, @Param("hasta") Instant hasta, Pageable pagina);

    long countByFechaEmisionGreaterThanEqualAndFechaEmisionLessThan(Instant desde, Instant hasta);

    /** Comprobantes emitidos cuya venta ya fue anulada en Django (hay que darlos de baja). */
    @Query(value = """
            select c.id from ms_comprobante c
              join ventas_venta v on v.id = c.venta_id
            where c.estado = 'EMITIDO' and v.estado = 'ANULADA'
            """, nativeQuery = true)
    List<Long> idsPorAnular();
}
