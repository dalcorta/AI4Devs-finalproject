package ar.libroabierto.support;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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
 * Anvil local con el ComplaintRegistry compilado por Forge. Requiere `forge build` en contracts/;
 * si el binario de Anvil no está instalado, los tests que lo usan se omiten.
 */
public final class AnvilLocal implements AutoCloseable {

    public static final long CHAIN_ID = 31337;

    // Claves de desarrollo públicas que Anvil genera por defecto; no tienen valor fuera de esta cadena local.
    public static final Credentials ADMIN =
            Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    public static final Credentials RELAYER =
            Credentials.create("0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
    public static final Credentials SIN_ROL =
            Credentials.create("0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdd4c4a3");

    private static final Path ANVIL = Path.of(System.getProperty("user.home"), ".foundry", "bin", "anvil");
    private static final Path ARTEFACTO =
            Path.of("..", "contracts", "out", "ComplaintRegistry.sol", "ComplaintRegistry.json");

    private final Process proceso;
    private final String rpcUrl;
    private final Web3j web3j;

    private AnvilLocal(Process proceso, String rpcUrl) {
        this.proceso = proceso;
        this.rpcUrl = rpcUrl;
        this.web3j = Web3j.build(new HttpService(rpcUrl));
    }

    public static AnvilLocal iniciar() throws Exception {
        assumeThat(Files.isExecutable(ANVIL)).as("Anvil no está instalado en " + ANVIL).isTrue();
        assertThat(ARTEFACTO).as("Falta el artefacto del contrato: correr `forge build` en contracts/").exists();

        int puerto;
        try (ServerSocket socket = new ServerSocket(0)) {
            puerto = socket.getLocalPort();
        }
        Process proceso = new ProcessBuilder(ANVIL.toString(), "--port", String.valueOf(puerto), "--silent")
                .redirectErrorStream(true)
                .start();
        AnvilLocal anvil = new AnvilLocal(proceso, "http://127.0.0.1:" + puerto);
        anvil.esperarRpc();
        return anvil;
    }

    public String rpcUrl() {
        return rpcUrl;
    }

    public Web3j web3j() {
        return web3j;
    }

    /** Despliega un ComplaintRegistry nuevo con ADMIN como administrador y RELAYER con RELAYER_ROLE. */
    public String desplegarContrato() throws Exception {
        String bytecode = new ObjectMapper().readTree(ARTEFACTO.toFile()).get("bytecode").get("object").asText();
        String constructor = FunctionEncoder.encodeConstructor(List.of(new Address(ADMIN.getAddress())));
        String contrato = enviar(ADMIN, null, bytecode + constructor).getContractAddress();

        byte[] relayerRole = Hash.sha3("RELAYER_ROLE".getBytes());
        enviar(ADMIN, contrato, FunctionEncoder.encode(new Function(
                "grantRole", List.of(new Bytes32(relayerRole), new Address(RELAYER.getAddress())), List.of())));
        return contrato;
    }

    public BigInteger idPorHash(String contrato, String contentHash) throws IOException {
        return llamarUint(contrato, "complaintIdByHash",
                List.of(new Bytes32(Numeric.hexStringToByteArray(contentHash))));
    }

    public BigInteger totalQuejas(String contrato) throws IOException {
        return llamarUint(contrato, "totalComplaints", List.of());
    }

    public BigInteger nonce(Credentials cuenta) throws IOException {
        return web3j.ethGetTransactionCount(cuenta.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
    }

    public TransactionReceipt esperarRecibo(String txHash) throws Exception {
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

    @Override
    public void close() {
        web3j.shutdown();
        proceso.destroy();
    }

    @SuppressWarnings("rawtypes")
    private BigInteger llamarUint(String contrato, String nombre, List<Type> args) throws IOException {
        Function funcion = new Function(nombre, args, List.of(new TypeReference<Uint256>() { }));
        String resultado = web3j.ethCall(
                Transaction.createEthCallTransaction(ADMIN.getAddress(), contrato, FunctionEncoder.encode(funcion)),
                DefaultBlockParameterName.LATEST).send().getValue();
        return (BigInteger) FunctionReturnDecoder.decode(resultado, funcion.getOutputParameters()).get(0).getValue();
    }

    private TransactionReceipt enviar(Credentials cuenta, String destino, String datos) throws Exception {
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

    private void esperarRpc() throws InterruptedException {
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
