package ar.libroabierto.triage.application;

/** Puerto hacia el proveedor del LLM. El dominio no conoce el SDK ni el proveedor. */
public interface LlmClient {

    /**
     * @throws LlmNoDisponibleException si el proveedor no respondió (red, sobrecarga, límite de uso): la queja se
     *     reintenta en la próxima ejecución sin contar como salida inválida
     */
    RespuestaLlm completar(SolicitudLlm solicitud);
}
