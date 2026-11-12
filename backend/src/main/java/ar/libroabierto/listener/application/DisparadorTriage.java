package ar.libroabierto.listener.application;

import java.util.UUID;

/**
 * Avisa que una queja quedó confirmada on-chain y su contenido ya es público, así que puede ir al LLM de triage.
 * El listener lo llama una sola vez por queja: un evento repetido no lo vuelve a disparar.
 */
public interface DisparadorTriage {

    void quejaConfirmada(UUID quejaId);
}
