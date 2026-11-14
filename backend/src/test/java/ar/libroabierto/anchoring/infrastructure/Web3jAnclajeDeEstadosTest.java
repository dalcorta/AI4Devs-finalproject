package ar.libroabierto.anchoring.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.complaint.domain.EstadoQueja;
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

/** changeStatus real contra un ComplaintRegistry en Anvil, firmado con la clave del funcionario. */
class Web3jAnclajeDeEstadosTest {

    private static final String NOTA = "0x38cfa891fd711ef6d384dbb224906d8918bc731a3d01c36422010e5c838fd019";

    private static AnvilLocal anvil;
    private static String contrato;
    private static Web3jAnclajeDeEstados funcionario;

    @BeforeAll
    static void quejaAnclada() throws Exception {
        anvil = AnvilLocal.iniciar();
        contrato = anvil.desplegarContrato();
        var relayer = new Web3jBlockchainGateway(anvil.web3j(), AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);
        anvil.esperarRecibo(relayer.registrarQueja(30712345671L, "0x" + "1".repeat(64), "bafy-uno"));
        anvil.esperarRecibo(relayer.registrarQueja(30712345671L, "0x" + "2".repeat(64), "bafy-dos"));
        funcionario = new Web3jAnclajeDeEstados(anvil.web3j(), AnvilLocal.FUNCIONARIO, contrato, AnvilLocal.CHAIN_ID);
    }

    @AfterAll
    static void apagar() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @Test
    void cambiaElEstadoYEmiteStatusChangedConElHashDelComentario() throws Exception {
        String tx = funcionario.cambiarEstado(1, EstadoQueja.EN_REVISION, NOTA);

        assertThat(anvil.esperarRecibo(tx).isStatusOK()).isTrue();
        List<Log> eventos = statusChanged(1);
        assertThat(eventos).hasSize(1);
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.STATUS_CHANGED, eventos.get(0));
        assertThat(valores.getNonIndexedValues().get(0).getValue()).isEqualTo(BigInteger.ZERO);
        assertThat(valores.getNonIndexedValues().get(1).getValue()).isEqualTo(BigInteger.ONE);
        assertThat(org.web3j.utils.Numeric.toHexString((byte[]) valores.getNonIndexedValues().get(2).getValue()))
                .isEqualTo(NOTA);
    }

    @Test
    void reintentarUnCambioYaAncladoDevuelveLaTransaccionOriginalSinMandarOtra() throws Exception {
        String original = funcionario.cambiarEstado(2, EstadoQueja.EN_REVISION, NOTA);
        anvil.esperarRecibo(original);
        BigInteger nonce = anvil.nonce(AnvilLocal.FUNCIONARIO);

        String reintento = funcionario.cambiarEstado(2, EstadoQueja.EN_REVISION, NOTA);

        assertThat(reintento).isEqualTo(original);
        assertThat(anvil.nonce(AnvilLocal.FUNCIONARIO)).isEqualTo(nonce);
        assertThat(statusChanged(2)).hasSize(1);
    }

    @Test
    void unaTransicionInvalidaFallaSinGastarNonce() throws Exception {
        BigInteger nonce = anvil.nonce(AnvilLocal.FUNCIONARIO);

        assertThatThrownBy(() -> funcionario.cambiarEstado(1, EstadoQueja.RESUELTA, NOTA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(anvil.nonce(AnvilLocal.FUNCIONARIO)).isEqualTo(nonce);
    }

    @Test
    void laClaveDelRelayerNoPuedeCambiarEstados() {
        var relayer = new Web3jAnclajeDeEstados(anvil.web3j(), AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);

        assertThatThrownBy(() -> relayer.cambiarEstado(1, EstadoQueja.DERIVADA, NOTA))
                .isInstanceOf(IllegalStateException.class);
    }

    private static List<Log> statusChanged(long id) throws Exception {
        EthFilter filtro = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contrato)
                .addSingleTopic(ComplaintRegistryAbi.STATUS_CHANGED_TOPIC)
                .addSingleTopic(org.web3j.utils.Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(id), 64));
        return anvil.web3j().ethGetLogs(filtro).send().getLogs().stream().map(r -> (Log) r.get()).toList();
    }
}
