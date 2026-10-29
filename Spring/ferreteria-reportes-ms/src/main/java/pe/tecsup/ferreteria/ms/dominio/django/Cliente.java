package pe.tecsup.ferreteria.ms.dominio.django;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla de Django {@code ventas_cliente}. Solo lectura. */
@Entity
@Immutable
@Table(name = "ventas_cliente")
public class Cliente {

    @Id
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getNumeroDocumento() { return numeroDocumento; }
}
