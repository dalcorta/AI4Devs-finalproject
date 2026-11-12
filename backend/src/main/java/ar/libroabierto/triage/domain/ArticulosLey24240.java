package ar.libroabierto.triage.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lista cerrada de artículos de la Ley 24.240 que el triage puede sugerir. Cualquier otro artículo que devuelva el LLM
 * se descarta: así una alucinación nunca llega al funcionario como si fuera normativa.
 *
 * <p>Los temas solo orientan al modelo. La selección de artículos debe revisarla alguien con formación jurídica.
 */
public final class ArticulosLey24240 {

    public static final Map<String, String> PERMITIDOS;

    static {
        Map<String, String> articulos = new LinkedHashMap<>();
        articulos.put("4", "Deber de información");
        articulos.put("5", "Protección de la salud y la seguridad");
        articulos.put("6", "Cosas y servicios riesgosos");
        articulos.put("7", "Oferta");
        articulos.put("8", "Efectos de la publicidad");
        articulos.put("8 bis", "Trato digno y prácticas abusivas");
        articulos.put("10", "Contenido del documento de venta");
        articulos.put("10 bis", "Incumplimiento de la obligación");
        articulos.put("10 ter", "Modos de rescisión");
        articulos.put("11", "Garantía legal");
        articulos.put("12", "Servicio técnico");
        articulos.put("13", "Responsabilidad solidaria por la garantía");
        articulos.put("14", "Certificado de garantía");
        articulos.put("15", "Constancia de reparación");
        articulos.put("16", "Prolongación del plazo de garantía");
        articulos.put("17", "Reparación no satisfactoria");
        articulos.put("18", "Vicios redhibitorios");
        articulos.put("19", "Modalidades de prestación de servicios");
        articulos.put("23", "Deficiencias en la prestación del servicio");
        articulos.put("25", "Servicios públicos domiciliarios");
        articulos.put("26", "Reciprocidad en el trato");
        articulos.put("27", "Registro de reclamos");
        articulos.put("28", "Seguridad de las instalaciones");
        articulos.put("30", "Interrupción de la prestación del servicio");
        articulos.put("31", "Cuestionamientos de facturación");
        articulos.put("32", "Venta domiciliaria");
        articulos.put("33", "Venta por correspondencia y otras a distancia");
        articulos.put("34", "Revocación de la aceptación");
        articulos.put("35", "Cobro de productos o servicios no solicitados");
        articulos.put("36", "Operaciones de venta de crédito");
        articulos.put("37", "Interpretación del contrato y cláusulas abusivas");
        articulos.put("38", "Contratos de adhesión");
        articulos.put("39", "Modificación de contratos tipo");
        articulos.put("40", "Responsabilidad solidaria por daños");
        articulos.put("40 bis", "Daño directo");
        articulos.put("52 bis", "Daño punitivo");
        PERMITIDOS = Collections.unmodifiableMap(articulos);
    }

    private ArticulosLey24240() {
    }

    public static boolean permitido(String articulo) {
        return PERMITIDOS.containsKey(articulo);
    }
}
