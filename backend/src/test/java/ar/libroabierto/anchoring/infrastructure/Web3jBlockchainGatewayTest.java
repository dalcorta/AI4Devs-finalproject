package ar.libroabierto.anchoring.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

/**
 * Integración contra un Anvil real con el ComplaintRegistry compilado por Forge.
 * Requiere `forge build` en contracts/ y el binario de Anvil; si Anvil no está instalado, se omite.
 */
class Web3jBlockchainGatewayTest {

    private static final Path ANVIL = Path.of(System.getProperty("user.home"), ".foundry", "bin", "anvil");
    private static final Path ARTEFACTO = Path.of("..", "contracts", "out", "ComplaintRegistry.sol", "ComplaintRegistry.json");
    private static final long CHAIN_ID = 31337;

    // Claves de desarrollo públicas que Anvil genera por defecto; no tienen valor fuera de esta cadena local.
    private static final Credentials ADMIN =
            Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final Credentials RELAYER =
            Credentials.create("0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
    private static final Credentials SIN_ROL =
            Credentials.create("0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdd4c4a3");

    private static Process anvil;
    private static Web3j web3j;

    private String contrato;

    @BeforeAll
    static void levantarAnvil() throws Exception {
        assumeThat(Files.isExecutable(ANVIL)).as("Anvil no está instalado en " + ANVIL).isTrue();
        assertThat(ARTEFACTO).as("Falta el artefacto del contrato: correr `forge build` en contracts/").exists();

        int puerto;
        try (ServerSocket socket = new ServerSocket(0)) {
            puerto = socket.getLocalPort();
        }
        anvil = new ProcessBuilder(ANVIL.toString(), "--port", String.valueOf(puerto), "--silent")
                .redirectErrorStream(true)
                .start();
        web3j = Web3j.build(new HttpService("http://127.0.0.1:" + puerto));
        esperarRpc();
    }

    @AfterAll
    static void apagarAnvil() {
        if (web3j != null) {
            web3j.shutdown();
        }
        if (anvil != null) {
            anvil.destroy();
        }
    }

    @BeforeEach
    void desplegarContrato() throws Exception {
        String bytecode = new ObjectMapper().readTree(ARTEFACTO.toFile()).get("bytecode").get("object").asText();
        String constructor = FunctionEncoder.encodeConstructor(List.of(new Address(ADMIN.getAddress())));
        TransactionReceipt despliegue = enviar(ADMIN, null, bytecode + constructor);
        contrato = despliegue.getContractAddress();

        byte[] relayerRole = Hash.sha3("RELAYER_ROLE".getBytes());
        enviar(ADMIN, contrato, FunctionEncoder.encode(new Function(
                "grantRole", List.of(new Bytes32(relayerRole), new Address(RELAYER.getAddress())), List.of())));
    }

    @Test
    void registraLaQuejaOnChain() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(web3j, RELAYER, contrato, CHAIN_ID);
        String hash = hashDePrueba("queja-1");

        String txHash = gateway.registrarQueja(30712345678L, hash, "bafy-1");

        TransactionReceipt recibo = esperarRecibo(txHash);
        assertThat(recibo.isStatusOK()).isTrue();
        assertThat(idPorHash(hash)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void reenviarElMismoHashDevuelveLaTransaccionOriginalSinDuplicar() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(web3j, RELAYER, contrato, CHAIN_ID);
        String hash = hashDePrueba("queja-reintentada");
        String original = gateway.registrarQueja(30712345678L, hash, "bafy-1");
        esperarRecibo(original);

        String reintento = gateway.registrarQueja(30712345678L, hash, "bafy-1");

        assertThat(reintento).isEqualTo(original);
        assertThat(totalQuejas()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void sinRolDeRelayerFallaSinEnviarLaTransaccion() throws Exception {
        Web3jBlockchainGateway gateway = new Web3jBlockchainGateway(web3j, SIN_ROL, contrato, CHAIN_ID);
        BigInteger nonceAntes = nonce(SIN_ROL);

        assertThatThrownBy(() -> gateway.registrarQueja(30712345678L, hashDePrueba("queja-sin-rol"), "bafy-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(nonce(SIN_ROL)).isEqualTo(nonceAntes);
    }

    private static String hashDePrueba(String semilla) {
        return Numeric.toHexString(Hash.sha3(semilla.getBytes()));
    }

    private BigInteger idPorHash(String hash) throws IOException {
        return llamarUint("complaintIdByHash", List.of(new Bytes32(Numeric.hexStringToByteArray(hash))));
    }

    private BigInteger totalQuejas() throws IOException {
        return llamarUint("totalComplaints", List.of());
    }

    @SuppressWarnings("rawtypes")
    private BigInteger llamarUint(String nombre, List<org.web3j.abi.datatypes.Type> args) throws IOException {
        Function funcion = new Function(nombre, args, List.of(new TypeReference<Uint256>() { }));
        String resultado = web3j.ethCall(
                Transaction.createEthCallTransaction(ADMIN.getAddress(), contrato, FunctionEncoder.encode(funcion)),
                DefaultBlockParameterName.LATEST).send().getValue();
        return (BigInteger) FunctionReturnDecoder.decode(resultado, funcion.getOutputParameters()).get(0).getValue();
    }

    private static BigInteger nonce(Credentials cuenta) throws IOException {
        return web3j.ethGetTransactionCount(cuenta.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
    }

    private static TransactionReceipt enviar(Credentials cuenta, String destino, String datos) throws Exception {
        RawTransactionManager tx = new RawTransactionManager(web3j, cuenta, CHAIN_ID);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        EthSendTransaction envio = destino == null
                ? tx.sendTransaction(gasPrice, BigInteger.valueOf(5_000_000), null, datos, BigInteger.ZERO, true)
                : tx.sendTransaction(gasPrice, BigInteger.valueOf(500_000), destino, datos, BigInteger.ZERO);
        assertThat(envio.hasError()).as(envio.hasError() ? envio.getError().getMessage() : "").isFalse();
        TransactionReceipt recibo = esperarRecibo(envio.getTransactionHash());
        assertThat(recibo.isStatusOK()).isTrue();
        return recibo;
    }

    private static TransactionReceipt esperarRecibo(String txHash) throws Exception {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(limite)) {
            var recibo = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (recibo.isPresent()) {
                return recibo.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No llegó el recibo de " + txHash);
    }

    private static void esperarRpc() throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(limite)) {
            try {
                web3j.ethChainId().send();
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new AssertionError("Anvil no respondió a tiempo");
    }
}
