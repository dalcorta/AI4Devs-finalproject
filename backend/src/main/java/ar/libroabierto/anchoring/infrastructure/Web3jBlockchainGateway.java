package ar.libroabierto.anchoring.infrastructure;

import ar.libroabierto.anchoring.application.BlockchainGateway;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

/**
 * Relayer: firma y envía {@code fileComplaint}. Es idempotente por contentHash, así un reintento del worker
 * después de un envío exitoso devuelve la transacción original en lugar de anclar la queja dos veces.
 */
public class Web3jBlockchainGateway implements BlockchainGateway {

    private final Web3j web3j;
    private final Firmante firmante;
    private final String contrato;

    public Web3jBlockchainGateway(Web3j web3j, Credentials relayer, String contrato, long chainId) {
        this.web3j = web3j;
        this.firmante = new Firmante(web3j, relayer, chainId);
        this.contrato = contrato;
    }

    @Override
    public String registrarQueja(long companyCuit, String contentHash, String ipfsCid) {
        try {
            BigInteger existente = idPorHash(contentHash);
            if (existente.signum() > 0) {
                return transaccionDeRegistro(existente);
            }
            return enviarFileComplaint(companyCuit, contentHash, ipfsCid);
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    private String enviarFileComplaint(long companyCuit, String contentHash, String ipfsCid) throws IOException {
        return firmante.enviar(contrato, FunctionEncoder.encode(new Function("fileComplaint", List.of(
                new Uint64(BigInteger.valueOf(companyCuit)),
                new Bytes32(Numeric.hexStringToByteArray(contentHash)),
                new Utf8String(ipfsCid)), List.of())));
    }

    private BigInteger idPorHash(String contentHash) throws IOException {
        Function funcion = new Function("complaintIdByHash",
                List.of(new Bytes32(Numeric.hexStringToByteArray(contentHash))),
                List.of(new TypeReference<Uint256>() { }));
        var respuesta = web3j.ethCall(
                Transaction.createEthCallTransaction(firmante.direccion(), contrato, FunctionEncoder.encode(funcion)),
                DefaultBlockParameterName.LATEST).send();
        Firmante.exigirSinError(respuesta, "Falló la consulta complaintIdByHash");
        return (BigInteger) FunctionReturnDecoder.decode(respuesta.getValue(), funcion.getOutputParameters())
                .get(0).getValue();
    }

    private String transaccionDeRegistro(BigInteger id) throws IOException {
        EthFilter filtro = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contrato)
                .addSingleTopic(ComplaintRegistryAbi.COMPLAINT_FILED_TOPIC)
                .addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(id, 64));
        EthLog logs = web3j.ethGetLogs(filtro).send();
        Firmante.exigirSinError(logs, "Falló la búsqueda del evento ComplaintFiled");
        return logs.getLogs().stream()
                .map(resultado -> ((Log) resultado.get()).getTransactionHash())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La queja " + id + " existe pero no se encontró su evento"));
    }
}
