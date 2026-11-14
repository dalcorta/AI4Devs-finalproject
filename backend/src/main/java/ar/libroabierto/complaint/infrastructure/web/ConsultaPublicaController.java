package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.ConsultaPublica;
import ar.libroabierto.complaint.application.ConsultasPublicas;
import ar.libroabierto.complaint.application.DatosInvalidosException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** HU-02: lectura pública, sin autenticación. La verificación de integridad la hace el navegador contra IPFS y la cadena. */
@RestController
class ConsultaPublicaController {

    private final ConsultaPublica consulta;

    ConsultaPublicaController(ConsultaPublica consulta) {
        this.consulta = consulta;
    }

    record Pagina<T>(List<T> items) {
    }

    @GetMapping("/api/empresas")
    List<ConsultasPublicas.EmpresaResumen> buscar(@RequestParam(required = false) String q) {
        return consulta.buscarEmpresas(q);
    }

    @GetMapping("/api/empresas/{cuit}/quejas")
    ResponseEntity<Pagina<ConsultasPublicas.QuejaResumen>> quejasDeEmpresa(@PathVariable String cuit,
            @RequestParam(defaultValue = "0") int page) {
        return consulta.quejasDeEmpresa(cuit, page)
                .map(items -> ResponseEntity.ok(new Pagina<>(items)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/quejas/{id}")
    ResponseEntity<ConsultasPublicas.QuejaDetalle> queja(@PathVariable UUID id) {
        return ResponseEntity.of(consulta.queja(id));
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<Map<String, Object>> datosInvalidos(DatosInvalidosException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Hay datos inválidos", "errores", e.errores()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", "El parámetro " + e.getName() + " no tiene un formato válido"));
    }
}
