package ar.libroabierto.privacidad.application;

import ar.libroabierto.privacidad.domain.Hallazgo;
import ar.libroabierto.privacidad.domain.TipoDatoPersonal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datos personales con formato fijo (email, CUIL, CBU, tarjeta, teléfono, DNI), sin modelo: lo que se puede encontrar
 * con certeza no depende de que un LLM chico acierte. Prefiere avisar de más: un falso positivo se resuelve con una
 * confirmación del autor, un dato publicado no se puede borrar.
 */
public class DetectorDePatrones {

    /** Ni letra, dígito, punto, coma, guion ni signo de pesos antes: el número no es parte de otro mayor. */
    private static final String INICIO = "(?<![\\p{L}\\d.,$-])";
    /** Ni letra, dígito ni guion después, ni un punto o una coma seguidos de un dígito. */
    private static final String FIN = "(?![\\p{L}\\d-])(?![.,]\\d)";

    private static final Pattern MONEDA_ANTES = Pattern.compile("(?:\\$|ARS|USD|U\\$S)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEDA_DESPUES =
            Pattern.compile("^\\s*(?:pesos|ars|d[oó]lares|usd)\\b", Pattern.CASE_INSENSITIVE);

    /** En orden de prioridad: si dos patrones se superponen, gana el primero. */
    private final List<Patron> patrones = List.of(
            new Patron(TipoDatoPersonal.EMAIL,
                    Pattern.compile("(?<![\\w.+-])[\\w.%+-]+@[A-Za-z\\d.-]+\\.[A-Za-z]{2,}"), (t, m) -> true),
            // CUIL de persona física (20, 23, 24, 27); el CUIT de una empresa (30, 33, 34) es público. No se exige el
            // dígito verificador: un CUIL mal tipeado igual lleva el DNI en el medio.
            new Patron(TipoDatoPersonal.CUIL,
                    Pattern.compile(INICIO + "(?:20|23|24|27)-?\\d{8}-?\\d" + FIN), (t, m) -> true),
            new Patron(TipoDatoPersonal.CBU, Pattern.compile(INICIO + "\\d{22}" + FIN), (t, m) -> true),
            new Patron(TipoDatoPersonal.TARJETA,
                    Pattern.compile(INICIO + "\\d(?:[ -]?\\d){12,18}" + FIN),
                    (t, m) -> luhn(digitos(m.group()))),
            new Patron(TipoDatoPersonal.TELEFONO, Pattern.compile(String.join("|",
                    // Internacional: +54 y el resto con cualquier separador.
                    "\\+54[\\d ()-]{8,16}\\d",
                    // Con un guion antes de los últimos cuatro dígitos: 4567-8901, (011) 4567-8901, 15-4567-8901.
                    INICIO + "(?:\\(?0?\\d{2,4}\\)?[ -])?(?:15[ -]?)?\\d{3,4}-\\d{4}" + FIN,
                    // Celular o línea de Buenos Aires sin guion: 11 4567 8901, 1145678901, 15 4567 8901.
                    INICIO + "(?:0?11|15) ?\\d{4} ?\\d{4}" + FIN)),
                    (t, m) -> {
                        int cantidad = digitos(m.group()).length();
                        return cantidad >= 8 && cantidad <= 13;
                    }),
            new Patron(TipoDatoPersonal.DNI,
                    Pattern.compile(INICIO + "(?:\\d{1,2}\\.\\d{3}\\.\\d{3}|\\d{7,8})" + FIN),
                    DetectorDePatrones::noEsUnMonto));

    public List<Hallazgo> detectar(String texto) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Patron patron : patrones) {
            Matcher m = patron.expresion().matcher(texto);
            while (m.find()) {
                Hallazgo hallazgo = new Hallazgo(patron.tipo(), m.group(), m.start(), m.end());
                if (patron.valido().test(texto, m) && hallazgos.stream().noneMatch(h -> seSuperponen(h, hallazgo))) {
                    hallazgos.add(hallazgo);
                }
            }
        }
        hallazgos.sort(Comparator.comparingInt(Hallazgo::inicio));
        return hallazgos;
    }

    static boolean seSuperponen(Hallazgo a, Hallazgo b) {
        return a.inicio() < b.fin() && b.inicio() < a.fin();
    }

    /** "$ 12.500.000" o "1.500.000 pesos" son montos, no documentos. */
    private static boolean noEsUnMonto(String texto, Matcher m) {
        String antes = texto.substring(Math.max(0, m.start() - 6), m.start());
        String despues = texto.substring(m.end());
        return !MONEDA_ANTES.matcher(antes).find() && !MONEDA_DESPUES.matcher(despues).find();
    }

    private static String digitos(String texto) {
        return texto.replaceAll("\\D", "");
    }

    private static boolean luhn(String digitos) {
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int digito = digitos.charAt(digitos.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
        }
        return suma % 10 == 0;
    }

    private record Patron(TipoDatoPersonal tipo, Pattern expresion, BiPredicate<String, Matcher> valido) {
    }
}
