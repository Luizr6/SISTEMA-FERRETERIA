package pe.tecsup.ferreteria.ms.dominio.cierre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cierre del día que genera la tarea programada de las 23:00 (tabla propia {@code ms_cierre_diario}).
 * Se puede volver a ejecutar a mano: reemplaza los valores del mismo día.
 */
@Entity
@Table(name = "ms_cierre_diario")
public class CierreDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(name = "cantidad_ventas", nullable = false)
    private long cantidadVentas;

    @Column(name = "ventas_anuladas", nullable = false)
    private long ventasAnuladas;

    @Column(name = "total_vendido", nullable = false)
    private BigDecimal totalVendido;

    @Column(name = "ticket_promedio", nullable = false)
    private BigDecimal ticketPromedio;

    @Column(name = "total_efectivo", nullable = false)
    private BigDecimal totalEfectivo;

    @Column(name = "total_otros_medios", nullable = false)
    private BigDecimal totalOtrosMedios;

    @Column(name = "comprobantes_emitidos", nullable = false)
    private long comprobantesEmitidos;

    @Column(name = "cajas_cerradas", nullable = false)
    private long cajasCerradas;

    @Column(name = "cajas_abiertas", nullable = false)
    private long cajasAbiertas;

    /** Suma de los faltantes (en positivo) de las cajas cerradas ese día */
    @Column(nullable = false)
    private BigDecimal faltantes;

    @Column(nullable = false)
    private BigDecimal sobrantes;

    @Column(name = "productos_criticos", nullable = false)
    private long productosCriticos;

    /** Alertas del día, una por línea */
    @Column(columnDefinition = "TEXT")
    private String alertas;

    /** PROGRAMADO (tarea de las 23:00) | MANUAL (botón del Administrador) */
    @Column(nullable = false, length = 12)
    private String origen;

    @Column(name = "generado_en", nullable = false)
    private Instant generadoEn;

    protected CierreDiario() { }

    public CierreDiario(LocalDate fecha) {
        this.fecha = fecha;
    }

    public void ventas(long cantidad, long anuladas, BigDecimal total, BigDecimal ticket, BigDecimal efectivo, BigDecimal otros) {
        this.cantidadVentas = cantidad;
        this.ventasAnuladas = anuladas;
        this.totalVendido = total;
        this.ticketPromedio = ticket;
        this.totalEfectivo = efectivo;
        this.totalOtrosMedios = otros;
    }

    public void cajas(long cerradas, long abiertas, BigDecimal faltantes, BigDecimal sobrantes) {
        this.cajasCerradas = cerradas;
        this.cajasAbiertas = abiertas;
        this.faltantes = faltantes;
        this.sobrantes = sobrantes;
    }

    public void resto(long comprobantes, long criticos, String alertas, String origen) {
        this.comprobantesEmitidos = comprobantes;
        this.productosCriticos = criticos;
        this.alertas = alertas;
        this.origen = origen;
        this.generadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getFecha() { return fecha; }
    public long getCantidadVentas() { return cantidadVentas; }
    public long getVentasAnuladas() { return ventasAnuladas; }
    public BigDecimal getTotalVendido() { return totalVendido; }
    public BigDecimal getTicketPromedio() { return ticketPromedio; }
    public BigDecimal getTotalEfectivo() { return totalEfectivo; }
    public BigDecimal getTotalOtrosMedios() { return totalOtrosMedios; }
    public long getComprobantesEmitidos() { return comprobantesEmitidos; }
    public long getCajasCerradas() { return cajasCerradas; }
    public long getCajasAbiertas() { return cajasAbiertas; }
    public BigDecimal getFaltantes() { return faltantes; }
    public BigDecimal getSobrantes() { return sobrantes; }
    public long getProductosCriticos() { return productosCriticos; }
    public String getAlertas() { return alertas; }
    public String getOrigen() { return origen; }
    public Instant getGeneradoEn() { return generadoEn; }
}
