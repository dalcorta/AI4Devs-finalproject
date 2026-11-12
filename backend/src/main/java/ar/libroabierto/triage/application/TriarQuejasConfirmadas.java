package ar.libroabierto.triage.application;

import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.triage.domain.EstadoTriage;
import ar.libroabierto.triage.domain.Triage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker de triage (patrón outbox, como la publicación): toma las quejas confirmadas sin triage y las clasifica con el
 * LLM. Una salida inválida se reintenta hasta 2 veces; si sigue fallando, la queja queda en "Triage manual".
 *
 * <p>Es idempotente porque solo lee quejas sin triage y el repositorio guarda uno por queja. Si el proveedor no está
 * disponible, no se guarda nada y la queja vuelve en la próxima ejecución.
 */
public class TriarQuejasConfirmadas {

    static final int MAX_INTENTOS = 3;

    private static final Logger log = LoggerFactory.getLogger(TriarQuejasConfirmadas.class);

    private final QuejasParaTriage pendientes;
    private final TriageRepository triages;
    private final LlmClient llm;
    private final ValidadorSalidaTriage validador;
    private final int lote;

    public TriarQuejasConfirmadas(QuejasParaTriage pendientes, TriageRepository triages, LlmClient llm,
            ValidadorSalidaTriage validador, int lote) {
        this.pendientes = pendientes;
        this.triages = triages;
        this.llm = llm;
        this.validador = validador;
        this.lote = lote;
    }

    public Resultado ejecutar() {
        List<Queja> quejas = pendientes.confirmadasSinTriage(lote);
        int sugeridos = 0;
        int manuales = 0;
        for (int i = 0; i < quejas.size(); i++) {
            try {
                if (triar(quejas.get(i)).estado() == EstadoTriage.SUGERIDO) {
                    sugeridos++;
                } else {
                    manuales++;
                }
            } catch (LlmNoDisponibleException e) {
                // Si el proveedor está caído, insistir con el resto del lote solo suma errores.
                log.warn("LLM no disponible; {} quejas quedan para la próxima ejecución", quejas.size() - i, e);
                return new Resultado(sugeridos, manuales, quejas.size() - i);
            }
        }
        return new Resultado(sugeridos, manuales, 0);
    }

    private Triage triar(Queja queja) {
        SolicitudLlm solicitud = PromptTriage.para(queja);
        String modelo = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            RespuestaLlm respuesta = llm.completar(solicitud);
            modelo = respuesta.modelo();
            try {
                SalidaTriage salida = validador.validar(respuesta.texto());
                if (!salida.articulosDescartados().isEmpty()) {
                    log.info("Triage de la queja {}: artículos fuera de la lista cerrada descartados {}",
                            queja.id(), salida.articulosDescartados());
                }
                Triage triage = Triage.sugerido(queja.id(), salida.categoria(), salida.resumen(), salida.prioridad(),
                        salida.articulos(), modelo, intento);
                triages.guardar(triage);
                return triage;
            } catch (SalidaInvalidaException e) {
                log.warn("Triage de la queja {}: salida inválida en el intento {} de {} ({})",
                        queja.id(), intento, MAX_INTENTOS, e.getMessage());
            }
        }
        Triage manual = Triage.manual(queja.id(), modelo, MAX_INTENTOS);
        triages.guardar(manual);
        return manual;
    }

    /** @param pendientes quejas del lote que no se procesaron porque el LLM no estaba disponible */
    public record Resultado(int sugeridos, int manuales, int pendientes) {
    }
}
