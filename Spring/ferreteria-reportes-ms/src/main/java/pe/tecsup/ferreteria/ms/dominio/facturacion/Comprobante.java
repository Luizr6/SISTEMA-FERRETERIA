package pe.tecsup.ferreteria.ms.dominio.facturacion;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Comprobante electrónico emitido por este microservicio (tabla propia {@code ms_comprobante}).
 * <p>
 * Django registra la venta; Spring la "factura": arma el XML en formato UBL 2.1 (estándar SUNAT),
 * calcula su código hash, genera el QR y guarda el registro. Simulado: no se firma con certificado
 * digital ni se envía a SUNAT.
 */
@Entity
@Table(name = "ms_comprobante")
public class Comprobante {

    public static final String EMITIDO = "EMITIDO";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venta_id", nullable = false, unique = true)
    private Long ventaId;

    /** BOLETA | FACTURA */
    @Column(nullable = false, length = 10)
    private String tipo;

    /** Código SUNAT del tipo de comprobante: 03 boleta, 01 factura */
    @Column(name = "codigo_tipo", nullable = false, length = 2)
    private String codigoTipo;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private Integer correlativo;

    /** Serie-correlativo, por ejemplo B001-000123 */
    @Column(nullable = false, length = 20)
    private String numero;

    /** Fecha de la venta (la del comprobante) */
    @Column(name = "fecha_emision", nullable = false)
    private Instant fechaEmision;

    /** Catálogo 06 de SUNAT: 1 DNI, 6 RUC, 4 CE, 0 sin documento */
    @Column(name = "cliente_tipo_doc", nullable = false, length = 1)
    private String clienteTipoDoc;

    @Column(name = "cliente_documento", nullable = false, length = 20)
    private String clienteDocumento;

    @Column(name = "cliente_nombre", nullable = false, length = 150)
    private String clienteNombre;

    @Column(name = "op_gravada", nullable = false)
    private BigDecimal opGravada;

    @Column(nullable = false)
    private BigDecimal igv;

    @Column(nullable = false)
    private BigDecimal total;

    /** SHA-256 del XML en Base64 (el "código hash" que se imprime en el comprobante) */
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String xml;

    /** EMITIDO | ANULADO */
    @Column(nullable = false, length = 10)
    private String estado = EMITIDO;

    @Column(name = "fecha_anulacion")
    private Instant fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 250)
    private String motivoAnulacion;

    @Column(name = "generado_en", nullable = false)
    private Instant generadoEn = Instant.now();

    protected Comprobante() { }

    public Comprobante(Long ventaId, String tipo, String codigoTipo, String serie, Integer correlativo, Instant fechaEmision) {
        this.ventaId = ventaId;
        this.tipo = tipo;
        this.codigoTipo = codigoTipo;
        this.serie = serie;
        this.correlativo = correlativo;
        this.numero = serie + "-" + String.format("%06d", correlativo);
        this.fechaEmision = fechaEmision;
    }

    public void cliente(String tipoDoc, String documento, String nombre) {
        this.clienteTipoDoc = tipoDoc;
        this.clienteDocumento = documento;
        this.clienteNombre = nombre;
    }

    public void montos(BigDecimal opGravada, BigDecimal igv, BigDecimal total) {
        this.opGravada = opGravada;
        this.igv = igv;
        this.total = total;
    }

    public void documento(String xml, String hash) {
        this.xml = xml;
        this.hash = hash;
    }

    public void anular(Instant fecha, String motivo) {
        this.estado = ANULADO;
        this.fechaAnulacion = fecha != null ? fecha : Instant.now();
        this.motivoAnulacion = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 250));
    }

    public boolean esAnulado() {
        return ANULADO.equals(estado);
    }

    public Long getId() { return id; }
    public Long getVentaId() { return ventaId; }
    public String getTipo() { return tipo; }
    public String getCodigoTipo() { return codigoTipo; }
    public String getSerie() { return serie; }
    public Integer getCorrelativo() { return correlativo; }
    public String getNumero() { return numero; }
    public Instant getFechaEmision() { return fechaEmision; }
    public String getClienteTipoDoc() { return clienteTipoDoc; }
    public String getClienteDocumento() { return clienteDocumento; }
    public String getClienteNombre() { return clienteNombre; }
    public BigDecimal getOpGravada() { return opGravada; }
    public BigDecimal getIgv() { return igv; }
    public BigDecimal getTotal() { return total; }
    public String getHash() { return hash; }
    public String getXml() { return xml; }
    public String getEstado() { return estado; }
    public Instant getFechaAnulacion() { return fechaAnulacion; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public Instant getGeneradoEn() { return generadoEn; }
}
