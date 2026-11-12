package ar.libroabierto.complaint.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CuitTest {

    @ParameterizedTest
    @ValueSource(strings = {"30712345671", "30500010912", "20123456786", "27000000006"})
    void aceptaCuitsConDigitoVerificadorCorrecto(String cuit) {
        assertThat(Cuit.esValido(cuit)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"30712345678", "30500010913", "20123456780"})
    void rechazaUnDigitoVerificadorIncorrecto(String cuit) {
        assertThat(Cuit.esValido(cuit)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3071234567", "307123456711", "30-71234567-1", "3071234567a"})
    void rechazaFormatosQueNoSonOnceDigitos(String cuit) {
        assertThat(Cuit.esValido(cuit)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20000000010", "20000000019"})
    void unRestoQueDaDiezNoTieneDigitoVerificadorValido(String cuit) {
        // 11 - (suma mod 11) = 10 no es un dígito: AFIP cambia el prefijo en esos casos, nunca emite ese CUIT.
        assertThat(Cuit.esValido(cuit)).isFalse();
    }
}
