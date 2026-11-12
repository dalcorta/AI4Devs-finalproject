package ar.libroabierto.anchoring.infrastructure;

import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;

/** Partes del ABI de ComplaintRegistry.sol que usan los adaptadores Web3j. */
public final class ComplaintRegistryAbi {

    /** {@code ComplaintFiled(uint256 indexed id, uint64 indexed companyCuit, bytes32 contentHash, string ipfsCid)}. */
    public static final Event COMPLAINT_FILED = new Event("ComplaintFiled", List.of(
            new TypeReference<Uint256>(true) { },
            new TypeReference<Uint64>(true) { },
            new TypeReference<Bytes32>() { },
            new TypeReference<Utf8String>() { }));

    public static final String COMPLAINT_FILED_TOPIC = EventEncoder.encode(COMPLAINT_FILED);

    private ComplaintRegistryAbi() {
    }
}
