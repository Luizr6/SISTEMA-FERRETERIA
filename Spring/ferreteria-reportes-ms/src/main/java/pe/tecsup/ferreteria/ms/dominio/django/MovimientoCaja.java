package pe.tecsup.ferreteria.ms.dominio.django;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla de Django {@code caja_movimientocaja}: ingreso o egreso de efectivo que no es venta. Solo lectura. */
@Entity
@Immutable
@Table(name = "caja_movimientocaja")
public class MovimientoCaja {

    @Id
    private Long id;

    @Column(name = "caja_id", nullable = false)
    private Long cajaId;

    /** INGRESO | EGRESO */
    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private String concepto;

    @Column(nullable = false)
    private Instant fecha;

    public Long getId() { return id; }
    public Long getCajaId() { return cajaId; }
    public String getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public String getConcepto() { return concepto; }
    public Instant getFecha() { return fecha; }
}
