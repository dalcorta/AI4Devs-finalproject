package ar.libroabierto.identity.application;

public interface ClaveHasher {

    boolean coincide(String clave, String hash);

    /** Un hash válido que no corresponde a ninguna clave: se verifica contra él cuando el email no existe. */
    String hashDeRelleno();
}
