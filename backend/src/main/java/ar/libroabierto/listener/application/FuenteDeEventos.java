package ar.libroabierto.listener.application;

import java.util.List;

/** Lectura de eventos del contrato ComplaintRegistry. */
public interface FuenteDeEventos {

    long bloqueActual();

    /** Eventos {@code ComplaintFiled} entre {@code desde} y {@code hasta}, ambos inclusive, en orden de cadena. */
    List<EventoQuejaRegistrada> quejasRegistradas(long desde, long hasta);
}
