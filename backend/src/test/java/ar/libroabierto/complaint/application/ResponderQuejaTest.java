package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.complaint.domain.AnchorStatus;
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

class ResponderQuejaTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");

    private final UUID empresa = UUID.randomUUID();
    private final UUID representante = UUID.randomUUID();
    private final UUID queja = UUID.randomUUID();
    private RespuestasEnMemoria respuestas;
    private ResponderQueja responder;

    @BeforeEach
    void setUp() {
        respuestas = new RespuestasEnMemoria();
        respuestas.empresas.put(representante, empresa);
        respuestas.situaciones.put(queja, new Respuestas.Situacion(empresa, AnchorStatus.CONFIRMADA, false));
        responder = new ResponderQueja(respuestas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registraLaRespuestaConSuAutorYElTextoSinEspaciosSobrantes() {
        UUID id = responder.ejecutar(representante, queja, "  Ya restablecimos el servicio.  ");

        assertThat(respuestas.registradas).containsExactly(
                new Respuestas.NuevaRespuesta(id, queja, representante, "Ya restablecimos el servicio.", AHORA));
    }

    @Test
    void unTextoVacioODemasiadoLargoEsInvalido() {
        assertThatThrownBy(() -> responder.ejecutar(representante, queja, "   "))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> responder.ejecutar(representante, queja, null))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> responder.ejecutar(representante, queja, "x".repeat(ResponderQueja.MAX_TEXTO + 1)))
                .isInstanceOf(DatosInvalidosException.class);
        assertThat(responder.ejecutar(representante, queja, "x".repeat(ResponderQueja.MAX_TEXTO))).isNotNull();
    }

    @Test
    void unaQuejaInexistenteEsNoEncontrada() {
        assertThatThrownBy(() -> responder.ejecutar(representante, UUID.randomUUID(), "Texto"))
                .isInstanceOf(QuejaNoEncontradaException.class);
    }

    @Test
    void soloRespondenLosRepresentantesDeLaEmpresaDenunciada() {
        UUID deOtraEmpresa = UUID.randomUUID();
        respuestas.empresas.put(deOtraEmpresa, UUID.randomUUID());

        assertThatThrownBy(() -> responder.ejecutar(deOtraEmpresa, queja, "Texto"))
                .isInstanceOf(RespuestaNoPermitidaException.class);
        assertThatThrownBy(() -> responder.ejecutar(UUID.randomUUID(), queja, "Texto"))
                .isInstanceOf(RespuestaNoPermitidaException.class);
        assertThat(respuestas.registradas).isEmpty();
    }

    @Test
    void unaQuejaSinAnclarTodaviaNoSePuedeResponder() {
        respuestas.situaciones.put(queja, new Respuestas.Situacion(empresa, AnchorStatus.ENVIADA, false));

        assertThatThrownBy(() -> responder.ejecutar(representante, queja, "Texto"))
                .isInstanceOf(RespuestaNoDisponibleException.class);
    }

    @Test
    void hayUnaSolaRespuestaPorQueja() {
        respuestas.situaciones.put(queja, new Respuestas.Situacion(empresa, AnchorStatus.CONFIRMADA, true));

        assertThatThrownBy(() -> responder.ejecutar(representante, queja, "Texto"))
                .isInstanceOf(RespuestaNoDisponibleException.class);
        assertThat(respuestas.registradas).isEmpty();
    }

    @Test
    void siOtraRespuestaSeGuardoEnElMedioTambienEsUnConflicto() {
        respuestas.rechazar = true;

        assertThatThrownBy(() -> responder.ejecutar(representante, queja, "Texto"))
                .isInstanceOf(RespuestaNoDisponibleException.class);
    }

    static class RespuestasEnMemoria implements Respuestas {
        final Map<UUID, UUID> empresas = new HashMap<>();
        final Map<UUID, Situacion> situaciones = new HashMap<>();
        final List<NuevaRespuesta> registradas = new ArrayList<>();
        boolean rechazar;

        @Override
        public Optional<UUID> empresaDe(UUID usuarioId) {
            return Optional.ofNullable(empresas.get(usuarioId));
        }

        @Override
        public Optional<Situacion> situacion(UUID quejaId) {
            return Optional.ofNullable(situaciones.get(quejaId));
        }

        @Override
        public boolean registrar(NuevaRespuesta respuesta) {
            if (rechazar) {
                return false;
            }
            registradas.add(respuesta);
            return true;
        }

        @Override
        public List<RespuestaPendiente> pendientesDeAnclaje() {
            return List.of();
        }

        @Override
        public void registrarPublicacion(UUID respuestaId, String ipfsCid, String contentHash) {
        }

        @Override
        public void marcarEnviada(UUID respuestaId, String txHash) {
        }
    }
}
