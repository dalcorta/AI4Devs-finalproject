package ar.libroabierto.anchoring.infrastructure;

import static ar.libroabierto.support.AnvilLocal.CHAIN_ID;
import static ar.libroabierto.support.AnvilLocal.RELAYER;
import static ar.libroabierto.support.AnvilLocal.SIN_ROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.support.AnvilLocal;
import java.math.BigInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

/** Integración contra un Anvil real con el ComplaintRegistry compilado por Forge. */
class Web3jBlockchainGatewayTest {

    private static AnvilLocal anvil;

    private String contrato;

    @BeforeAll
    static void levantarAnvil() throws Exception {
        anvil = AnvilLocal.iniciar();
    }

    @AfterAll
    static void apagarAnvil() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @BeforeEach
    void desplegarContrato() throws Exception {
        contrato = anvil.desplegarContrato();
    }

    @Test
    void registraLaQuejaOnChain() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(anvil.web3j(), RELAYER, contrato, CHAIN_ID);
        String hash = hashDePrueba("queja-1");

        String txHash = gateway.registrarQueja(30712345678L, hash, "bafy-1");

        TransactionReceipt recibo = anvil.esperarRecibo(txHash);
        assertThat(recibo.isStatusOK()).isTrue();
        assertThat(anvil.idPorHash(contrato, hash)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void reenviarElMismoHashDevuelveLaTransaccionOriginalSinDuplicar() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(anvil.web3j(), RELAYER, contrato, CHAIN_ID);
        String hash = hashDePrueba("queja-reintentada");
        String original = gateway.registrarQueja(30712345678L, hash, "bafy-1");
        anvil.esperarRecibo(original);

        String reintento = gateway.registrarQueja(30712345678L, hash, "bafy-1");

        assertThat(reintento).isEqualTo(original);
        assertThat(anvil.totalQuejas(contrato)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void sinRolDeRelayerFallaSinEnviarLaTransaccion() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(anvil.web3j(), SIN_ROL, contrato, CHAIN_ID);
        BigInteger nonceAntes = anvil.nonce(SIN_ROL);

        assertThatThrownBy(() -> gateway.registrarQueja(30712345678L, hashDePrueba("queja-sin-rol"), "bafy-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(anvil.nonce(SIN_ROL)).isEqualTo(nonceAntes);
    }

    private static String hashDePrueba(String semilla) {
        return Numeric.toHexString(Hash.sha3(semilla.getBytes()));
    }
}
