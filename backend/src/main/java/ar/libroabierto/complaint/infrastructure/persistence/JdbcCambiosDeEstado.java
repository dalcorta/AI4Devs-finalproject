package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.CambioPendiente;
import ar.libroabierto.complaint.application.CambiosDeEstado;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import ar.libroabierto.listener.application.ConfirmacionDeCambios;
import ar.libroabierto.listener.application.EventoEstadoCambiado;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcCambiosDeEstado implements CambiosDeEstado, ConfirmacionDeCambios {

    private static final Logger log = LoggerFactory.getLogger(JdbcCambiosDeEstado.class);

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCambiosDeEstado(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Situacion> situacion(UUID quejaId) {
        return jdbc.query("SELECT onchain_id, anchor_status, estado FROM queja WHERE id = :id", Map.of("id", quejaId),
                (rs, i) -> new Situacion(
                        rs.getObject("onchain_id") == null ? null : rs.getLong("onchain_id"),
                        AnchorStatus.valueOf(rs.getString("anchor_status")),
                        EstadoQueja.valueOf(rs.getString("estado")))).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean registrar(CambioDeEstado cambio) {
        // UPDATE condicional: si otro funcionario la cambió entre la lectura y acá, no se pisa su cambio.
        int actualizadas = jdbc.update("UPDATE queja SET estado = :nuevo WHERE id = :queja AND estado = :anterior",
                Map.of("nuevo", cambio.nuevo().name(), "queja", cambio.quejaId(), "anterior", cambio.anterior().name()));
        if (actualizadas == 0) {
            return false;
        }
        var parametros = new HashMap<String, Object>();
        parametros.put("id", cambio.id());
        parametros.put("queja", cambio.quejaId());
        parametros.put("anterior", cambio.anterior().name());
        parametros.put("nuevo", cambio.nuevo().name());
        parametros.put("actor", cambio.actorId());
        parametros.put("comentario", cambio.comentario());
        parametros.put("creado", Timestamp.from(cambio.registradoEn()));
        jdbc.update("""
                INSERT INTO cambio_estado (id, queja_id, estado_anterior, estado_nuevo, actor_id, comentario, created_at)
                VALUES (:id, :queja, :anterior, :nuevo, :actor, :comentario, :creado)""", parametros);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CambioPendiente> pendientesDeAnclaje() {
        return jdbc.query("""
                SELECT c.id, c.queja_id, q.onchain_id, c.estado_nuevo, c.comentario
                FROM cambio_estado c JOIN queja q ON q.id = c.queja_id
                WHERE c.tx_hash IS NULL
                ORDER BY c.created_at, c.id""", Map.of(),
                (rs, i) -> new CambioPendiente(rs.getObject("id", UUID.class), rs.getObject("queja_id", UUID.class),
                        rs.getLong("onchain_id"), EstadoQueja.valueOf(rs.getString("estado_nuevo")),
                        rs.getString("comentario")));
    }

    @Override
    @Transactional
    public void marcarEnviado(UUID cambioId, String txHash) {
        jdbc.update("UPDATE cambio_estado SET tx_hash = :tx WHERE id = :id", Map.of("tx", txHash, "id", cambioId));
        jdbc.update("""
                INSERT INTO transaccion_blockchain (id, tx_hash, tipo, referencia_id, estado, intentos)
                VALUES (:id, :tx, 'CAMBIO_ESTADO', :cambio, 'ENVIADA', 1)
                ON CONFLICT (tx_hash) DO NOTHING""",
                Map.of("id", UUID.randomUUID(), "tx", txHash, "cambio", cambioId));
    }

    @Override
    @Transactional
    public void confirmar(EventoEstadoCambiado evento) {
        // Cada estado se alcanza una sola vez (la máquina no tiene ciclos): (queja, estado nuevo) identifica el cambio.
        List<UUID> cambio = jdbc.queryForList("""
                SELECT c.id FROM cambio_estado c JOIN queja q ON q.id = c.queja_id
                WHERE q.onchain_id = :onchain AND c.estado_nuevo = :nuevo""",
                Map.of("onchain", evento.onchainId(), "nuevo", evento.nuevo().name()), UUID.class);
        if (cambio.isEmpty()) {
            log.warn("Evento StatusChanged de la queja on-chain {} sin cambio local hacia {}", evento.onchainId(),
                    evento.nuevo());
            return;
        }
        Map<String, Object> parametros = Map.of("id", cambio.get(0), "tx", evento.txHash(), "bloque", evento.bloque(),
                "nueva", UUID.randomUUID());
        jdbc.update("UPDATE cambio_estado SET tx_hash = :tx WHERE id = :id AND tx_hash IS NULL", parametros);
        jdbc.update("""
                INSERT INTO transaccion_blockchain (id, tx_hash, tipo, referencia_id, block_number, estado, intentos)
                VALUES (:nueva, :tx, 'CAMBIO_ESTADO', :id, :bloque, 'CONFIRMADA', 1)
                ON CONFLICT (tx_hash) DO UPDATE SET estado = 'CONFIRMADA', block_number = :bloque""", parametros);
    }
}
