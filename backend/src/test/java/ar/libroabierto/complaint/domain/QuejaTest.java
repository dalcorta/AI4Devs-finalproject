package ar.libroabierto.complaint.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuejaTest {

    private static final String HASH = "0x" + "a".repeat(64);
    private static final String TX = "0x" + "b".repeat(64);

    @Test
    void unaQuejaEnviadaSeConfirmaConSuIdOnChain() {
        Queja queja = publicada();
        queja.marcarEnviada(TX);

        boolean cambio = queja.confirmarAnclaje(7, TX, 42);

        assertThat(cambio).isTrue();
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
        assertThat(queja.onchainId()).isEqualTo(7L);
        assertThat(queja.txHash()).isEqualTo(TX);
        assertThat(queja.bloqueConfirmacion()).isEqualTo(42L);
    }

    @Test
    void unaQuejaPublicadaQueQuedoPendienteTambienSeConfirma() {
        // El worker pudo caerse después de enviar la transacción y antes de marcarla ENVIADA:
        // la cadena es la fuente de verdad, así que el evento la confirma igual.
        Queja queja = publicada();

        assertThat(queja.confirmarAnclaje(7, TX, 42)).isTrue();
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
    }

    @Test
    void confirmarDosVecesConElMismoIdNoCambiaNada() {
        Queja queja = publicada();
        queja.confirmarAnclaje(7, TX, 42);

        boolean cambio = queja.confirmarAnclaje(7, "0x" + "c".repeat(64), 99);

        assertThat(cambio).isFalse();
        assertThat(queja.txHash()).isEqualTo(TX);
        assertThat(queja.bloqueConfirmacion()).isEqualTo(42L);
    }

    @Test
    void unaQuejaConfirmadaNoAceptaOtroIdOnChain() {
        Queja queja = publicada();
        queja.confirmarAnclaje(7, TX, 42);

        assertThatThrownBy(() -> queja.confirmarAnclaje(8, TX, 43)).isInstanceOf(IllegalStateException.class);
        assertThat(queja.onchainId()).isEqualTo(7L);
    }

    @Test
    void noSePuedeConfirmarUnaQuejaSinPublicar() {
        Queja queja = nueva();

        assertThatThrownBy(() -> queja.confirmarAnclaje(7, TX, 42)).isInstanceOf(IllegalStateException.class);
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
    }

    @Test
    void unaQuejaConfirmadaNoVuelveAEnviarse() {
        Queja queja = publicada();
        queja.confirmarAnclaje(7, TX, 42);

        assertThatThrownBy(() -> queja.marcarEnviada(TX)).isInstanceOf(IllegalStateException.class);
    }

    private static Queja publicada() {
        Queja queja = nueva();
        queja.registrarPublicacion("bafy-doc", HASH);
        return queja;
    }

    private static Queja nueva() {
        return Queja.nueva(UUID.randomUUID(), UUID.randomUUID(), new Empresa("30712345678", "Empresa Ficticia S.A."),
                "telecomunicaciones", "Corte de servicio.", new BigDecimal("1000"), LocalDate.parse("2026-10-15"),
                List.of(), Instant.parse("2026-10-18T12:00:00Z"));
    }
}
