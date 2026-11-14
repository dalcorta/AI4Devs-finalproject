package ar.libroabierto.complaint.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura pública (HU-02). Los modelos traen solo lo que ya es público o sirve para verificar: nunca el usuario, el
 * actor de un cambio de estado ni sus comentarios internos.
 */
public interface ConsultasPublicas {

    List<EmpresaResumen> empresasPorCuit(String prefijo, int limite);

    /** @param texto ya en minúsculas; se compara sin acentos */
    List<EmpresaResumen> empresasPorRazonSocial(String texto, int limite);

    /** Vacío si la empresa no existe; una empresa sin quejas devuelve una lista vacía. */
    Optional<List<QuejaResumen>> quejasDeEmpresa(String cuit, int pagina, int tamanio);

    Optional<QuejaDetalle> queja(UUID id);

    record EmpresaResumen(String cuit, String razonSocial) {
    }

    /** Sin anclar todavía, onchainId, ipfsCid y contentHash son nulos: el frontend la muestra "Pendiente de anclaje". */
    record QuejaResumen(UUID id, Long onchainId, String ipfsCid, String contentHash, String estado, String categoria,
            LocalDate fechaHecho) {
    }

    record QuejaDetalle(UUID id, Long onchainId, String ipfsCid, String contentHash, String estado,
            List<CambioDeEstado> historialEstados, List<String> evidenciasCids) {
    }

    record CambioDeEstado(String estadoAnterior, String estadoNuevo, String txHash, Instant createdAt) {
    }
}
