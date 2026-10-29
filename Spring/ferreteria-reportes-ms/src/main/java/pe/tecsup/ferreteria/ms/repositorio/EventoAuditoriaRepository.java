package pe.tecsup.ferreteria.ms.repositorio;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.tecsup.ferreteria.ms.dominio.auditoria.EventoAuditoria;
import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;

/** Única tabla en la que este servicio escribe. Los filtros dinámicos se arman con Specifications. */
public interface EventoAuditoriaRepository
        extends JpaRepository<EventoAuditoria, Long>, JpaSpecificationExecutor<EventoAuditoria> {

    /** Para detectar varios intentos fallidos seguidos del mismo usuario. */
    long countByTipoAndUsuarioIgnoreCaseAndFechaAfter(String tipo, String usuario, Instant desde);

    @Query("select e.tipo, count(e) from EventoAuditoria e where e.fecha >= :desde group by e.tipo order by count(e) desc")
    List<Object[]> contarPorTipo(@Param("desde") Instant desde);

    @Query("select e.nivel, count(e) from EventoAuditoria e where e.fecha >= :desde group by e.nivel")
    List<Object[]> contarPorNivel(@Param("desde") Instant desde);

    List<EventoAuditoria> findTop10ByNivelOrderByFechaDesc(NivelEvento nivel);
}
