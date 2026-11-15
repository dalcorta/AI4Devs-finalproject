package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.RespuestaPendiente;
import ar.libroabierto.complaint.application.Respuestas;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.DatosDeRespuesta;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.listener.application.ConfirmacionDeRespuestas;
import ar.libroabierto.listener.application.EventoRespuestaRegistrada;
import java.sql.Timestamp;
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
class JdbcRespuestas implements Respuestas, ConfirmacionDeRespuestas {

    private static final Logger log = LoggerFactory.getLogger(JdbcRespuestas.class);

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRespuestas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> empresaDe(UUID usuarioId) {
        return jdbc.queryForList("SELECT empresa_id FROM usuario WHERE id = :id AND rol = 'EMPRESA'",
                Map.of("id", usuarioId), UUID.class).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Situacion> situacion(UUID quejaId) {
        return jdbc.query("""
                SELECT q.empresa_id, q.anchor_status,
                       EXISTS (SELECT 1 FROM respuesta r WHERE r.queja_id = q.id) AS respondida
                FROM queja q WHERE q.id = :id""", Map.of("id", quejaId),
                (rs, i) -> new Situacion(rs.getObject("empresa_id", UUID.class),
                        AnchorStatus.valueOf(rs.getString("anchor_status")), rs.getBoolean("respondida")))
                .stream().findFirst();
    }

    @Override
    @Transactional
    public boolean registrar(NuevaRespuesta respuesta) {
        // queja_id es UNIQUE: si dos pedidos llegan juntos, el segundo no inserta nada y se informa como conflicto.
        return jdbc.update("""
                INSERT INTO respuesta (id, queja_id, autor_id, texto, created_at)
                VALUES (:id, :queja, :autor, :texto, :creada)
                ON CONFLICT (queja_id) DO NOTHING""", Map.of("id", respuesta.id(), "queja", respuesta.quejaId(),
                "autor", respuesta.autorId(), "texto", respuesta.texto(),
                "creada", Timestamp.from(respuesta.creadaEn()))) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RespuestaPendiente> pendientesDeAnclaje() {
        return jdbc.query("""
                SELECT r.id, r.texto, r.created_at, r.ipfs_cid, r.content_hash,
                       q.onchain_id, q.content_hash AS queja_hash, e.cuit, e.razon_social
                FROM respuesta r
                JOIN queja q ON q.id = r.queja_id
                JOIN empresa e ON e.id = q.empresa_id
                WHERE r.anchor_status = 'PENDIENTE' AND q.anchor_status = 'CONFIRMADA'
                ORDER BY r.created_at, r.id""", Map.of(),
                (rs, i) -> new RespuestaPendiente(rs.getObject("id", UUID.class),
                        new DatosDeRespuesta(rs.getLong("onchain_id"), rs.getString("queja_hash"),
                                new Empresa(rs.getString("cuit"), rs.getString("razon_social")),
                                rs.getString("texto"), rs.getTimestamp("created_at").toInstant()),
                        rs.getString("ipfs_cid"), rs.getString("content_hash")));
    }

    @Override
    @Transactional
    public void registrarPublicacion(UUID respuestaId, String ipfsCid, String contentHash) {
        int actualizadas = jdbc.update("""
                UPDATE respuesta SET ipfs_cid = :cid, content_hash = :hash
                WHERE id = :id AND anchor_status = 'PENDIENTE' AND content_hash IS NULL""",
                Map.of("cid", ipfsCid, "hash", contentHash, "id", respuestaId));
        if (actualizadas == 0) {
            throw new IllegalStateException("La respuesta " + respuestaId + " ya estaba publicada");
        }
    }

    @Override
    @Transactional
    public void marcarEnviada(UUID respuestaId, String txHash) {
        jdbc.update("""
                UPDATE respuesta SET tx_hash = :tx, anchor_status = 'ENVIADA'
                WHERE id = :id AND anchor_status = 'PENDIENTE'""", Map.of("tx", txHash, "id", respuestaId));
        jdbc.update("""
                INSERT INTO transaccion_blockchain (id, tx_hash, tipo, referencia_id, estado, intentos)
                VALUES (:id, :tx, 'RESPUESTA', :respuesta, 'ENVIADA', 1)
                ON CONFLICT (tx_hash) DO NOTHING""",
                Map.of("id", UUID.randomUUID(), "tx", txHash, "respuesta", respuestaId));
    }

    @Override
    @Transactional
    public void confirmar(EventoRespuestaRegistrada evento) {
        // Se busca por hash y por la queja del evento: un hash que coincide con la respuesta de otra queja no confirma.
        List<UUID> respuesta = jdbc.queryForList("""
                SELECT r.id FROM respuesta r JOIN queja q ON q.id = r.queja_id
                WHERE r.content_hash = :hash AND q.onchain_id = :onchain""",
                Map.of("hash", evento.contentHash(), "onchain", evento.quejaOnchainId()), UUID.class);
        if (respuesta.isEmpty()) {
            log.warn("Evento ResponseFiled de la queja on-chain {} sin respuesta local para el hash {}",
                    evento.quejaOnchainId(), evento.contentHash());
            return;
        }
        Map<String, Object> parametros = Map.of("id", respuesta.get(0), "tx", evento.txHash(), "bloque",
                evento.bloque(), "nueva", UUID.randomUUID());
        // La cadena es la fuente de verdad: confirma también una respuesta que quedó PENDIENTE porque el worker se cayó
        // después de enviar la transacción.
        jdbc.update("UPDATE respuesta SET anchor_status = 'CONFIRMADA', tx_hash = :tx WHERE id = :id", parametros);
        jdbc.update("""
                INSERT INTO transaccion_blockchain (id, tx_hash, tipo, referencia_id, block_number, estado, intentos)
                VALUES (:nueva, :tx, 'RESPUESTA', :id, :bloque, 'CONFIRMADA', 1)
                ON CONFLICT (tx_hash) DO UPDATE SET estado = 'CONFIRMADA', block_number = :bloque""", parametros);
    }
}
