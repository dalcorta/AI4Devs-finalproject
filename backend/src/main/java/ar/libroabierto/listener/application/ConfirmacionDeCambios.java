package ar.libroabierto.listener.application;

public interface ConfirmacionDeCambios {

    /**
     * Marca CONFIRMADA la transacción del cambio de estado, con su bloque. Si el worker no llegó a guardar el tx_hash,
     * el evento lo completa. Es idempotente: reprocesar el mismo evento no cambia nada.
     */
    void confirmar(EventoEstadoCambiado evento);
}
