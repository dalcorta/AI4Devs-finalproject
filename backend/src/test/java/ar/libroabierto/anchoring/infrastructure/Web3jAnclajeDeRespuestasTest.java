package ar.libroabierto.anchoring.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.support.AnvilLocal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/** fileResponse real contra un ComplaintRegistry en Anvil, firmado por el relayer en nombre de la empresa. */
class Web3jAnclajeDeRespuestasTest {

    private static final String HASH_A = "0x" + "a".repeat(64);
    private static final String HASH_B = "0x" + "b".repeat(64);

    private static AnvilLocal anvil;
    private static String contrato;
    private static Web3jAnclajeDeRespuestas relayer;

    @BeforeAll
    static void quejasAncladas() throws Exception {
        anvil = AnvilLocal.iniciar();
        contrato = anvil.desplegarContrato();
        var quejas = new Web3jBlockchainGateway(anvil.web3j(), AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);
        for (int i = 1; i <= 3; i++) {
            anvil.esperarRecibo(quejas.registrarQueja(30712345671L, "0x" + String.valueOf(i).repeat(64), "bafy-" + i));
        }
        relayer = new Web3jAnclajeDeRespuestas(anvil.web3j(), AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);
    }

    @AfterAll
    static void apagar() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @Test
    void anclaLaRespuestaYEmiteResponseFiled() throws Exception {
        String tx = relayer.registrarRespuesta(1, HASH_A, "bafy-respuesta-1");

        assertThat(anvil.esperarRecibo(tx).isStatusOK()).isTrue();
        List<Log> eventos = responseFiled(1);
        assertThat(eventos).hasSize(1);
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.RESPONSE_FILED, eventos.get(0));
        assertThat(Numeric.toHexString((byte[]) valores.getNonIndexedValues().get(0).getValue())).isEqualTo(HASH_A);
        assertThat(valores.getNonIndexedValues().get(1).getValue()).isEqualTo("bafy-respuesta-1");
    }

    @Test
    void reintentarUnaRespuestaYaAncladaDevuelveLaTransaccionOriginalSinMandarOtra() throws Exception {
        String original = relayer.registrarRespuesta(2, HASH_B, "bafy-respuesta-2");
        anvil.esperarRecibo(original);
        BigInteger nonce = anvil.nonce(AnvilLocal.RELAYER);

        assertThat(relayer.registrarRespuesta(2, HASH_B, "bafy-respuesta-2")).isEqualTo(original);
        assertThat(anvil.nonce(AnvilLocal.RELAYER)).isEqualTo(nonce);
        assertThat(responseFiled(2)).hasSize(1);
    }

    @Test
    void siLaQuejaYaTieneOtraRespuestaFallaEnLugarDeDevolverLaAjena() throws Exception {
        anvil.esperarRecibo(relayer.registrarRespuesta(3, "0x" + "c".repeat(64), "bafy-respuesta-3"));

        assertThatThrownBy(() -> relayer.registrarRespuesta(3, "0x" + "d".repeat(64), "bafy-otra"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaQuejaInexistenteFallaSinGastarNonce() throws Exception {
        BigInteger nonce = anvil.nonce(AnvilLocal.RELAYER);

        assertThatThrownBy(() -> relayer.registrarRespuesta(99, HASH_A, "bafy"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(anvil.nonce(AnvilLocal.RELAYER)).isEqualTo(nonce);
    }

    @Test
    void laClaveDelFuncionarioNoPuedeAnclarRespuestas() {
        var funcionario = new Web3jAnclajeDeRespuestas(anvil.web3j(), AnvilLocal.FUNCIONARIO, contrato,
                AnvilLocal.CHAIN_ID);

        assertThatThrownBy(() -> funcionario.registrarRespuesta(1, HASH_B, "bafy"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static List<Log> responseFiled(long id) throws Exception {
        EthFilter filtro = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contrato)
                .addSingleTopic(ComplaintRegistryAbi.RESPONSE_FILED_TOPIC)
                .addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(id), 64));
        return anvil.web3j().ethGetLogs(filtro).send().getLogs().stream().map(r -> (Log) r.get()).toList();
    }
}
