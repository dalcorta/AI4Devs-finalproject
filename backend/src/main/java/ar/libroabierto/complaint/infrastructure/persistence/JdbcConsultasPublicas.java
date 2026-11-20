package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.ConsultasPublicas;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lado de lectura con SQL directo: son proyecciones planas y así queda a la vista, columna por columna, qué sale a
 * la API pública. Ninguna consulta selecciona usuario_id, actor_id, autor_id ni comentario.
 */
@Component
class JdbcConsultasPublicas implements ConsultasPublicas {

    // Mismo reemplazo de ambos lados de la comparación, así "nacion" encuentra "Nación" sin la extensión unaccent.
    private static final String SIN_ACENTOS = "translate(%s, 'áéíóúüñàèìòù', 'aeiouunaeiou')";

    private static final RowMapper<EmpresaResumen> EMPRESA =
            (rs, i) -> new EmpresaResumen(rs.getString("cuit"), rs.getString("razon_social"));

    private static final RowMapper<QuejaResumen> RESUMEN = (rs, i) -> new QuejaResumen(
            rs.getObject("id", UUID.class), onchainId(rs), rs.getString("ipfs_cid"), rs.getString("content_hash"),
            rs.getString("estado"), rs.getString("categoria_declarada"), rs.getObject("fecha_hecho", LocalDate.class),
            rs.getString("tx_hash"));

    // Columnas públicas de la queja más la transacción de registro confirmada. Solo la confirmada: un reintento del
    // worker puede dejar envíos que nunca se minaron, y el enlace tiene que llevar a la transacción real.
    private static final String COLUMNAS_QUEJA = """
            SELECT q.id, q.onchain_id, q.ipfs_cid, q.content_hash, q.estado, q.categoria_declarada, q.fecha_hecho,
                   (SELECT t.tx_hash FROM transaccion_blockchain t
                    WHERE t.tipo = 'REGISTRO' AND t.referencia_id = q.id AND t.estado = 'CONFIRMADA'
                    ORDER BY t.created_at DESC LIMIT 1) AS tx_hash
            FROM queja q
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConsultasPublicas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmpresaResumen> empresasPorCuit(String prefijo, int limite) {
        return jdbc.query("""
                SELECT cuit, razon_social FROM empresa
                WHERE cuit LIKE :prefijo || '%'
                ORDER BY cuit LIMIT :limite""", Map.of("prefijo", prefijo, "limite", limite), EMPRESA);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmpresaResumen> empresasPorRazonSocial(String texto, int limite) {
        String sql = """
                SELECT cuit, razon_social FROM empresa
                WHERE %s LIKE '%%' || %s || '%%' ESCAPE '\\'
                ORDER BY razon_social LIMIT :limite""".formatted(
                SIN_ACENTOS.formatted("lower(razon_social)"), SIN_ACENTOS.formatted(":texto"));
        return jdbc.query(sql, Map.of("texto", escaparLike(texto), "limite", limite), EMPRESA);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<QuejaResumen>> quejasDeEmpresa(String cuit, int pagina, int tamanio) {
        List<UUID> empresa = jdbc.queryForList("SELECT id FROM empresa WHERE cuit = :cuit", Map.of("cuit", cuit),
                UUID.class);
        if (empresa.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jdbc.query(COLUMNAS_QUEJA + """
                WHERE q.empresa_id = :empresa
                ORDER BY q.created_at DESC, q.id
                LIMIT :tamanio OFFSET :desde""",
                Map.of("empresa", empresa.get(0), "tamanio", tamanio, "desde", (long) pagina * tamanio), RESUMEN));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuejaDetalle> queja(UUID id) {
        List<QuejaResumen> quejas = jdbc.query(COLUMNAS_QUEJA + "WHERE q.id = :id", Map.of("id", id), RESUMEN);
        if (quejas.isEmpty()) {
            return Optional.empty();
        }
        QuejaResumen queja = quejas.get(0);
        List<CambioDeEstado> historial = jdbc.query("""
                SELECT estado_anterior, estado_nuevo, tx_hash, created_at
                FROM cambio_estado WHERE queja_id = :id
                ORDER BY created_at, id""", Map.of("id", id),
                (rs, i) -> new CambioDeEstado(rs.getString("estado_anterior"), rs.getString("estado_nuevo"),
                        rs.getString("tx_hash"), rs.getTimestamp("created_at").toInstant()));
        List<String> evidencias = jdbc.queryForList(
                "SELECT ipfs_cid FROM evidencia WHERE queja_id = :id ORDER BY created_at, id", Map.of("id", id),
                String.class);
        RespuestaDeEmpresa respuesta = jdbc.query(
                "SELECT anchor_status, tx_hash, created_at FROM respuesta WHERE queja_id = :id", Map.of("id", id),
                (rs, i) -> new RespuestaDeEmpresa(rs.getString("anchor_status"), rs.getString("tx_hash"),
                        rs.getTimestamp("created_at").toInstant())).stream().findFirst().orElse(null);
        return Optional.of(new QuejaDetalle(queja.id(), queja.onchainId(), queja.ipfsCid(), queja.contentHash(),
                queja.txHash(), queja.estado(), historial, evidencias, respuesta));
    }

    private static Long onchainId(ResultSet rs) throws SQLException {
        long valor = rs.getLong("onchain_id");
        return rs.wasNull() ? null : valor;
    }

    /** Los comodines que escribe el usuario se buscan como texto: "%%" no puede listar todas las empresas. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
