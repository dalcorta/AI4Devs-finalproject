package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.ConsultasDelPanel;
import ar.libroabierto.complaint.domain.EstadoQueja;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcConsultasDelPanel implements ConsultasDelPanel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConsultasDelPanel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDeBandeja> bandeja(EstadoQueja estado, int pagina, int tamanio) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("tamanio", tamanio);
        parametros.put("desde", (long) pagina * tamanio);
        String filtro = "";
        if (estado != null) {
            filtro = "AND q.estado = :estado";
            parametros.put("estado", estado.name());
        }
        return jdbc.query("""
                SELECT q.id, e.razon_social, t.estado AS triage_estado, t.categoria_sugerida, t.resumen, t.prioridad,
                       t.articulos_sugeridos::text AS articulos, q.estado, q.created_at
                FROM queja q
                JOIN empresa e ON e.id = q.empresa_id
                LEFT JOIN triage t ON t.queja_id = q.id
                WHERE q.anchor_status = 'CONFIRMADA' %s
                ORDER BY CASE
                             WHEN t.estado = 'MANUAL' THEN 0
                             WHEN t.prioridad = 'ALTA' THEN 1
                             WHEN t.prioridad = 'MEDIA' THEN 2
                             WHEN t.prioridad = 'BAJA' THEN 3
                             ELSE 4
                         END,
                         q.created_at, q.id
                LIMIT :tamanio OFFSET :desde""".formatted(filtro), parametros,
                (rs, i) -> new ItemDeBandeja(
                        rs.getObject("id", UUID.class),
                        rs.getString("razon_social"),
                        rs.getString("triage_estado") == null ? "PENDIENTE" : rs.getString("triage_estado"),
                        rs.getString("categoria_sugerida"),
                        rs.getString("resumen"),
                        rs.getString("prioridad"),
                        articulos(rs.getString("articulos")),
                        EstadoQueja.valueOf(rs.getString("estado")),
                        rs.getTimestamp("created_at").toInstant()));
    }

    private static List<String> articulos(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("articulos_sugeridos no es una lista JSON", e);
        }
    }
}
