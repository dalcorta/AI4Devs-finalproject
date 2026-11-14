package ar.libroabierto.anchoring.infrastructure;

import ar.libroabierto.anchoring.application.AnclajeDeEstados;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/**
 * Envía {@code changeStatus} con la clave institucional del funcionario (solo OFFICER_ROLE: si se filtra la del relayer,
 * no alcanza para cambiar estados). Es idempotente: como la máquina de estados no tiene ciclos, cada estado se alcanza
 * una sola vez, así que si ya hay un StatusChanged hacia ese estado, un reintento devuelve esa transacción.
 */
public class Web3jAnclajeDeEstados implements AnclajeDeEstados {

    private final Web3j web3j;
    private final Firmante firmante;
    private final String contrato;

    public Web3jAnclajeDeEstados(Web3j web3j, Credentials funcionario, String contrato, long chainId) {
        this.web3j = web3j;
        this.firmante = new Firmante(web3j, funcionario, chainId);
        this.contrato = contrato;
    }

    @Override
    public String cambiarEstado(long onchainId, EstadoQueja nuevo, String noteHash) {
        try {
            Optional<String> anterior = transaccionQueLlevoA(onchainId, nuevo);
            if (anterior.isPresent()) {
                return anterior.get();
            }
            return firmante.enviar(contrato, FunctionEncoder.encode(new Function("changeStatus", List.of(
                    new Uint256(BigInteger.valueOf(onchainId)),
                    new Uint8(BigInteger.valueOf(nuevo.ordinal())),
                    new Bytes32(Numeric.hexStringToByteArray(noteHash))), List.of())));
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    private Optional<String> transaccionQueLlevoA(long onchainId, EstadoQueja estado) throws IOException {
        EthFilter filtro = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contrato)
                .addSingleTopic(ComplaintRegistryAbi.STATUS_CHANGED_TOPIC)
                .addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(onchainId), 64));
        EthLog logs = web3j.ethGetLogs(filtro).send();
        Firmante.exigirSinError(logs, "Falló la búsqueda del evento StatusChanged");
        return logs.getLogs().stream()
                .map(resultado -> (Log) resultado.get())
                .filter(log -> estadoActual(log) == estado.ordinal())
                .map(Log::getTransactionHash)
                .findFirst();
    }

    private static int estadoActual(Log log) {
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.STATUS_CHANGED, log);
        return ((BigInteger) valores.getNonIndexedValues().get(1).getValue()).intValueExact();
    }
}
