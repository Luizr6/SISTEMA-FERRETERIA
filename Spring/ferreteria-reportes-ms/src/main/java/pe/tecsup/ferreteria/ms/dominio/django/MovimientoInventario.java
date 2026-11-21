package pe.tecsup.ferreteria.ms.dominio.django;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tabla de Django {@code productos_movimientoinventario} (kardex). Solo lectura. */
@Entity
@Immutable
@Table(name = "productos_movimientoinventario")
public class MovimientoInventario {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** INGRESO | SALIDA | AJUSTE */
    @Column(nullable = false)
    private String tipo;

    /** Con signo: positivo ingresa, negativo sale. */
    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private Integer stockResultante;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private Instant fecha;

    public Long getId() { return id; }
    public Producto getProducto() { return producto; }
    public String getTipo() { return tipo; }
    public Integer getCantidad() { return cantidad; }
    public Integer getStockResultante() { return stockResultante; }
    public String getMotivo() { return motivo; }
    public Usuario getUsuario() { return usuario; }
    public Instant getFecha() { return fecha; }
}
