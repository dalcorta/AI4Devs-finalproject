package ar.libroabierto.listener.infrastructure;

import ar.libroabierto.anchoring.infrastructure.ComplaintRegistryAbi;
import ar.libroabierto.complaint.domain.EstadoQueja;
import ar.libroabierto.listener.application.EventoEstadoCambiado;
import ar.libroabierto.listener.application.EventoQuejaRegistrada;
import ar.libroabierto.listener.application.EventoRespuestaRegistrada;
import ar.libroabierto.listener.application.FuenteDeEventos;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/**
 * Lee los eventos del contrato con {@code eth_getLogs} sobre rangos de bloques acotados. Se usa polling en lugar de
 * una suscripción por WebSocket: funciona con cualquier RPC HTTP y, junto con el checkpoint, no pierde eventos si
 * la conexión se corta.
 */
public class Web3jFuenteDeEventos implements FuenteDeEventos {

    private final Web3j web3j;
    private final String contrato;

    public Web3jFuenteDeEventos(Web3j web3j, String contrato) {
        this.web3j = web3j;
        this.contrato = contrato;
    }

    @Override
    public long bloqueActual() {
        try {
            var respuesta = web3j.ethBlockNumber().send();
            exigirSinError(respuesta, "Falló eth_blockNumber");
            return respuesta.getBlockNumber().longValueExact();
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    @Override
    public List<EventoQuejaRegistrada> quejasRegistradas(long desde, long hasta) {
        EthFilter filtro = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(desde)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(hasta)),
                contrato)
                .addSingleTopic(ComplaintRegistryAbi.COMPLAINT_FILED_TOPIC);
        try {
            EthLog logs = web3j.ethGetLogs(filtro).send();
            exigirSinError(logs, "Falló la lectura de eventos ComplaintFiled");
            return logs.getLogs().stream().map(resultado -> decodificar((Log) resultado.get())).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    @Override
    public List<EventoEstadoCambiado> estadosCambiados(long desde, long hasta) {
        EthFilter filtro = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(desde)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(hasta)),
                contrato)
                .addSingleTopic(ComplaintRegistryAbi.STATUS_CHANGED_TOPIC);
        try {
            EthLog logs = web3j.ethGetLogs(filtro).send();
            exigirSinError(logs, "Falló la lectura de eventos StatusChanged");
            return logs.getLogs().stream().map(resultado -> decodificarCambio((Log) resultado.get())).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    @Override
    public List<EventoRespuestaRegistrada> respuestasRegistradas(long desde, long hasta) {
        EthFilter filtro = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(desde)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(hasta)),
                contrato)
                .addSingleTopic(ComplaintRegistryAbi.RESPONSE_FILED_TOPIC);
        try {
            EthLog logs = web3j.ethGetLogs(filtro).send();
            exigirSinError(logs, "Falló la lectura de eventos ResponseFiled");
            return logs.getLogs().stream().map(resultado -> decodificarRespuesta((Log) resultado.get())).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Error de comunicación con el nodo", e);
        }
    }

    @SuppressWarnings("rawtypes")
    private static EventoRespuestaRegistrada decodificarRespuesta(Log log) {
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.RESPONSE_FILED, log);
        List<Type> datos = valores.getNonIndexedValues();
        return new EventoRespuestaRegistrada(
                ((BigInteger) valores.getIndexedValues().get(0).getValue()).longValueExact(),
                Numeric.toHexString((byte[]) datos.get(0).getValue()),
                (String) datos.get(1).getValue(),
                log.getBlockNumber().longValueExact(),
                log.getTransactionHash());
    }

    @SuppressWarnings("rawtypes")
    private static EventoEstadoCambiado decodificarCambio(Log log) {
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.STATUS_CHANGED, log);
        List<Type> datos = valores.getNonIndexedValues();
        return new EventoEstadoCambiado(
                ((BigInteger) valores.getIndexedValues().get(0).getValue()).longValueExact(),
                EstadoQueja.values()[((BigInteger) datos.get(0).getValue()).intValueExact()],
                EstadoQueja.values()[((BigInteger) datos.get(1).getValue()).intValueExact()],
                log.getBlockNumber().longValueExact(),
                log.getTransactionHash());
    }

    @SuppressWarnings("rawtypes")
    private static EventoQuejaRegistrada decodificar(Log log) {
        var valores = Contract.staticExtractEventParameters(ComplaintRegistryAbi.COMPLAINT_FILED, log);
        List<Type> indexados = valores.getIndexedValues();
        List<Type> datos = valores.getNonIndexedValues();
        return new EventoQuejaRegistrada(
                ((BigInteger) indexados.get(0).getValue()).longValueExact(),
                ((BigInteger) indexados.get(1).getValue()).longValueExact(),
                Numeric.toHexString((byte[]) datos.get(0).getValue()),
                (String) datos.get(1).getValue(),
                log.getBlockNumber().longValueExact(),
                log.getTransactionHash());
    }

    private static void exigirSinError(Response<?> respuesta, String contexto) {
        if (respuesta.hasError()) {
            throw new IllegalStateException(contexto + ": " + respuesta.getError().getMessage());
        }
    }
}
