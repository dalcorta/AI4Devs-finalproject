package ar.libroabierto.triage.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.triage.domain.EstadoTriage;
import ar.libroabierto.triage.domain.Prioridad;
import ar.libroabierto.triage.domain.Triage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TriarQuejasConfirmadasTest {

    private static final String VALIDA = """
            {"categoria": "telecomunicaciones", "resumen": "Corte de internet de 15 días.", "prioridad": "MEDIA",
             "articulos": ["19"]}""";
    private static final String INVALIDA = "{\"categoria\": \"telecomunicaciones\"}";

    private PendientesEnMemoria pendientes;
    private TriagesEnMemoria triages;
    private LlmGuionado llm;
    private TriarQuejasConfirmadas worker;

    @BeforeEach
    void setUp() {
        pendientes = new PendientesEnMemoria();
        triages = new TriagesEnMemoria(pendientes);
        llm = new LlmGuionado();
        worker = new TriarQuejasConfirmadas(pendientes, triages, llm, new ValidadorSalidaTriage(), 10);
    }

    @Test
    void unaSalidaValidaQuedaComoTriageSugeridoConElModeloUsado() {
        Queja queja = pendientes.agregar(confirmada());
        llm.responder(VALIDA);

        TriarQuejasConfirmadas.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new TriarQuejasConfirmadas.Resultado(1, 0, 0));
        Triage triage = triages.de(queja.id());
        assertThat(triage.estado()).isEqualTo(EstadoTriage.SUGERIDO);
        assertThat(triage.categoriaSugerida()).isEqualTo("telecomunicaciones");
        assertThat(triage.prioridad()).isEqualTo(Prioridad.MEDIA);
        assertThat(triage.articulos()).containsExactly("19");
        assertThat(triage.modelo()).isEqualTo("modelo-de-prueba");
        assertThat(triage.intentos()).isEqualTo(1);
    }

    @Test
    void unArticuloInventadoPorElLlmNoLlegaAlTriageGuardado() {
        Queja queja = pendientes.agregar(confirmada());
        llm.responder("""
                {"categoria": "telecomunicaciones", "resumen": "Corte.", "prioridad": "ALTA",
                 "articulos": ["19", "123 quater"]}""");

        worker.ejecutar();

        assertThat(triages.de(queja.id()).articulos()).containsExactly("19");
    }

    @Test
    void reintentaAnteSalidasInvalidasYAceptaLaTercera() {
        Queja queja = pendientes.agregar(confirmada());
        llm.responder(INVALIDA, "no es json", VALIDA);

        worker.ejecutar();

        assertThat(llm.pedidos).hasSize(3);
        assertThat(triages.de(queja.id()).estado()).isEqualTo(EstadoTriage.SUGERIDO);
        assertThat(triages.de(queja.id()).intentos()).isEqualTo(3);
    }

    @Test
    void trasDosReintentosFallidosLaQuejaQuedaEnTriageManual() {
        Queja queja = pendientes.agregar(confirmada());
        llm.responder(INVALIDA, INVALIDA, INVALIDA, VALIDA);

        TriarQuejasConfirmadas.Resultado resultado = worker.ejecutar();

        assertThat(llm.pedidos).hasSize(3);
        assertThat(resultado).isEqualTo(new TriarQuejasConfirmadas.Resultado(0, 1, 0));
        Triage triage = triages.de(queja.id());
        assertThat(triage.estado()).isEqualTo(EstadoTriage.MANUAL);
        assertThat(triage.categoriaSugerida()).isNull();
        assertThat(triage.articulos()).isEmpty();
        assertThat(triage.modelo()).isEqualTo("modelo-de-prueba");
        assertThat(triage.intentos()).isEqualTo(3);
    }

    @Test
    void unaQuejaYaTriadaNoVuelveAlLlm() {
        pendientes.agregar(confirmada());
        llm.responder(VALIDA, VALIDA);
        worker.ejecutar();

        TriarQuejasConfirmadas.Resultado segunda = worker.ejecutar();

        assertThat(segunda).isEqualTo(new TriarQuejasConfirmadas.Resultado(0, 0, 0));
        assertThat(llm.pedidos).hasSize(1);
        assertThat(triages.guardados).hasSize(1);
    }

    @Test
    void siElLlmNoEstaDisponibleNoGuardaNadaYCortaElLoteHastaLaProximaEjecucion() {
        Queja primera = pendientes.agregar(confirmada());
        pendientes.agregar(confirmada());
        llm.caido = true;

        TriarQuejasConfirmadas.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new TriarQuejasConfirmadas.Resultado(0, 0, 2));
        assertThat(llm.pedidos).hasSize(1);
        assertThat(triages.guardados).isEmpty();

        llm.caido = false;
        llm.responder(VALIDA, VALIDA);
        assertThat(worker.ejecutar()).isEqualTo(new TriarQuejasConfirmadas.Resultado(2, 0, 0));
        assertThat(triages.de(primera.id()).estado()).isEqualTo(EstadoTriage.SUGERIDO);
    }

    @Test
    void cadaPedidoLlevaElDocumentoPublicoDeLaQueja() {
        Queja queja = pendientes.agregar(confirmada());
        llm.responder(VALIDA);

        worker.ejecutar();

        assertThat(llm.pedidos.get(0)).isEqualTo(PromptTriage.para(queja));
    }

    private static Queja confirmada() {
        Queja queja = Queja.nueva(UUID.randomUUID(), UUID.randomUUID(), new Empresa("30712345678", "Empresa S.A."),
                "telecomunicaciones", "Corte de servicio de internet durante 15 días.", new BigDecimal("15000"),
                LocalDate.parse("2026-10-15"), List.of(), Instant.parse("2026-10-18T12:00:00Z"));
        queja.registrarPublicacion("bafy-doc", "0x" + UUID.randomUUID().toString().replace("-", "").repeat(2));
        queja.confirmarAnclaje(Math.abs(queja.id().getMostSignificantBits() % 1000) + 1, "0x" + "b".repeat(64), 10);
        return queja;
    }

    /** Las quejas confirmadas que todavía no tienen un triage guardado, como la consulta real. */
    static class PendientesEnMemoria implements QuejasParaTriage {
        private final Map<UUID, Queja> quejas = new LinkedHashMap<>();
        private TriagesEnMemoria triages;

        Queja agregar(Queja queja) {
            quejas.put(queja.id(), queja);
            return queja;
        }

        @Override
        public List<Queja> confirmadasSinTriage(int limite) {
            return quejas.values().stream().filter(q -> !triages.guardados.containsKey(q.id())).limit(limite).toList();
        }
    }

    static class TriagesEnMemoria implements TriageRepository {
        final Map<UUID, Triage> guardados = new LinkedHashMap<>();

        TriagesEnMemoria(PendientesEnMemoria pendientes) {
            pendientes.triages = this;
        }

        Triage de(UUID quejaId) {
            return guardados.get(quejaId);
        }

        @Override
        public void guardar(Triage triage) {
            guardados.putIfAbsent(triage.quejaId(), triage);
        }
    }

    static class LlmGuionado implements LlmClient {
        final List<SolicitudLlm> pedidos = new ArrayList<>();
        private final Deque<String> respuestas = new ArrayDeque<>();
        boolean caido;

        void responder(String... textos) {
            respuestas.addAll(List.of(textos));
        }

        @Override
        public RespuestaLlm completar(SolicitudLlm solicitud) {
            pedidos.add(solicitud);
            if (caido) {
                throw new LlmNoDisponibleException("Proveedor no disponible", null);
            }
            return new RespuestaLlm(respuestas.removeFirst(), "modelo-de-prueba");
        }
    }
}
