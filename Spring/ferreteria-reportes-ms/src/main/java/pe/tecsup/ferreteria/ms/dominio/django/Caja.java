package pe.tecsup.ferreteria.ms.dominio.django;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tabla de Django {@code caja_caja}: turno de caja de un vendedor. Solo lectura. */
@Entity
@Immutable
@Table(name = "caja_caja")
public class Caja {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** ABIERTA | CERRADA */
    @Column(nullable = false)
    private String estado;

    @Column(name = "fecha_apertura", nullable = false)
    private Instant fechaApertura;

    @Column(name = "monto_inicial", nullable = false)
    private BigDecimal montoInicial;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @Column(name = "monto_esperado")
    private BigDecimal montoEsperado;

    @Column(name = "monto_contado")
    private BigDecimal montoContado;

    /** contado - esperado: negativo = faltante, positivo = sobrante */
    private BigDecimal diferencia;

    private String observacion;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getEstado() { return estado; }
    public Instant getFechaApertura() { return fechaApertura; }
    public BigDecimal getMontoInicial() { return montoInicial; }
    public Instant getFechaCierre() { return fechaCierre; }
    public BigDecimal getMontoEsperado() { return montoEsperado; }
    public BigDecimal getMontoContado() { return montoContado; }
    public BigDecimal getDiferencia() { return diferencia; }
    public String getObservacion() { return observacion; }

    public boolean esAbierta() {
        return "ABIERTA".equals(estado);
    }
}
