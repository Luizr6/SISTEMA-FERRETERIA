package pe.tecsup.ferreteria.ms.dominio.django;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Tabla de Django {@code ventas_venta}. Solo lectura.
 * Montos: subtotal sin IGV, igv y total con IGV, tal como los calcula Django al registrar la venta.
 */
@Entity
@Immutable
@Table(name = "ventas_venta")
public class Venta {

    @Id
    private Long id;

    private String numero;

    /** timestamp with time zone (UTC en la base) */
    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal igv;

    @Column(nullable = false)
    private BigDecimal total;

    /** COMPLETADA | ANULADA */
    @Column(nullable = false)
    private String estado;

    /** EFECTIVO | TARJETA | YAPE | PLIN | TRANSFERENCIA */
    @Column(name = "metodo_pago", nullable = false)
    private String metodoPago;

    /** MOSTRADOR | MOVIL | WEB | WHATSAPP */
    @Column(nullable = false)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "venta", fetch = FetchType.LAZY)
    private List<DetalleVenta> detalles = new ArrayList<>();

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public Instant getFecha() { return fecha; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getIgv() { return igv; }
    public BigDecimal getTotal() { return total; }
    public String getEstado() { return estado; }
    public String getMetodoPago() { return metodoPago; }
    public String getOrigen() { return origen; }
    public Usuario getVendedor() { return vendedor; }
    public Cliente getCliente() { return cliente; }
    public List<DetalleVenta> getDetalles() { return detalles; }

    public boolean esCompletada() {
        return "COMPLETADA".equals(estado);
    }
}
