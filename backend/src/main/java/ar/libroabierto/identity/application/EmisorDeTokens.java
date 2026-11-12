package ar.libroabierto.identity.application;

import ar.libroabierto.identity.domain.Rol;
import java.util.UUID;

@FunctionalInterface
public interface EmisorDeTokens {

    String emitir(UUID usuarioId, Rol rol);
}
