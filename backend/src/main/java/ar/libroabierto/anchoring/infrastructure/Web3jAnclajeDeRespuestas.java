package ar.libroabierto.anchoring.infrastructure;

import ar.libroabierto.anchoring.application.AnclajeDeRespuestas;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/**
 * Relayer de las respuestas: envía {@code fileResponse}. Es idempotente: el contrato admite una sola respuesta por
 * queja, así que si ya hay un ResponseFiled con ese mismo hash, un reintento devuelve esa transacción. Si la queja
 * tiene otra respuesta, falla: nunca se toma como propia la transacción de una respuesta distinta.
 */
public class Web3jAnclajeDeRespuestas implements AnclajeDeRespuestas {

    private final Web3j web3j;
    private final Firmante firmante;
    private final String contrato;

    public Web3jAnclajeDeRespuestas(Web3j web3j, Credentials relayer, String contrato, long chainId) {
        this.web3j = web3j;
        this.firmante = new Firmante(web3j, relayer, chainId);
        this.contrato = contrato;
    }

    @Override
    public String registrarRespuesta(long quejaOnchainId, String contentHash, String ipfsCid) {
        try {
            Optional<Log> anterior = respuestaAnclada(quejaOnchainId);
            if (anterior.isPresent()) {
                String hashAnclado = hashDe(anterior.get());
                if (!hashAnclado.equalsIgnoreCase(contentHash)) {
                    throw new IllegalStateException("La queja " + quejaOnchainId
                            + " ya tiene otra respuesta anclada (hash " + hashAnclado + ")");
                }
                return anterior.get().getTransactionHash();
            }
            return firmante.enviar(contrato, FunctionEncoder.encode(new Function("fileResponse", List.of(
                    new Uint256(BigInteger.valueOf(quejaOnchainId)),
                    new Bytes32(Numeric.hexStringToByteArray(contentHash)),
                    new Utf8String(ipfsCid)), List.of())));
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    private Optional<Log> respuestaAnclada(long quejaOnchainId) throws IOException {
        EthFilter filtro = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contrato)
                .addSingleTopic(ComplaintRegistryAbi.RESPONSE_FILED_TOPIC)
                .addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(quejaOnchainId), 64));
        EthLog logs = web3j.ethGetLogs(filtro).send();
        Firmante.exigirSinError(logs, "Falló la búsqueda del evento ResponseFiled");
        return logs.getLogs().stream().map(resultado -> (Log) resultado.get()).findFirst();
    }

    private static String hashDe(Log log) {
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.RESPONSE_FILED, log);
        return Numeric.toHexString((byte[]) valores.getNonIndexedValues().get(0).getValue());
    }
}
