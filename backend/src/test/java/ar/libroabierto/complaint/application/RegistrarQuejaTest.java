package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrarQuejaTest {

    // 12:00 en Buenos Aires (UTC-3).
    private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");
    private static final String CUIT_EXISTENTE = "30500010912";
    private static final String CUIT_NUEVO = "30712345671";

    private final UUID ciudadano = UUID.randomUUID();
    private DenunciantesEnMemoria denunciantes;
    private EmpresasEnMemoria empresas;
    private RegistroEnMemoria registro;
    private RegistrarQueja registrar;

    @BeforeEach
    void setUp() {
        denunciantes = new DenunciantesEnMemoria();
        denunciantes.agregar(new Denunciante(ciudadano, true, true));
        empresas = new EmpresasEnMemoria();
        empresas.agregar(new Empresa(CUIT_EXISTENTE, "Banco de la Nación Argentina"));
        registro = new RegistroEnMemoria();
        registrar = new RegistrarQueja(denunciantes, empresas, registro, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registraLaQuejaPendienteConLaEmpresaExistente() {
        UUID id = registrar.ejecutar(nueva(CUIT_EXISTENTE, "Otro nombre que manda el cliente"));

        Queja queja = registro.quejas.get(0);
        assertThat(queja.id()).isEqualTo(id);
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
        assertThat(queja.usuarioId()).isEqualTo(ciudadano);
        // La razón social de una empresa ya registrada no la cambia un denunciante.
        assertThat(queja.empresa()).isEqualTo(new Empresa(CUIT_EXISTENTE, "Banco de la Nación Argentina"));
        assertThat(queja.categoria()).isEqualTo("bancos");
        assertThat(queja.descripcion()).isEqualTo("Me cobraron dos veces el mantenimiento de cuenta.");
        assertThat(queja.montoReclamado()).isEqualByComparingTo("15000.50");
        assertThat(queja.fechaHecho()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(queja.registradaEn()).isEqualTo(AHORA);
        assertThat(queja.evidenciasCids()).isEmpty();
    }

    @Test
    void unaEmpresaNuevaSeRegistraConLaRazonSocialInformada() {
        registrar.ejecutar(nueva(CUIT_NUEVO, "  Empresa Nueva S.A.  "));

        assertThat(registro.quejas.get(0).empresa()).isEqualTo(new Empresa(CUIT_NUEVO, "Empresa Nueva S.A."));
    }

    @Test
    void unaEmpresaNuevaSinRazonSocialEsInvalida() {
        assertThatThrownBy(() -> registrar.ejecutar(nueva(CUIT_NUEVO, null)))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.errores()).containsOnlyKeys("razonSocial"));
        assertThat(registro.quejas).isEmpty();
    }

    @Test
    void unCuitConDigitoVerificadorInvalidoNoCreaLaQueja() {
        assertThatThrownBy(() -> registrar.ejecutar(nueva("30712345678", "Empresa S.A.")))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.errores()).containsOnlyKeys("cuitEmpresa"));
        assertThat(registro.quejas).isEmpty();
    }

    @Test
    void informaTodosLosCamposInvalidosJuntos() {
        NuevaQueja invalida = new NuevaQueja(ciudadano, "123", null, " ", "x".repeat(5001),
                new BigDecimal("-1"), LocalDate.parse("2026-11-13"));

        assertThatThrownBy(() -> registrar.ejecutar(invalida))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.errores())
                        .containsOnlyKeys("cuitEmpresa", "categoria", "descripcion", "montoReclamado", "fechaHecho"));
    }

    @Test
    void laFechaDelHechoPuedeSerHoyEnArgentinaAunqueEnUtcSeaManana() {
        // 23:30 del 12/11 en Buenos Aires ya es 13/11 en UTC.
        Clock tarde = Clock.fixed(Instant.parse("2026-11-13T02:30:00Z"), ZoneOffset.UTC);
        RegistrarQueja deNoche = new RegistrarQueja(denunciantes, empresas, registro, tarde);

        deNoche.ejecutar(new NuevaQueja(ciudadano, CUIT_EXISTENTE, null, "bancos", "Cobro indebido.", null,
                LocalDate.parse("2026-11-12")));

        assertThat(registro.quejas).hasSize(1);
    }

    @Test
    void unMontoConMasDeDosDecimalesEsInvalido() {
        NuevaQueja queja = new NuevaQueja(ciudadano, CUIT_EXISTENTE, null, "bancos", "Cobro indebido.",
                new BigDecimal("10.001"), LocalDate.parse("2026-11-01"));

        assertThatThrownBy(() -> registrar.ejecutar(queja))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.errores()).containsOnlyKeys("montoReclamado"));
    }

    @Test
    void unUsuarioSinIdentidadVerificadaNoPuedeRegistrar() {
        UUID sinVerificar = UUID.randomUUID();
        denunciantes.agregar(new Denunciante(sinVerificar, true, false));

        assertThatThrownBy(() -> registrar.ejecutar(nueva(sinVerificar, CUIT_EXISTENTE, null)))
                .isInstanceOf(DenuncianteNoHabilitadoException.class);
        assertThat(registro.quejas).isEmpty();
    }

    @Test
    void unUsuarioQueNoEsCiudadanoNoPuedeRegistrar() {
        UUID funcionario = UUID.randomUUID();
        denunciantes.agregar(new Denunciante(funcionario, false, true));

        assertThatThrownBy(() -> registrar.ejecutar(nueva(funcionario, CUIT_EXISTENTE, null)))
                .isInstanceOf(DenuncianteNoHabilitadoException.class);
    }

    @Test
    void conCincoQuejasHoyLaSextaSuperaElLimiteDiario() {
        for (int i = 0; i < 5; i++) {
            registrar.ejecutar(nueva(CUIT_EXISTENTE, null));
        }

        assertThatThrownBy(() -> registrar.ejecutar(nueva(CUIT_EXISTENTE, null)))
                .isInstanceOf(LimiteDiarioAlcanzadoException.class);
        assertThat(registro.quejas).hasSize(5);
    }

    @Test
    void elLimiteCuentaDesdeLaMedianocheDeArgentina() {
        registrar.ejecutar(nueva(CUIT_EXISTENTE, null));

        // 00:00 del 12/11 en Buenos Aires = 03:00 UTC.
        assertThat(registro.consultasDesde).containsExactly(Instant.parse("2026-11-12T03:00:00Z"));
    }

    private NuevaQueja nueva(String cuit, String razonSocial) {
        return nueva(ciudadano, cuit, razonSocial);
    }

    private static NuevaQueja nueva(UUID usuario, String cuit, String razonSocial) {
        return new NuevaQueja(usuario, cuit, razonSocial, " bancos ",
                "Me cobraron dos veces el mantenimiento de cuenta.", new BigDecimal("15000.50"),
                LocalDate.parse("2026-11-01"));
    }

    static class DenunciantesEnMemoria implements Denunciantes {
        private final Map<UUID, Denunciante> porId = new HashMap<>();

        void agregar(Denunciante denunciante) {
            porId.put(denunciante.id(), denunciante);
        }

        @Override
        public Optional<Denunciante> porId(UUID id) {
            return Optional.ofNullable(porId.get(id));
        }
    }

    static class EmpresasEnMemoria implements EmpresaRepository {
        private final Map<String, Empresa> porCuit = new HashMap<>();

        void agregar(Empresa empresa) {
            porCuit.put(empresa.cuit(), empresa);
        }

        @Override
        public Optional<Empresa> porCuit(String cuit) {
            return Optional.ofNullable(porCuit.get(cuit));
        }
    }

    static class RegistroEnMemoria implements RegistroDeQuejas {
        final List<Queja> quejas = new ArrayList<>();
        final List<Instant> consultasDesde = new ArrayList<>();

        @Override
        public void registrar(Queja queja) {
            quejas.add(queja);
        }

        @Override
        public long registradasDesde(UUID usuarioId, Instant desde) {
            consultasDesde.add(desde);
            return quejas.stream().filter(q -> q.usuarioId().equals(usuarioId))
                    .filter(q -> !q.registradaEn().isBefore(desde)).count();
        }
    }
}
