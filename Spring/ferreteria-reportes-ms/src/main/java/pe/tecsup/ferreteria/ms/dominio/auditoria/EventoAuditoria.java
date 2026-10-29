package pe.tecsup.ferreteria.ms.dominio.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evento de auditoría guardado por el microservicio en su propia tabla.
 * Los eventos no se editan ni se borran desde la API (solo se registran y consultan).
 */
@Entity
@Table(name = "ms_evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha;

    /** LOGIN_EXITOSO, LOGIN_FALLIDO, PEDIDO_APROBADO, PRECIO_MODIFICADO, VENTA_ANULADA… */
    @Column(nullable = false, length = 40)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NivelEvento nivel;

    /** DJANGO, REACT, ANDROID, WHATSAPP o MS (generado aquí) */
    @Column(nullable = false, length = 20)
    private String origen;

    @Column(length = 40)
    private String modulo;

    @Column(length = 150)
    private String usuario;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 60)
    private String entidad;

    @Column(name = "entidad_id", length = 40)
    private String entidadId;

    @Column(nullable = false, length = 500)
    private String descripcion;

    /** JSON libre con datos adicionales (precio anterior y nuevo, n° de pedido, etc.) */
    @Column(columnDefinition = "TEXT")
    private String datos;

    @Column(length = 45)
    private String ip;

    protected EventoAuditoria() { }

    public EventoAuditoria(String tipo, NivelEvento nivel, String origen, String descripcion) {
        this.fecha = Instant.now();
        this.tipo = tipo;
        this.nivel = nivel;
        this.origen = origen;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public Instant getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public NivelEvento getNivel() { return nivel; }
    public String getOrigen() { return origen; }
    public String getModulo() { return modulo; }
    public String getUsuario() { return usuario; }
    public Long getUsuarioId() { return usuarioId; }
    public String getEntidad() { return entidad; }
    public String getEntidadId() { return entidadId; }
    public String getDescripcion() { return descripcion; }
    public String getDatos() { return datos; }
    public String getIp() { return ip; }

    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public void setNivel(NivelEvento nivel) { this.nivel = nivel; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public void setEntidad(String entidad) { this.entidad = entidad; }
    public void setEntidadId(String entidadId) { this.entidadId = entidadId; }
    public void setDatos(String datos) { this.datos = datos; }
    public void setIp(String ip) { this.ip = ip; }
}
