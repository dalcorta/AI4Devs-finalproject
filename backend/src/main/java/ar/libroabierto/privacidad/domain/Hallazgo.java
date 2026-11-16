package ar.libroabierto.privacidad.domain;

/**
 * Un posible dato personal en un texto. {@code inicio} y {@code fin} son posiciones (en unidades UTF-16, como en Java
 * y en JavaScript) del texto revisado, con {@code fin} exclusivo; {@code fragmento} es el texto en esas posiciones.
 */
public record Hallazgo(TipoDatoPersonal tipo, String fragmento, int inicio, int fin) {
}
