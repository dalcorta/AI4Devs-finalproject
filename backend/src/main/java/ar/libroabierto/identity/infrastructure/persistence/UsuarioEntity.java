package ar.libroabierto.identity.infrastructure.persistence;

import ar.libroabierto.identity.domain.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Solo las columnas que necesitan el login y el alta de quejas. Nombre, DNI y teléfono no se mapean a propósito:
 * ninguna parte de este flujo los lee, así que no pueden filtrarse por acá.
 */
@Entity
@Table(name = "usuario")
class UsuarioEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "identidad_verificada", nullable = false)
    private boolean identidadVerificada;

    protected UsuarioEntity() {
    }

    UUID id() {
        return id;
    }

    String passwordHash() {
        return passwordHash;
    }

    Rol rol() {
        return rol;
    }

    boolean identidadVerificada() {
        return identidadVerificada;
    }
}
