package ar.libroabierto.identity.application;

import ar.libroabierto.identity.domain.Rol;
import java.util.Locale;
import java.util.Optional;

public class Autenticar {

    private final CuentasDeUsuario cuentas;
    private final ClaveHasher hasher;
    private final EmisorDeTokens tokens;

    public Autenticar(CuentasDeUsuario cuentas, ClaveHasher hasher, EmisorDeTokens tokens) {
        this.cuentas = cuentas;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    public Sesion ejecutar(String email, String clave) {
        String normalizado = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        Optional<Cuenta> cuenta = cuentas.porEmail(normalizado);
        // Siempre se verifica un hash: si el email no existe, contra uno de relleno. Si no, la respuesta más rápida
        // delataría qué emails están registrados.
        String hash = cuenta.map(Cuenta::passwordHash).orElseGet(hasher::hashDeRelleno);
        boolean coincide = hasher.coincide(clave == null ? "" : clave, hash);
        if (cuenta.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        return new Sesion(tokens.emitir(cuenta.get().id(), cuenta.get().rol()), cuenta.get().rol());
    }

    public record Sesion(String token, Rol rol) {
    }
}
