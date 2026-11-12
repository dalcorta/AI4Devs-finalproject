package ar.libroabierto.complaint.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Queja {

    private final UUID id;
    private final UUID usuarioId;
    private final Empresa empresa;
    private final String categoria;
    private final String descripcion;
    private final BigDecimal montoReclamado;
    private final LocalDate fechaHecho;
    private final List<String> evidenciasCids;
    private final Instant registradaEn;

    private AnchorStatus anchorStatus;
    private String ipfsCid;
    private String contentHash;
    private String txHash;
    private Long onchainId;
    private Long bloqueConfirmacion;

    private Queja(UUID id, UUID usuarioId, Empresa empresa, String categoria, String descripcion,
            BigDecimal montoReclamado, LocalDate fechaHecho, List<String> evidenciasCids, Instant registradaEn) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.empresa = Objects.requireNonNull(empresa);
        this.categoria = Objects.requireNonNull(categoria);
        this.descripcion = Objects.requireNonNull(descripcion);
        this.montoReclamado = montoReclamado;
        this.fechaHecho = Objects.requireNonNull(fechaHecho);
        this.evidenciasCids = List.copyOf(evidenciasCids);
        this.registradaEn = Objects.requireNonNull(registradaEn);
        this.anchorStatus = AnchorStatus.PENDIENTE;
    }

    public static Queja nueva(UUID id, UUID usuarioId, Empresa empresa, String categoria, String descripcion,
            BigDecimal montoReclamado, LocalDate fechaHecho, List<String> evidenciasCids, Instant registradaEn) {
        return new Queja(id, usuarioId, empresa, categoria, descripcion, montoReclamado, fechaHecho,
                evidenciasCids, registradaEn);
    }

    public static Queja reconstituir(UUID id, UUID usuarioId, Empresa empresa, String categoria, String descripcion,
            BigDecimal montoReclamado, LocalDate fechaHecho, List<String> evidenciasCids, Instant registradaEn,
            AnchorStatus anchorStatus, String ipfsCid, String contentHash, String txHash, Long onchainId) {
        Queja queja = new Queja(id, usuarioId, empresa, categoria, descripcion, montoReclamado, fechaHecho,
                evidenciasCids, registradaEn);
        queja.anchorStatus = Objects.requireNonNull(anchorStatus);
        queja.ipfsCid = ipfsCid;
        queja.contentHash = contentHash;
        queja.txHash = txHash;
        queja.onchainId = onchainId;
        return queja;
    }

    public boolean estaPublicada() {
        return contentHash != null;
    }

    public void registrarPublicacion(String ipfsCid, String contentHash) {
        exigirEstado(AnchorStatus.PENDIENTE);
        this.ipfsCid = Objects.requireNonNull(ipfsCid);
        this.contentHash = Objects.requireNonNull(contentHash);
    }

    public void marcarEnviada(String txHash) {
        exigirEstado(AnchorStatus.PENDIENTE);
        if (!estaPublicada()) {
            throw new IllegalStateException("No se puede anclar una queja que no fue publicada en IPFS");
        }
        this.txHash = Objects.requireNonNull(txHash);
        this.anchorStatus = AnchorStatus.ENVIADA;
    }

    /**
     * Aplica el evento ComplaintFiled: la cadena es la fuente de verdad, así que confirma también una queja publicada
     * que quedó PENDIENTE porque el worker se cayó después de enviar la transacción.
     *
     * @return {@code false} si la queja ya estaba confirmada con ese id (evento repetido): no cambia nada.
     */
    public boolean confirmarAnclaje(long onchainId, String txHash, long bloque) {
        if (anchorStatus == AnchorStatus.CONFIRMADA) {
            if (Objects.equals(this.onchainId, onchainId)) {
                return false;
            }
            throw new IllegalStateException("La queja " + id + " ya está confirmada con el id on-chain "
                    + this.onchainId + " y llegó otro evento con el id " + onchainId);
        }
        if (!estaPublicada()) {
            throw new IllegalStateException("No se puede confirmar una queja que no fue publicada en IPFS");
        }
        this.onchainId = onchainId;
        this.txHash = Objects.requireNonNull(txHash);
        this.bloqueConfirmacion = bloque;
        this.anchorStatus = AnchorStatus.CONFIRMADA;
        return true;
    }

    private void exigirEstado(AnchorStatus esperado) {
        if (anchorStatus != esperado) {
            throw new IllegalStateException("Se esperaba " + esperado + " pero la queja está en " + anchorStatus);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public Empresa empresa() {
        return empresa;
    }

    public String categoria() {
        return categoria;
    }

    public String descripcion() {
        return descripcion;
    }

    public BigDecimal montoReclamado() {
        return montoReclamado;
    }

    public LocalDate fechaHecho() {
        return fechaHecho;
    }

    public List<String> evidenciasCids() {
        return evidenciasCids;
    }

    public Instant registradaEn() {
        return registradaEn;
    }

    public AnchorStatus anchorStatus() {
        return anchorStatus;
    }

    public String ipfsCid() {
        return ipfsCid;
    }

    public String contentHash() {
        return contentHash;
    }

    public String txHash() {
        return txHash;
    }

    public Long onchainId() {
        return onchainId;
    }

    /** Bloque donde se confirmó el anclaje; solo se conoce al aplicar el evento, no al leer la queja de la base. */
    public Long bloqueConfirmacion() {
        return bloqueConfirmacion;
    }
}
