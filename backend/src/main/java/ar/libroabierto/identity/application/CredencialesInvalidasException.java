package ar.libroabierto.identity.application;

/** El mismo error para email desconocido y clave incorrecta, así la respuesta no revela qué emails existen. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Email o contraseña incorrectos");
    }
}
