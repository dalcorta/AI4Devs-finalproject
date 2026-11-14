package ar.libroabierto.anchoring.infrastructure;

import java.io.IOException;
import java.math.BigInteger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;

/** Firma y envía transacciones al contrato con una cuenta. Lo comparten el relayer y el funcionario. */
class Firmante {

    private final Web3j web3j;
    private final Credentials cuenta;
    private final RawTransactionManager transacciones;

    Firmante(Web3j web3j, Credentials cuenta, long chainId) {
        this.web3j = web3j;
        this.cuenta = cuenta;
        this.transacciones = new RawTransactionManager(web3j, cuenta, chainId);
    }

    String direccion() {
        return cuenta.getAddress();
    }

    /** Estimar primero hace que un revert (rol faltante, transición inválida) falle acá, sin gastar gas ni nonce. */
    String enviar(String contrato, String datos) throws IOException {
        var estimacion = web3j.ethEstimateGas(Transaction.createFunctionCallTransaction(
                cuenta.getAddress(), null, null, null, contrato, datos)).send();
        exigirSinError(estimacion, "La transacción revertiría");
        BigInteger gasLimit = estimacion.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction envio = transacciones.sendTransaction(gasPrice, gasLimit, contrato, datos, BigInteger.ZERO);
        exigirSinError(envio, "El nodo rechazó la transacción");
        return envio.getTransactionHash();
    }

    static void exigirSinError(Response<?> respuesta, String contexto) {
        if (respuesta.hasError()) {
            throw new IllegalStateException(contexto + ": " + respuesta.getError().getMessage());
        }
    }
}
