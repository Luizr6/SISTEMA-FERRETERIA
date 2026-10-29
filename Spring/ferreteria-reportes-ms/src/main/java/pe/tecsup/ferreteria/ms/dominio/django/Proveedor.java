package pe.tecsup.ferreteria.ms.dominio.django;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla de Django {@code productos_proveedor}. Solo lectura. */
@Entity
@Immutable
@Table(name = "productos_proveedor")
public class Proveedor {

    @Id
    private Long id;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    private String ruc;

    private String telefono;

    public Long getId() { return id; }
    public String getRazonSocial() { return razonSocial; }
    public String getRuc() { return ruc; }
    public String getTelefono() { return telefono; }
}
