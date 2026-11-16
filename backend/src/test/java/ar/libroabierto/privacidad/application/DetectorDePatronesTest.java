package ar.libroabierto.privacidad.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.privacidad.domain.Hallazgo;
import ar.libroabierto.privacidad.domain.TipoDatoPersonal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DetectorDePatronesTest {

    private final DetectorDePatrones detector = new DetectorDePatrones();

    @Test
    void encuentraUnEmailConSuPosicionExacta() {
        String texto = "Les escribí a reclamos.juan+1@correo.example.com y nadie contestó.";

        assertThat(detector.detectar(texto)).containsExactly(
                hallazgo(texto, TipoDatoPersonal.EMAIL, "reclamos.juan+1@correo.example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"30.123.456", "30123456", "5.123.456", "5123456"})
    void encuentraUnDniConOSinPuntos(String dni) {
        String texto = "Mi DNI es " + dni + ", lo di en el local.";

        assertThat(detector.detectar(texto)).containsExactly(hallazgo(texto, TipoDatoPersonal.DNI, dni));
    }

    @ParameterizedTest
    @ValueSource(strings = {"$ 12.500.000", "$12500000", "12.500.000 pesos", "ARS 1.500.000"})
    void unMontoNoEsUnDni(String monto) {
        assertThat(detector.detectar("Me cobraron " + monto + " de más.")).isEmpty();
    }

    @Test
    void numerosCortosFechasYAniosNoSonDatosPersonales() {
        assertThat(detector.detectar("El 15/10/2026 pagué 15000 en 3 cuotas; el reclamo 4521 sigue abierto desde 2025."))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20-30123456-3", "20301234563", "27-12345678-0"})
    void encuentraUnCuilDePersona(String cuil) {
        String texto = "Mi CUIL " + cuil + " figura en la factura.";

        assertThat(detector.detectar(texto)).containsExactly(hallazgo(texto, TipoDatoPersonal.CUIL, cuil));
    }

    @Test
    void unCuilMalTipeadoIgualSeAvisaPorqueLlevaElDni() {
        // 20-30123456-1 tiene mal el dígito verificador, pero el DNI 30123456 está adentro igual.
        String texto = "Mi CUIL es 20-30123456-1.";

        assertThat(detector.detectar(texto)).containsExactly(hallazgo(texto, TipoDatoPersonal.CUIL, "20-30123456-1"));
    }

    @Test
    void elCuitDeUnaEmpresaNoEsUnDatoPersonal() {
        assertThat(detector.detectar("La empresa, CUIT 30-71234567-1, no respondió.")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+54 9 11 4567-8901", "+5491145678901", "011 4567-8901", "(011) 4567-8901",
            "11 4567-8901", "15-4567-8901", "4567-8901", "1145678901", "0351 456-7890"})
    void encuentraTelefonosEnLosFormatosHabituales(String telefono) {
        String texto = "Llamame al " + telefono + " por favor.";

        assertThat(detector.detectar(texto)).containsExactly(hallazgo(texto, TipoDatoPersonal.TELEFONO, telefono));
    }

    @Test
    void encuentraUnaTarjetaSoloSiPasaElControlDeLuhn() {
        String valida = "Pagué con la tarjeta 4111 1111 1111 1111 y me la debitaron dos veces.";
        String invalida = "El número de pedido es 4111 1111 1111 1112.";

        assertThat(detector.detectar(valida)).containsExactly(
                hallazgo(valida, TipoDatoPersonal.TARJETA, "4111 1111 1111 1111"));
        assertThat(detector.detectar(invalida)).extracting(Hallazgo::tipo).doesNotContain(TipoDatoPersonal.TARJETA);
    }

    @Test
    void encuentraUnCbu() {
        String texto = "Transferí desde el CBU 0110599520000001234567 y no acreditaron.";

        assertThat(detector.detectar(texto)).containsExactly(
                hallazgo(texto, TipoDatoPersonal.CBU, "0110599520000001234567"));
    }

    @Test
    void devuelveVariosHallazgosOrdenadosYSinSuperponerse() {
        String texto = "Soy DNI 30.123.456, CUIL 20-30123456-3, mail yo@example.com, cel 11 4567-8901.";

        List<Hallazgo> hallazgos = detector.detectar(texto);

        assertThat(hallazgos).extracting(Hallazgo::tipo).containsExactly(
                TipoDatoPersonal.DNI, TipoDatoPersonal.CUIL, TipoDatoPersonal.EMAIL, TipoDatoPersonal.TELEFONO);
        assertThat(hallazgos).extracting(Hallazgo::fragmento)
                .containsExactly("30.123.456", "20-30123456-3", "yo@example.com", "11 4567-8901");
    }

    @Test
    void unTextoSinDatosPersonalesNoTieneHallazgos() {
        assertThat(detector.detectar("Me cortaron el servicio de internet durante cinco días sin aviso.")).isEmpty();
    }

    private static Hallazgo hallazgo(String texto, TipoDatoPersonal tipo, String fragmento) {
        int inicio = texto.indexOf(fragmento);
        return new Hallazgo(tipo, fragmento, inicio, inicio + fragmento.length());
    }
}
