package pe.tecsup.ferreteria.ms.dominio.auditoria;

/** Gravedad del evento. CRITICO se usa, por ejemplo, ante varios logins fallidos seguidos. */
public enum NivelEvento {
    INFO,
    ADVERTENCIA,
    CRITICO
}
