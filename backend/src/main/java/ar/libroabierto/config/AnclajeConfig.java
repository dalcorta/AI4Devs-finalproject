package ar.libroabierto.config;

import ar.libroabierto.anchoring.application.AnclajeDeEstados;
import ar.libroabierto.anchoring.application.BlockchainGateway;
import ar.libroabierto.anchoring.infrastructure.Web3jAnclajeDeEstados;
import ar.libroabierto.anchoring.infrastructure.Web3jBlockchainGateway;
import ar.libroabierto.complaint.application.AnclarCambiosDeEstado;
import ar.libroabierto.complaint.application.CambiosDeEstado;
import ar.libroabierto.complaint.application.IpfsGateway;
import ar.libroabierto.complaint.application.PublicarQuejasPendientes;
import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.infrastructure.KuboIpfsGateway;
import ar.libroabierto.listener.application.CheckpointRepository;
import ar.libroabierto.listener.application.ConfirmacionDeCambios;
import ar.libroabierto.listener.application.FuenteDeEventos;
import ar.libroabierto.listener.application.ProcesarEventosOnChain;
import ar.libroabierto.listener.infrastructure.Web3jFuenteDeEventos;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LibroAbiertoProperties.class)
public class AnclajeConfig {

    @Bean(destroyMethod = "shutdown")
    Web3j web3j(LibroAbiertoProperties props) {
        return Web3j.build(new HttpService(props.blockchain().rpcUrl()));
    }

    @Bean
    BlockchainGateway blockchainGateway(Web3j web3j, LibroAbiertoProperties props) {
        LibroAbiertoProperties.Blockchain cadena = props.blockchain();
        return new Web3jBlockchainGateway(
                web3j, Credentials.create(cadena.relayerPrivateKey()), cadena.contrato(), cadena.chainId());
    }

    @Bean
    AnclajeDeEstados anclajeDeEstados(Web3j web3j, LibroAbiertoProperties props) {
        LibroAbiertoProperties.Blockchain cadena = props.blockchain();
        return new Web3jAnclajeDeEstados(
                web3j, Credentials.create(cadena.officerPrivateKey()), cadena.contrato(), cadena.chainId());
    }

    @Bean
    AnclarCambiosDeEstado anclarCambiosDeEstado(CambiosDeEstado cambios, AnclajeDeEstados cadena) {
        return new AnclarCambiosDeEstado(cambios, cadena);
    }

    @Bean
    IpfsGateway ipfsGateway(LibroAbiertoProperties props) {
        return new KuboIpfsGateway(props.ipfs().apiUrl());
    }

    @Bean
    PublicarQuejasPendientes publicarQuejasPendientes(QuejaRepository repositorio, IpfsGateway ipfs,
            BlockchainGateway cadena) {
        return new PublicarQuejasPendientes(repositorio, ipfs, cadena);
    }

    @Bean
    FuenteDeEventos fuenteDeEventos(Web3j web3j, LibroAbiertoProperties props) {
        return new Web3jFuenteDeEventos(web3j, props.blockchain().contrato());
    }

    @Bean
    ProcesarEventosOnChain procesarEventosOnChain(QuejaRepository repositorio, FuenteDeEventos fuente,
            CheckpointRepository checkpoint, ConfirmacionDeCambios cambios, LibroAbiertoProperties props) {
        LibroAbiertoProperties.Listener listener = props.listener();
        return new ProcesarEventosOnChain(repositorio, fuente, checkpoint, cambios,
                listener.bloqueInicial(), listener.confirmaciones(), listener.maxBloquesPorLote());
    }
}
