// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/// @notice Registro on-chain de quejas de consumo y de sus cambios de estado.
/// Solo se ancla el hash del contenido público (IPFS) y metadatos no personales;
/// los datos del denunciante nunca llegan a este contrato.
contract ComplaintRegistry is AccessControl {
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");
    bytes32 public constant OFFICER_ROLE = keccak256("OFFICER_ROLE");

    enum Status {
        REGISTRADA,
        EN_REVISION,
        DERIVADA,
        RESUELTA,
        DESESTIMADA
    }

    struct Complaint {
        bytes32 contentHash; // keccak256 del JSON público canonicalizado
        string ipfsCid; // CID del JSON público
        uint64 companyCuit; // CUIT de la empresa (dato público)
        uint64 timestamp;
        Status status;
    }

    /// @notice Respuesta pública de la empresa denunciada (una por queja).
    struct Response {
        bytes32 contentHash; // keccak256 del JSON público de la respuesta, canonicalizado
        string ipfsCid; // CID de ese JSON
        uint64 timestamp;
    }

    event ComplaintFiled(uint256 indexed id, uint64 indexed companyCuit, bytes32 contentHash, string ipfsCid);
    event StatusChanged(uint256 indexed id, Status previous, Status current, bytes32 noteHash);
    event ResponseFiled(uint256 indexed complaintId, bytes32 contentHash, string ipfsCid);

    error ComplaintNotFound(uint256 id);
    error InvalidTransition(Status from, Status to);
    error ComplaintAlreadyFiled(bytes32 contentHash, uint256 existingId);
    error ResponseAlreadyFiled(uint256 complaintId);
    error ResponseNotFound(uint256 complaintId);

    uint256 private _nextId = 1;
    mapping(uint256 => Complaint) private _complaints;
    // El backend correlaciona eventos con quejas por contentHash: debe ser único on-chain.
    mapping(bytes32 => uint256) private _idByContentHash;
    mapping(uint256 => Response) private _responses;

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    /// @notice Registra una queja nueva. Solo el relayer del backend puede llamarla:
    /// el ciudadano nunca firma esta transacción, así que este rol únicamente agrega
    /// registros y no puede modificarlos ni borrarlos.
    function fileComplaint(uint64 companyCuit, bytes32 contentHash, string calldata ipfsCid)
        external
        onlyRole(RELAYER_ROLE)
        returns (uint256 id)
    {
        uint256 existingId = _idByContentHash[contentHash];
        if (existingId != 0) revert ComplaintAlreadyFiled(contentHash, existingId);

        id = _nextId++;
        _idByContentHash[contentHash] = id;
        _complaints[id] = Complaint({
            contentHash: contentHash,
            ipfsCid: ipfsCid,
            companyCuit: companyCuit,
            // casting to 'uint64' is safe because block.timestamp no supera 2^64 en miles de millones de años
            // forge-lint: disable-next-line(unsafe-typecast)
            timestamp: uint64(block.timestamp),
            status: Status.REGISTRADA
        });

        emit ComplaintFiled(id, companyCuit, contentHash, ipfsCid);
    }

    /// @notice Cambia el estado de una queja siguiendo la máquina de estados
    /// REGISTRADA -> EN_REVISION -> DERIVADA -> RESUELTA | DESESTIMADA.
    /// @param noteHash hash del comentario del funcionario (keccak256), o bytes32(0) si no hay comentario;
    /// el texto del comentario no se publica on-chain.
    function changeStatus(uint256 id, Status newStatus, bytes32 noteHash) external onlyRole(OFFICER_ROLE) {
        Complaint storage complaint = _complaints[id];
        if (complaint.timestamp == 0) revert ComplaintNotFound(id);

        Status previous = complaint.status;
        if (!_isValidTransition(previous, newStatus)) {
            revert InvalidTransition(previous, newStatus);
        }

        complaint.status = newStatus;
        emit StatusChanged(id, previous, newStatus, noteHash);
    }

    /// @notice Ancla la respuesta de la empresa a una queja. La firma el relayer: el backend ya comprobó que quien
    /// responde representa a la empresa denunciada, y el documento publicado lleva el CUIT y el hash de la queja.
    /// Hay una sola respuesta por queja, en cualquier estado: el derecho a responder no vence.
    function fileResponse(uint256 complaintId, bytes32 contentHash, string calldata ipfsCid)
        external
        onlyRole(RELAYER_ROLE)
    {
        if (_complaints[complaintId].timestamp == 0) revert ComplaintNotFound(complaintId);
        if (_responses[complaintId].timestamp != 0) revert ResponseAlreadyFiled(complaintId);

        _responses[complaintId] = Response({
            contentHash: contentHash,
            ipfsCid: ipfsCid,
            // forge-lint: disable-next-line(unsafe-typecast)
            timestamp: uint64(block.timestamp)
        });

        emit ResponseFiled(complaintId, contentHash, ipfsCid);
    }

    function getResponse(uint256 complaintId) external view returns (Response memory) {
        if (_responses[complaintId].timestamp == 0) revert ResponseNotFound(complaintId);
        return _responses[complaintId];
    }

    function hasResponse(uint256 complaintId) external view returns (bool) {
        return _responses[complaintId].timestamp != 0;
    }

    function getComplaint(uint256 id) external view returns (Complaint memory) {
        if (_complaints[id].timestamp == 0) revert ComplaintNotFound(id);
        return _complaints[id];
    }

    /// @notice Id on-chain de la queja con ese contentHash, o 0 si no fue registrada.
    function complaintIdByHash(bytes32 contentHash) external view returns (uint256) {
        return _idByContentHash[contentHash];
    }

    function totalComplaints() external view returns (uint256) {
        return _nextId - 1;
    }

    function _isValidTransition(Status from, Status to) private pure returns (bool) {
        if (from == Status.REGISTRADA && to == Status.EN_REVISION) return true;
        if (from == Status.EN_REVISION && to == Status.DERIVADA) return true;
        if (from == Status.DERIVADA && to == Status.RESUELTA) return true;
        if (from == Status.DERIVADA && to == Status.DESESTIMADA) return true;
        return false;
    }
}
