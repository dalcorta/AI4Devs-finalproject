package ar.libroabierto.config;

import ar.libroabierto.anchoring.application.BlockchainGateway;
import ar.libroabierto.anchoring.infrastructure.Web3jBlockchainGateway;
import ar.libroabierto.complaint.application.IpfsGateway;
import ar.libroabierto.complaint.application.PublicarQuejasPendientes;
import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.infrastructure.KuboIpfsGateway;
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
    IpfsGateway ipfsGateway(LibroAbiertoProperties props) {
        return new KuboIpfsGateway(props.ipfs().apiUrl());
    }

    @Bean
    PublicarQuejasPendientes publicarQuejasPendientes(QuejaRepository repositorio, IpfsGateway ipfs,
            BlockchainGateway cadena) {
        return new PublicarQuejasPendientes(repositorio, ipfs, cadena);
    }
}
