package ar.libroabierto.triage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.triage.domain.Prioridad;
import org.junit.jupiter.api.Test;

class ValidadorSalidaTriageTest {

    private final ValidadorSalidaTriage validador = new ValidadorSalidaTriage();

    @Test
    void unaSalidaValidaSeAcepta() {
        SalidaTriage salida = validador.validar("""
                {"categoria": "telecomunicaciones", "resumen": "Corte de internet de 15 días sin respuesta.",
                 "prioridad": "MEDIA", "articulos": ["19", "10 bis"]}""");

        assertThat(salida.categoria()).isEqualTo("telecomunicaciones");
        assertThat(salida.resumen()).isEqualTo("Corte de internet de 15 días sin respuesta.");
        assertThat(salida.prioridad()).isEqualTo(Prioridad.MEDIA);
        assertThat(salida.articulos()).containsExactly("19", "10 bis");
    }

    @Test
    void unArticuloFueraDeLaListaCerradaSeDescartaYElRestoSeConserva() {
        SalidaTriage salida = validador.validar("""
                {"categoria": "telecomunicaciones", "resumen": "Corte de servicio.", "prioridad": "ALTA",
                 "articulos": ["19", "999", "Art. 4 de otra ley", "10 bis"]}""");

        assertThat(salida.articulos()).containsExactly("19", "10 bis");
        assertThat(salida.articulosDescartados()).containsExactly("999", "Art. 4 de otra ley");
    }

    @Test
    void losArticulosRepetidosSeCuentanUnaVez() {
        SalidaTriage salida = validador.validar("""
                {"categoria": "bancos", "resumen": "Cobro indebido.", "prioridad": "BAJA",
                 "articulos": ["37", "37"]}""");

        assertThat(salida.articulos()).containsExactly("37");
    }

    @Test
    void sinArticulosEsValido() {
        assertThat(validador.validar("""
                {"categoria": "otros", "resumen": "Sin datos suficientes.", "prioridad": "BAJA", "articulos": []}""")
                .articulos()).isEmpty();
    }

    @Test
    void textoQueNoEsJsonEsInvalido() {
        assertThatThrownBy(() -> validador.validar("La queja es de prioridad alta."))
                .isInstanceOf(SalidaInvalidaException.class);
    }

    @Test
    void faltaUnCampoObligatorio() {
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": "telecomunicaciones", "prioridad": "MEDIA", "articulos": []}"""))
                .isInstanceOf(SalidaInvalidaException.class)
                .hasMessageContaining("resumen");
    }

    @Test
    void unaPrioridadFueraDelEnumEsInvalida() {
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": "telecomunicaciones", "resumen": "Corte.", "prioridad": "URGENTE", "articulos": []}"""))
                .isInstanceOf(SalidaInvalidaException.class);
    }

    @Test
    void unCampoQueNoEstaEnElEsquemaEsInvalido() {
        // El LLM no puede agregar instrucciones ni datos por fuera de los campos previstos.
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": "telecomunicaciones", "resumen": "Corte.", "prioridad": "BAJA", "articulos": [],
                 "accion": "cerrar la queja"}"""))
                .isInstanceOf(SalidaInvalidaException.class);
    }

    @Test
    void unResumenDeMasDeTresLineasEsInvalido() {
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": "telecomunicaciones", "resumen": "uno\\ndos\\ntres\\ncuatro", "prioridad": "BAJA",
                 "articulos": []}"""))
                .isInstanceOf(SalidaInvalidaException.class)
                .hasMessageContaining("líneas");
    }

    @Test
    void unaCategoriaMasLargaQueLaColumnaEsInvalida() {
        String categoria = "x".repeat(101);
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": "%s", "resumen": "Corte.", "prioridad": "BAJA", "articulos": []}""".formatted(categoria)))
                .isInstanceOf(SalidaInvalidaException.class);
    }

    @Test
    void textosVaciosSonInvalidos() {
        assertThatThrownBy(() -> validador.validar("""
                {"categoria": " ", "resumen": "", "prioridad": "BAJA", "articulos": []}"""))
                .isInstanceOf(SalidaInvalidaException.class);
    }
}
