package pe.tecsup.ferreteria.ms.dominio.django;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla de Django {@code cuentas_usuario}. Solo se mapean los datos que necesitan los reportes:
 * la contraseña (hash) a propósito NO se mapea.
 */
@Entity
@Immutable
@Table(name = "cuentas_usuario")
public class Usuario {

    @Id
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "first_name", nullable = false)
    private String nombre;

    @Column(name = "last_name", nullable = false)
    private String apellido;

    @Column(nullable = false)
    private String rol;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRol() { return rol; }

    public String getNombreVisible() {
        String completo = ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
        return completo.isEmpty() ? username : completo;
    }
}
