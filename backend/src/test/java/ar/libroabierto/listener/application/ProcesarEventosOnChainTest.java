package ar.libroabierto.listener.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcesarEventosOnChainTest {

    private static final String HASH_A = "0x" + "a".repeat(64);
    private static final String HASH_B = "0x" + "b".repeat(64);
    private static final String HASH_DESCONOCIDO = "0x" + "f".repeat(64);

    private RepositorioEnMemoria repositorio;
    private CadenaFalsa cadena;
    private CheckpointEnMemoria checkpoint;
    private TriageEspia triage;

    @BeforeEach
    void setUp() {
        repositorio = new RepositorioEnMemoria();
        cadena = new CadenaFalsa();
        checkpoint = new CheckpointEnMemoria();
        triage = new TriageEspia();
    }

    @Test
    void confirmaLaQuejaCorrectaPorContentHashYDisparaElTriage() {
        Queja a = repositorio.agregar(enviada(HASH_A));
        Queja b = repositorio.agregar(enviada(HASH_B));
        cadena.emitir(evento(1, HASH_B, 5));
        cadena.bloqueActual = 5;

        ProcesarEventosOnChain.Resultado resultado = listener(0, 0, 100).ejecutar();

        assertThat(resultado.confirmadas()).isEqualTo(1);
        assertThat(repositorio.actual(b.id()).anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
        assertThat(repositorio.actual(b.id()).onchainId()).isEqualTo(1L);
        assertThat(repositorio.actual(b.id()).bloqueConfirmacion()).isEqualTo(5L);
        assertThat(repositorio.actual(a.id()).anchorStatus()).isEqualTo(AnchorStatus.ENVIADA);
        assertThat(repositorio.guardadas).containsExactly(b.id());
        assertThat(triage.disparados).containsExactly(b.id());
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(5));
    }

    @Test
    void reprocesarLosMismosEventosNoReescribeNiDuplicaElTriage() {
        Queja a = repositorio.agregar(enviada(HASH_A));
        cadena.emitir(evento(1, HASH_A, 3));
        cadena.bloqueActual = 3;
        listener(0, 0, 100).ejecutar();

        // Simula un reinicio que perdió el checkpoint: vuelve a leer desde el bloque 0.
        checkpoint.valor = OptionalLong.empty();
        ProcesarEventosOnChain.Resultado replay = listener(0, 0, 100).ejecutar();

        assertThat(replay.confirmadas()).isZero();
        assertThat(replay.ignorados()).isEqualTo(1);
        assertThat(repositorio.actual(a.id()).onchainId()).isEqualTo(1L);
        assertThat(repositorio.guardadas).hasSize(1);
        assertThat(triage.disparados).containsExactly(a.id());
    }

    @Test
    void retomaDesdeElBloqueSiguienteAlCheckpoint() {
        checkpoint.valor = OptionalLong.of(10);
        cadena.bloqueActual = 15;

        listener(0, 0, 100).ejecutar();

        assertThat(cadena.consultas).containsExactly(new Rango(11, 15));
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(15));
    }

    @Test
    void sinCheckpointArrancaDesdeElBloqueInicialConfigurado() {
        cadena.bloqueActual = 30;

        listener(20, 0, 100).ejecutar();

        assertThat(cadena.consultas).containsExactly(new Rango(20, 30));
    }

    @Test
    void esperaLasConfirmacionesYNoLeeBloquesQueTodaviaPuedenReorganizarse() {
        checkpoint.valor = OptionalLong.of(10);
        cadena.bloqueActual = 12;

        ProcesarEventosOnChain.Resultado resultado = listener(0, 2, 100).ejecutar();

        assertThat(cadena.consultas).isEmpty();
        assertThat(resultado.hastaBloque()).isEqualTo(10);
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(10));

        cadena.bloqueActual = 14;
        listener(0, 2, 100).ejecutar();
        assertThat(cadena.consultas).containsExactly(new Rango(11, 12));
    }

    @Test
    void recorreRangosLargosEnLotesYGuardaElCheckpointDeCadaLote() {
        cadena.bloqueActual = 249;

        listener(0, 0, 100).ejecutar();

        assertThat(cadena.consultas).containsExactly(new Rango(0, 99), new Rango(100, 199), new Rango(200, 249));
        assertThat(checkpoint.historial).containsExactly(99L, 199L, 249L);
    }

    @Test
    void unEventoDeUnaQuejaDesconocidaSeIgnoraYNoFrenaAlListener() {
        Queja a = repositorio.agregar(enviada(HASH_A));
        cadena.emitir(evento(1, HASH_DESCONOCIDO, 2));
        cadena.emitir(evento(2, HASH_A, 3));
        cadena.bloqueActual = 3;

        ProcesarEventosOnChain.Resultado resultado = listener(0, 0, 100).ejecutar();

        assertThat(resultado.confirmadas()).isEqualTo(1);
        assertThat(resultado.ignorados()).isEqualTo(1);
        assertThat(repositorio.actual(a.id()).onchainId()).isEqualTo(2L);
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(3));
    }

    @Test
    void siFallaLaBaseNoAvanzaElCheckpointYElLoteSeReintenta() {
        Queja a = repositorio.agregar(enviada(HASH_A));
        cadena.emitir(evento(1, HASH_A, 3));
        cadena.bloqueActual = 3;
        repositorio.fallar = true;

        assertThatThrownBy(() -> listener(0, 0, 100).ejecutar()).isInstanceOf(IllegalStateException.class);
        assertThat(checkpoint.valor).isEmpty();
        assertThat(repositorio.actual(a.id()).anchorStatus()).isEqualTo(AnchorStatus.ENVIADA);
        assertThat(triage.disparados).isEmpty();

        repositorio.fallar = false;
        listener(0, 0, 100).ejecutar();
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(3));
        assertThat(repositorio.actual(a.id()).anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
        assertThat(triage.disparados).containsExactly(a.id());
    }

    @Test
    void unFalloDelTriageNoDeshaceLaConfirmacionNiFrenaElCheckpoint() {
        Queja a = repositorio.agregar(enviada(HASH_A));
        cadena.emitir(evento(1, HASH_A, 3));
        cadena.bloqueActual = 3;
        triage.fallar = true;

        listener(0, 0, 100).ejecutar();

        assertThat(repositorio.actual(a.id()).anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
        assertThat(repositorio.guardadas).containsExactly(a.id());
        assertThat(checkpoint.valor).isEqualTo(OptionalLong.of(3));
    }

    private ProcesarEventosOnChain listener(long bloqueInicial, int confirmaciones, int maxBloquesPorLote) {
        return new ProcesarEventosOnChain(repositorio, cadena, checkpoint, triage,
                bloqueInicial, confirmaciones, maxBloquesPorLote);
    }

    private static EventoQuejaRegistrada evento(long onchainId, String contentHash, long bloque) {
        return new EventoQuejaRegistrada(onchainId, 30712345678L, contentHash, "bafy-doc", bloque,
                String.format("0x%064x", onchainId));
    }

    private static Queja enviada(String hash) {
        Queja queja = Queja.nueva(UUID.randomUUID(), UUID.randomUUID(), new Empresa("30712345678", "Empresa S.A."),
                "telecomunicaciones", "Descripción de prueba.", new BigDecimal("1000"),
                LocalDate.parse("2026-10-15"), List.of(), Instant.parse("2026-10-18T12:00:00Z"));
        queja.registrarPublicacion("bafy-doc", hash);
        queja.marcarEnviada("0x" + "9".repeat(64));
        return queja;
    }

    record Rango(long desde, long hasta) {
    }

    static class RepositorioEnMemoria implements QuejaRepository {
        private final Map<UUID, Queja> quejas = new LinkedHashMap<>();
        final List<UUID> guardadas = new ArrayList<>();
        boolean fallar;

        Queja agregar(Queja queja) {
            quejas.put(queja.id(), queja);
            return queja;
        }

        @Override
        public List<Queja> pendientesDeAnclaje() {
            return List.of();
        }

        /** Última versión guardada de la queja. */
        Queja actual(UUID id) {
            return quejas.get(id);
        }

        /** Como la base real, devuelve una copia: modificarla no cambia lo guardado hasta llamar a guardar. */
        @Override
        public Optional<Queja> porContentHash(String contentHash) {
            return quejas.values().stream().filter(q -> contentHash.equals(q.contentHash())).findFirst()
                    .map(q -> Queja.reconstituir(q.id(), q.usuarioId(), q.empresa(), q.categoria(), q.descripcion(),
                            q.montoReclamado(), q.fechaHecho(), q.evidenciasCids(), q.registradaEn(),
                            q.anchorStatus(), q.ipfsCid(), q.contentHash(), q.txHash(), q.onchainId()));
        }

        @Override
        public void guardar(Queja queja) {
            if (fallar) {
                throw new IllegalStateException("Base no disponible");
            }
            quejas.put(queja.id(), queja);
            guardadas.add(queja.id());
        }
    }

    static class CadenaFalsa implements FuenteDeEventos {
        private final List<EventoQuejaRegistrada> eventos = new ArrayList<>();
        final List<Rango> consultas = new ArrayList<>();
        long bloqueActual;

        void emitir(EventoQuejaRegistrada evento) {
            eventos.add(evento);
        }

        @Override
        public long bloqueActual() {
            return bloqueActual;
        }

        @Override
        public List<EventoQuejaRegistrada> quejasRegistradas(long desde, long hasta) {
            consultas.add(new Rango(desde, hasta));
            return eventos.stream().filter(e -> e.bloque() >= desde && e.bloque() <= hasta).toList();
        }
    }

    static class CheckpointEnMemoria implements CheckpointRepository {
        OptionalLong valor = OptionalLong.empty();
        final List<Long> historial = new ArrayList<>();

        @Override
        public OptionalLong ultimoBloque() {
            return valor;
        }

        @Override
        public void guardar(long bloque) {
            valor = OptionalLong.of(bloque);
            historial.add(bloque);
        }
    }

    static class TriageEspia implements DisparadorTriage {
        final List<UUID> disparados = new ArrayList<>();
        boolean fallar;

        @Override
        public void quejaConfirmada(UUID quejaId) {
            if (fallar) {
                throw new IllegalStateException("Triage no disponible");
            }
            disparados.add(quejaId);
        }
    }
}
