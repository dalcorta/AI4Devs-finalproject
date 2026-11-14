package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.anchoring.application.AnclajeDeEstados;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnclarCambiosDeEstadoTest {

    // Calculado con `cast keccak "Se pidió documentación."`, no con el código que se prueba.
    private static final String HASH_COMENTARIO = "0x38cfa891fd711ef6d384dbb224906d8918bc731a3d01c36422010e5c838fd019";
    private static final String SIN_COMENTARIO = "0x" + "0".repeat(64);

    private PendientesEnMemoria cambios;
    private CadenaFalsa cadena;
    private AnclarCambiosDeEstado worker;

    @BeforeEach
    void setUp() {
        cambios = new PendientesEnMemoria();
        cadena = new CadenaFalsa();
        worker = new AnclarCambiosDeEstado(cambios, cadena);
    }

    @Test
    void anclaCadaCambioConElHashDelComentarioYGuardaLaTransaccion() {
        CambioPendiente cambio = cambios.agregar(UUID.randomUUID(), 7, EstadoQueja.EN_REVISION, "Se pidió documentación.");

        AnclarCambiosDeEstado.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new AnclarCambiosDeEstado.Resultado(1, 0));
        assertThat(cadena.envios).containsExactly(new Envio(7, EstadoQueja.EN_REVISION, HASH_COMENTARIO));
        assertThat(cambios.enviados).containsEntry(cambio.cambioId(), "0xtx-1");
    }

    @Test
    void sinComentarioElNoteHashEsCero() {
        cambios.agregar(UUID.randomUUID(), 7, EstadoQueja.EN_REVISION, null);

        worker.ejecutar();

        assertThat(cadena.envios.get(0).noteHash()).isEqualTo(SIN_COMENTARIO);
    }

    @Test
    void siFallaUnCambioLosSiguientesDeLaMismaQuejaEsperanYLosDeOtrasSiguen() {
        UUID queja = UUID.randomUUID();
        CambioPendiente primero = cambios.agregar(queja, 7, EstadoQueja.EN_REVISION, null);
        cambios.agregar(queja, 7, EstadoQueja.DERIVADA, null);
        CambioPendiente otra = cambios.agregar(UUID.randomUUID(), 8, EstadoQueja.EN_REVISION, null);
        cadena.fallarPara = Set.of(7L);

        AnclarCambiosDeEstado.Resultado resultado = worker.ejecutar();

        // El contrato exige el orden: mandar DERIVADA antes que EN_REVISION revertiría igual.
        assertThat(resultado).isEqualTo(new AnclarCambiosDeEstado.Resultado(1, 1));
        assertThat(cadena.intentos).containsExactly(7L, 8L);
        assertThat(cambios.enviados).containsOnlyKeys(otra.cambioId()).doesNotContainKey(primero.cambioId());
    }

    @Test
    void losCambiosYaEnviadosNoSeVuelvenAMandar() {
        cambios.agregar(UUID.randomUUID(), 7, EstadoQueja.EN_REVISION, null);
        worker.ejecutar();

        assertThat(worker.ejecutar()).isEqualTo(new AnclarCambiosDeEstado.Resultado(0, 0));
        assertThat(cadena.envios).hasSize(1);
    }

    record Envio(long onchainId, EstadoQueja nuevo, String noteHash) {
    }

    static class PendientesEnMemoria implements CambiosDeEstado {
        private final List<CambioPendiente> pendientes = new ArrayList<>();
        final Map<UUID, String> enviados = new LinkedHashMap<>();

        CambioPendiente agregar(UUID queja, long onchainId, EstadoQueja nuevo, String comentario) {
            CambioPendiente cambio = new CambioPendiente(UUID.randomUUID(), queja, onchainId, nuevo, comentario);
            pendientes.add(cambio);
            return cambio;
        }

        @Override
        public List<CambioPendiente> pendientesDeAnclaje() {
            return pendientes.stream().filter(c -> !enviados.containsKey(c.cambioId())).toList();
        }

        @Override
        public void marcarEnviado(UUID cambioId, String txHash) {
            enviados.put(cambioId, txHash);
        }

        @Override
        public Optional<Situacion> situacion(UUID quejaId) {
            return Optional.empty();
        }

        @Override
        public boolean registrar(CambioDeEstado cambio) {
            return false;
        }
    }

    static class CadenaFalsa implements AnclajeDeEstados {
        final List<Envio> envios = new ArrayList<>();
        final List<Long> intentos = new ArrayList<>();
        Set<Long> fallarPara = Set.of();

        @Override
        public String cambiarEstado(long onchainId, EstadoQueja nuevo, String noteHash) {
            intentos.add(onchainId);
            if (fallarPara.contains(onchainId)) {
                throw new IllegalStateException("La transacción revertiría");
            }
            envios.add(new Envio(onchainId, nuevo, noteHash));
            return "0xtx-" + envios.size();
        }
    }
}
