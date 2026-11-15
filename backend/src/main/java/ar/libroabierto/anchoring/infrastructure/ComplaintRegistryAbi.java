package ar.libroabierto.anchoring.infrastructure;

import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;

/** Partes del ABI de ComplaintRegistry.sol que usan los adaptadores Web3j. */
public final class ComplaintRegistryAbi {

    /** {@code ComplaintFiled(uint256 indexed id, uint64 indexed companyCuit, bytes32 contentHash, string ipfsCid)}. */
    public static final Event COMPLAINT_FILED = new Event("ComplaintFiled", List.of(
            new TypeReference<Uint256>(true) { },
            new TypeReference<Uint64>(true) { },
            new TypeReference<Bytes32>() { },
            new TypeReference<Utf8String>() { }));

    public static final String COMPLAINT_FILED_TOPIC = EventEncoder.encode(COMPLAINT_FILED);

    /** {@code StatusChanged(uint256 indexed id, Status previous, Status current, bytes32 noteHash)}; Status es uint8. */
    public static final Event STATUS_CHANGED = new Event("StatusChanged", List.of(
            new TypeReference<Uint256>(true) { },
            new TypeReference<Uint8>() { },
            new TypeReference<Uint8>() { },
            new TypeReference<Bytes32>() { }));

    public static final String STATUS_CHANGED_TOPIC = EventEncoder.encode(STATUS_CHANGED);

    /** {@code ResponseFiled(uint256 indexed complaintId, bytes32 contentHash, string ipfsCid)}. */
    public static final Event RESPONSE_FILED = new Event("ResponseFiled", List.of(
            new TypeReference<Uint256>(true) { },
            new TypeReference<Bytes32>() { },
            new TypeReference<Utf8String>() { }));

    public static final String RESPONSE_FILED_TOPIC = EventEncoder.encode(RESPONSE_FILED);

    private ComplaintRegistryAbi() {
    }
}
