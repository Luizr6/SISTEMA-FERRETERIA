package pe.tecsup.ferreteria.ms.dominio.django;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tabla de Django {@code productos_producto}. Solo lectura: el stock lo modifica únicamente Django. */
@Entity
@Immutable
@Table(name = "productos_producto")
public class Producto {

    @Id
    private Long id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "unidad_medida", nullable = false)
    private String unidadMedida;

    @Column(name = "precio_compra", nullable = false)
    private BigDecimal precioCompra;

    @Column(name = "precio_venta", nullable = false)
    private BigDecimal precioVenta;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @Column(nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getUnidadMedida() { return unidadMedida; }
    public BigDecimal getPrecioCompra() { return precioCompra; }
    public BigDecimal getPrecioVenta() { return precioVenta; }
    public Integer getStockActual() { return stockActual; }
    public Integer getStockMinimo() { return stockMinimo; }
    public Boolean getActivo() { return activo; }
    public Categoria getCategoria() { return categoria; }
    public Proveedor getProveedor() { return proveedor; }
}
