package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CambiarEstadoDeQuejaTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");

    private final UUID funcionario = UUID.randomUUID();
    private CambiosEnMemoria cambios;
    private CambiarEstadoDeQueja cambiar;

    @BeforeEach
    void setUp() {
        cambios = new CambiosEnMemoria();
        cambiar = new CambiarEstadoDeQueja(cambios, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registraElCambioConActorComentarioYFechaYActualizaElEstado() {
        UUID queja = cambios.agregar(AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA);

        UUID id = cambiar.ejecutar(funcionario, queja, EstadoQueja.EN_REVISION, "  Se pidió documentación.  ");

        CambioDeEstado cambio = cambios.registrados.get(0);
        assertThat(cambio.id()).isEqualTo(id);
        assertThat(cambio.quejaId()).isEqualTo(queja);
        assertThat(cambio.anterior()).isEqualTo(EstadoQueja.REGISTRADA);
        assertThat(cambio.nuevo()).isEqualTo(EstadoQueja.EN_REVISION);
        assertThat(cambio.actorId()).isEqualTo(funcionario);
        assertThat(cambio.comentario()).isEqualTo("Se pidió documentación.");
        assertThat(cambio.registradoEn()).isEqualTo(AHORA);
        assertThat(cambios.estadoDe(queja)).isEqualTo(EstadoQueja.EN_REVISION);
    }

    @Test
    void elComentarioEsOpcional() {
        UUID queja = cambios.agregar(AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA);

        cambiar.ejecutar(funcionario, queja, EstadoQueja.EN_REVISION, "   ");

        assertThat(cambios.registrados.get(0).comentario()).isNull();
    }

    @Test
    void unaTransicionFueraDeLaMaquinaDeEstadosNoSePermite() {
        UUID queja = cambios.agregar(AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA);

        assertThatThrownBy(() -> cambiar.ejecutar(funcionario, queja, EstadoQueja.RESUELTA, null))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessageContaining("REGISTRADA").hasMessageContaining("RESUELTA");
        assertThat(cambios.registrados).isEmpty();
    }

    @Test
    void unaQuejaTodaviaNoAncladaNoCambiaDeEstado() {
        UUID queja = cambios.agregar(AnchorStatus.ENVIADA, EstadoQueja.REGISTRADA);

        assertThatThrownBy(() -> cambiar.ejecutar(funcionario, queja, EstadoQueja.EN_REVISION, null))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessageContaining("anclada");
    }

    @Test
    void unaQuejaInexistenteNoSeEncuentra() {
        assertThatThrownBy(() -> cambiar.ejecutar(funcionario, UUID.randomUUID(), EstadoQueja.EN_REVISION, null))
                .isInstanceOf(QuejaNoEncontradaException.class);
    }

    @Test
    void siOtroFuncionarioLaCambioEnElMedioNoSePisaElCambio() {
        UUID queja = cambios.agregar(AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA);
        cambios.otroFuncionarioSeAdelanta = true;

        assertThatThrownBy(() -> cambiar.ejecutar(funcionario, queja, EstadoQueja.EN_REVISION, null))
                .isInstanceOf(TransicionNoPermitidaException.class);
    }

    @Test
    void unComentarioDeMasDeMilCaracteresEsInvalido() {
        UUID queja = cambios.agregar(AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA);

        assertThatThrownBy(() -> cambiar.ejecutar(funcionario, queja, EstadoQueja.EN_REVISION, "x".repeat(1001)))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.errores()).containsOnlyKeys("comentario"));
    }

    static class CambiosEnMemoria implements CambiosDeEstado {
        private final Map<UUID, Situacion> quejas = new HashMap<>();
        final List<CambioDeEstado> registrados = new ArrayList<>();
        boolean otroFuncionarioSeAdelanta;

        UUID agregar(AnchorStatus anclaje, EstadoQueja estado) {
            UUID id = UUID.randomUUID();
            quejas.put(id, new Situacion(anclaje == AnchorStatus.CONFIRMADA ? 1L : null, anclaje, estado));
            return id;
        }

        EstadoQueja estadoDe(UUID queja) {
            return quejas.get(queja).estado();
        }

        @Override
        public Optional<Situacion> situacion(UUID quejaId) {
            return Optional.ofNullable(quejas.get(quejaId));
        }

        /** Como el UPDATE condicional de la base: solo cambia si el estado sigue siendo el anterior. */
        @Override
        public boolean registrar(CambioDeEstado cambio) {
            Situacion actual = quejas.get(cambio.quejaId());
            if (otroFuncionarioSeAdelanta || actual.estado() != cambio.anterior()) {
                return false;
            }
            quejas.put(cambio.quejaId(), new Situacion(actual.onchainId(), actual.anclaje(), cambio.nuevo()));
            registrados.add(cambio);
            return true;
        }

        @Override
        public List<CambioPendiente> pendientesDeAnclaje() {
            return List.of();
        }

        @Override
        public void marcarEnviado(UUID cambioId, String txHash) {
        }
    }
}
