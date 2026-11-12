package ar.libroabierto.complaint.domain;

/** CUIT/CUIL argentino: 11 dígitos con dígito verificador módulo 11. */
public final class Cuit {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private Cuit() {
    }

    public static boolean esValido(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (cuit.charAt(i) - '0') * PESOS[i];
        }
        int verificador = 11 - suma % 11;
        if (verificador == 11) {
            verificador = 0;
        }
        // Un resto de 10 no tiene dígito: AFIP nunca emite esos CUIT.
        return verificador != 10 && verificador == cuit.charAt(10) - '0';
    }
}
