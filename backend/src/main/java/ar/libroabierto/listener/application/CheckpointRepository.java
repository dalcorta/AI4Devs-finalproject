package ar.libroabierto.listener.application;

import java.util.OptionalLong;

/** Último bloque procesado por el listener, para retomar tras un reinicio. */
public interface CheckpointRepository {

    OptionalLong ultimoBloque();

    void guardar(long bloque);
}
