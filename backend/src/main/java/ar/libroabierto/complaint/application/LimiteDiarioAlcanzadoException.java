package ar.libroabierto.complaint.application;

public class LimiteDiarioAlcanzadoException extends RuntimeException {

    public LimiteDiarioAlcanzadoException(int limite) {
        super("Se alcanzó el límite de " + limite + " quejas por día");
    }
}
