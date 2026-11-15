package ar.libroabierto.listener.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.anchoring.infrastructure.Web3jBlockchainGateway;
import ar.libroabierto.complaint.domain.EstadoQueja;
import ar.libroabierto.listener.application.EventoEstadoCambiado;
import ar.libroabierto.listener.application.EventoQuejaRegistrada;
import ar.libroabierto.listener.application.EventoRespuestaRegistrada;
import ar.libroabierto.support.AnvilLocal;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** Lee eventos reales emitidos por el contrato desplegado en Anvil. */
class Web3jFuenteDeEventosTest {

    private static final String HASH_1 = "0x" + "1".repeat(64);
    private static final String HASH_2 = "0x" + "2".repeat(64);

    private static AnvilLocal anvil;
    private static Web3jFuenteDeEventos fuente;
    private static String contrato;
    private static TransactionReceipt primero;
    private static TransactionReceipt segundo;

    @BeforeAll
    static void anclarDosQuejas() throws Exception {
        anvil = AnvilLocal.iniciar();
        contrato = anvil.desplegarContrato();
        var relayer = new Web3jBlockchainGateway(anvil.web3j(), AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);
        primero = anvil.esperarRecibo(relayer.registrarQueja(30712345678L, HASH_1, "bafy-uno"));
        segundo = anvil.esperarRecibo(relayer.registrarQueja(30500010912L, HASH_2, "bafy-dos"));
        fuente = new Web3jFuenteDeEventos(anvil.web3j(), contrato);
    }

    @AfterAll
    static void apagar() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @Test
    void decodificaTodosLosCamposDelEvento() {
        List<EventoQuejaRegistrada> eventos = fuente.quejasRegistradas(0, fuente.bloqueActual());

        assertThat(eventos).containsExactly(
                new EventoQuejaRegistrada(1, 30712345678L, HASH_1, "bafy-uno",
                        primero.getBlockNumber().longValueExact(), primero.getTransactionHash()),
                new EventoQuejaRegistrada(2, 30500010912L, HASH_2, "bafy-dos",
                        segundo.getBlockNumber().longValueExact(), segundo.getTransactionHash()));
    }

    @Test
    void respetaElRangoDeBloquesPedido() {
        long bloqueDelSegundo = segundo.getBlockNumber().longValueExact();

        assertThat(fuente.quejasRegistradas(bloqueDelSegundo, bloqueDelSegundo))
                .extracting(EventoQuejaRegistrada::onchainId).containsExactly(2L);
        assertThat(fuente.quejasRegistradas(0, primero.getBlockNumber().longValueExact() - 1)).isEmpty();
    }

    @Test
    void elBloqueActualEsElUltimoMinado() throws Exception {
        assertThat(fuente.bloqueActual())
                .isEqualTo(anvil.web3j().ethBlockNumber().send().getBlockNumber().longValueExact())
                .isGreaterThanOrEqualTo(segundo.getBlockNumber().longValueExact());
    }

    @Test
    void decodificaLosCambiosDeEstado() throws Exception {
        var funcionario = new ar.libroabierto.anchoring.infrastructure.Web3jAnclajeDeEstados(anvil.web3j(),
                AnvilLocal.FUNCIONARIO, contrato, AnvilLocal.CHAIN_ID);
        var recibo = anvil.esperarRecibo(
                funcionario.cambiarEstado(1, EstadoQueja.EN_REVISION, "0x" + "0".repeat(64)));

        assertThat(fuente.estadosCambiados(0, fuente.bloqueActual())).containsExactly(new EventoEstadoCambiado(1,
                EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, recibo.getBlockNumber().longValueExact(),
                recibo.getTransactionHash()));
    }

    @Test
    void decodificaLasRespuestasDeLasEmpresas() throws Exception {
        var relayer = new ar.libroabierto.anchoring.infrastructure.Web3jAnclajeDeRespuestas(anvil.web3j(),
                AnvilLocal.RELAYER, contrato, AnvilLocal.CHAIN_ID);
        String hash = "0x" + "3".repeat(64);
        var recibo = anvil.esperarRecibo(relayer.registrarRespuesta(2, hash, "bafy-respuesta"));

        assertThat(fuente.respuestasRegistradas(0, fuente.bloqueActual())).containsExactly(
                new EventoRespuestaRegistrada(2, hash, "bafy-respuesta", recibo.getBlockNumber().longValueExact(),
                        recibo.getTransactionHash()));
    }

    @Test
    void ignoraEventosDeOtroContrato() throws Exception {
        String otro = anvil.desplegarContrato();

        assertThat(new Web3jFuenteDeEventos(anvil.web3j(), otro).quejasRegistradas(0, fuente.bloqueActual()))
                .isEmpty();
    }
}
