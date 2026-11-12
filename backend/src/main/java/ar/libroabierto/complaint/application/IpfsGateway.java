package ar.libroabierto.complaint.application;

public interface IpfsGateway {

    /** Publica el documento y devuelve su CID. */
    String publicar(String json);
}
