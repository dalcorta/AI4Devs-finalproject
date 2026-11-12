package ar.libroabierto.identity.application;

import java.util.Optional;

@FunctionalInterface
public interface CuentasDeUsuario {

    /** @param email ya normalizado (sin espacios, en minúsculas) */
    Optional<Cuenta> porEmail(String email);
}
