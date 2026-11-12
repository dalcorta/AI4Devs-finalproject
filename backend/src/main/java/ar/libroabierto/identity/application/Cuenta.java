package ar.libroabierto.identity.application;

import ar.libroabierto.identity.domain.Rol;
import java.util.UUID;

/** Lo mínimo para autenticar: no incluye nombre, DNI ni teléfono. */
public record Cuenta(UUID id, String passwordHash, Rol rol) {
}
