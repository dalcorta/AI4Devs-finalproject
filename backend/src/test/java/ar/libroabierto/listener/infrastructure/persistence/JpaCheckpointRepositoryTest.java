package ar.libroabierto.listener.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainer.class)
class JpaCheckpointRepositoryTest {

    private static final String CONTRATO = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
    private static final String OTRO = "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512";

    @Autowired
    private ListenerCheckpointJpaRepository checkpoints;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager em;

    @Test
    void sinCheckpointPrevioDevuelveVacio() {
        assertThat(repositorio(CONTRATO).ultimoBloque()).isEmpty();
    }

    @Test
    void elCheckpointGuardadoSobreviveAUnaNuevaInstancia() {
        repositorio(CONTRATO).guardar(41);
        repositorio(CONTRATO).guardar(57);
        sincronizar();

        assertThat(repositorio(CONTRATO).ultimoBloque()).isEqualTo(OptionalLong.of(57));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listener_checkpoint", Long.class)).isEqualTo(1);
    }

    @Test
    void cadaContratoTieneSuPropioCheckpointYLaDireccionSeNormaliza() {
        repositorio(CONTRATO).guardar(10);
        repositorio(OTRO).guardar(20);
        sincronizar();

        assertThat(repositorio(CONTRATO.toLowerCase()).ultimoBloque()).isEqualTo(OptionalLong.of(10));
        assertThat(repositorio(OTRO).ultimoBloque()).isEqualTo(OptionalLong.of(20));
        assertThat(jdbc.queryForList("SELECT contrato FROM listener_checkpoint ORDER BY ultimo_bloque", String.class))
                .containsExactly(CONTRATO.toLowerCase(), OTRO.toLowerCase());
    }

    private JpaCheckpointRepository repositorio(String contrato) {
        return new JpaCheckpointRepository(checkpoints, contrato);
    }

    private void sincronizar() {
        em.flush();
        em.clear();
    }
}
