package pe.tecsup.ferreteria.ms.dominio.django;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla de Django {@code productos_categoria}. Solo lectura. */
@Entity
@Immutable
@Table(name = "productos_categoria")
public class Categoria {

    @Id
    private Long id;

    @Column(nullable = false)
    private String nombre;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
