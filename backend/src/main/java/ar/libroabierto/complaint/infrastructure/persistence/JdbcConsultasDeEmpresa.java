package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.ConsultasDeEmpresa;
import ar.libroabierto.complaint.application.ConsultasPublicas.EmpresaResumen;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** La empresa ve lo mismo que el público de sus quejas, más si ya respondió: nunca quién se quejó. */
@Component
class JdbcConsultasDeEmpresa implements ConsultasDeEmpresa {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConsultasDeEmpresa(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmpresaResumen> empresaDe(UUID usuarioId) {
        return jdbc.query("""
                SELECT e.cuit, e.razon_social FROM usuario u JOIN empresa e ON e.id = u.empresa_id
                WHERE u.id = :id AND u.rol = 'EMPRESA'""", Map.of("id", usuarioId),
                (rs, i) -> new EmpresaResumen(rs.getString("cuit"), rs.getString("razon_social"))).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuejaRecibida> quejasRecibidas(UUID usuarioId, int pagina, int tamanio) {
        // Primero las que ya se pueden responder y todavía no tienen respuesta; después, de la más nueva a la más vieja.
        return jdbc.query("""
                SELECT q.id, q.onchain_id, q.categoria_declarada, q.fecha_hecho, q.estado, q.anchor_status,
                       r.anchor_status AS respuesta
                FROM queja q
                JOIN usuario u ON u.empresa_id = q.empresa_id AND u.rol = 'EMPRESA'
                LEFT JOIN respuesta r ON r.queja_id = q.id
                WHERE u.id = :usuario
                ORDER BY (q.anchor_status = 'CONFIRMADA' AND r.id IS NULL) DESC, q.created_at DESC, q.id
                LIMIT :tamanio OFFSET :desde""",
                Map.of("usuario", usuarioId, "tamanio", tamanio, "desde", (long) pagina * tamanio),
                (rs, i) -> new QuejaRecibida(rs.getObject("id", UUID.class),
                        rs.getObject("onchain_id") == null ? null : rs.getLong("onchain_id"),
                        rs.getString("categoria_declarada"), rs.getObject("fecha_hecho", LocalDate.class),
                        rs.getString("estado"), "CONFIRMADA".equals(rs.getString("anchor_status")),
                        rs.getString("respuesta")));
    }
}
