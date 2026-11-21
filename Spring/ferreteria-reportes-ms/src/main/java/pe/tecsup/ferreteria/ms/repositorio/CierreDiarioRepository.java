package pe.tecsup.ferreteria.ms.repositorio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.tecsup.ferreteria.ms.dominio.cierre.CierreDiario;

/** Tabla propia ms_cierre_diario. */
public interface CierreDiarioRepository extends JpaRepository<CierreDiario, Long> {

    Optional<CierreDiario> findByFecha(LocalDate fecha);

    List<CierreDiario> findTop60ByOrderByFechaDesc();
}
