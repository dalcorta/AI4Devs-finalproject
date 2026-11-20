package ar.libroabierto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ticket 1: {@code scripts/seed.sql} carga 3 empresas ficticias y un usuario de cada rol, se puede correr más de una
 * vez y las credenciales que documenta en su encabezado sirven para ingresar por la API.
 */
@SpringBootTest(properties = {
        "libroabierto.worker.habilitado=false",
        "libroabierto.listener.habilitado=false",
        "libroabierto.triage.habilitado=false",
        "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
        "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
        "libroabierto.blockchain.officer-private-key=7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6",
        "libroabierto.auth.jwt-secret=secreto-de-prueba-de-al-menos-32-bytes-para-hs256"})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class SeedTest {

    // Maven corre los tests desde backend/: el seed vive en la raíz del repo.
    private static final Path SEED = Path.of("..", "scripts", "seed.sql");
    private static final Pattern CREDENCIAL = Pattern.compile("^--\\s+(\\S+@\\S+)\\s+clave:\\s+(\\S+)", Pattern.MULTILINE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void baseVacia() {
        for (String tabla : new String[] {"cambio_estado", "evidencia", "triage", "respuesta", "queja",
                "transaccion_blockchain", "usuario", "empresa"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void cargaTresEmpresasFicticiasYUnUsuarioDeCadaRol() {
        sembrar();

        assertThat(jdbc.queryForList("SELECT cuit FROM empresa ORDER BY cuit", String.class))
                .containsExactly("30711111111", "30722222225", "30733333339");
        assertThat(jdbc.queryForList("SELECT rol FROM usuario ORDER BY rol", String.class))
                .containsExactly("CIUDADANO", "EMPRESA", "FUNCIONARIO");
        assertThat(jdbc.queryForObject(
                "SELECT identidad_verificada FROM usuario WHERE rol = 'CIUDADANO'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT e.cuit FROM usuario u JOIN empresa e ON e.id = u.empresa_id
                WHERE u.rol = 'EMPRESA'""", String.class)).isEqualTo("30711111111");
    }

    @Test
    void sePuedeCorrerDosVecesSinDuplicarNiFallar() {
        sembrar();
        sembrar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(3);
    }

    @Test
    void lasCredencialesDocumentadasSirvenParaIngresarConSuRol() throws Exception {
        sembrar();
        Map<String, String> credenciales = credencialesDocumentadas();
        assertThat(credenciales).hasSize(3);

        for (Map.Entry<String, String> credencial : credenciales.entrySet()) {
            String rol = jdbc.queryForObject("SELECT rol FROM usuario WHERE email = ?", String.class,
                    credencial.getKey());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(credencial.getKey(),
                                    credencial.getValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value(rol));
        }
    }

    @Test
    void losDatosDePruebaSonFicticios() throws IOException {
        // Ni dominios reales en los emails ni DNI o teléfonos cargados.
        assertThat(credencialesDocumentadas().keySet()).allMatch(email -> email.endsWith(".test"));
        sembrar();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE dni_cifrado IS NOT NULL OR telefono_cifrado IS NOT NULL",
                Integer.class)).isZero();
    }

    private void sembrar() {
        new ResourceDatabasePopulator(new FileSystemResource(SEED)).execute(dataSource);
    }

    private static Map<String, String> credencialesDocumentadas() throws IOException {
        Matcher m = CREDENCIAL.matcher(Files.readString(SEED, StandardCharsets.UTF_8));
        Map<String, String> credenciales = new LinkedHashMap<>();
        while (m.find()) {
            credenciales.put(m.group(1), m.group(2));
        }
        return credenciales;
    }
}
