package ar.libroabierto.complaint.domain;

import static ar.libroabierto.complaint.domain.EstadoQueja.DERIVADA;
import static ar.libroabierto.complaint.domain.EstadoQueja.DESESTIMADA;
import static ar.libroabierto.complaint.domain.EstadoQueja.EN_REVISION;
import static ar.libroabierto.complaint.domain.EstadoQueja.REGISTRADA;
import static ar.libroabierto.complaint.domain.EstadoQueja.RESUELTA;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tiene que ser la misma máquina de estados que _isValidTransition en ComplaintRegistry.sol. */
class EstadoQuejaTest {

    private static final Set<String> PERMITIDAS = Set.of(
            "REGISTRADA->EN_REVISION", "EN_REVISION->DERIVADA", "DERIVADA->RESUELTA", "DERIVADA->DESESTIMADA");

    @Test
    void soloSePermitenLasTransicionesDelContrato() {
        for (EstadoQueja desde : EstadoQueja.values()) {
            for (EstadoQueja hacia : EstadoQueja.values()) {
                assertThat(desde.puedePasarA(hacia))
                        .as(desde + " -> " + hacia)
                        .isEqualTo(PERMITIDAS.contains(desde + "->" + hacia));
            }
        }
    }

    @Test
    void elOrdenCoincideConElEnumDelContrato() {
        // El adaptador manda el ordinal como uint8: el orden del enum es parte del contrato.
        assertThat(Arrays.stream(EstadoQueja.values()).map(Enum::name))
                .containsExactly("REGISTRADA", "EN_REVISION", "DERIVADA", "RESUELTA", "DESESTIMADA");
    }

    @Test
    void resueltaYDesestimadaSonFinales() {
        assertThat(RESUELTA.siguientes()).isEmpty();
        assertThat(DESESTIMADA.siguientes()).isEmpty();
        assertThat(DERIVADA.siguientes()).containsExactlyInAnyOrder(RESUELTA, DESESTIMADA);
        assertThat(REGISTRADA.siguientes()).containsExactly(EN_REVISION);
    }
}
