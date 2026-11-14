import { type Address, BaseError, ContractFunctionRevertedError, createPublicClient, http } from "viem";
import type { Anclaje } from "./verify";

// Solo lo que el frontend lee de ComplaintRegistry.sol. Lo contrasta con el contrato compilado tests/chain.test.ts.
export const complaintRegistryAbi = [
  {
    type: "function",
    name: "getComplaint",
    stateMutability: "view",
    inputs: [{ name: "id", type: "uint256" }],
    outputs: [
      {
        type: "tuple",
        components: [
          { name: "contentHash", type: "bytes32" },
          { name: "ipfsCid", type: "string" },
          { name: "companyCuit", type: "uint64" },
          { name: "timestamp", type: "uint64" },
          { name: "status", type: "uint8" },
        ],
      },
    ],
  },
  {
    type: "error",
    name: "ComplaintNotFound",
    inputs: [{ name: "id", type: "uint256" }],
  },
] as const;

export function crearLectorDeAnclajes(config: { rpcUrl: string; contrato: Address }) {
  const cliente = createPublicClient({ transport: http(config.rpcUrl, { retryCount: 1 }) });

  return async function leerAnclaje(onchainId: bigint): Promise<Anclaje | null> {
    try {
      const queja = await cliente.readContract({
        address: config.contrato,
        abi: complaintRegistryAbi,
        functionName: "getComplaint",
        args: [onchainId],
      });
      return { contentHash: queja.contentHash, ipfsCid: queja.ipfsCid };
    } catch (error) {
      // Solo un revert ComplaintNotFound significa "no existe"; cualquier otro error (red, RPC) se propaga.
      if (error instanceof BaseError) {
        const revert = error.walk((e) => e instanceof ContractFunctionRevertedError);
        if (revert instanceof ContractFunctionRevertedError && revert.data?.errorName === "ComplaintNotFound") {
          return null;
        }
      }
      throw error;
    }
  };
}

/** Descarga el documento tal cual lo sirve el gateway; la verificación recalcula su hash. */
export function crearLectorDeIpfs(gateway: string) {
  return async function leerIpfs(cid: string): Promise<string> {
    const respuesta = await fetch(`${gateway.replace(/\/$/, "")}/ipfs/${encodeURIComponent(cid)}`);
    if (!respuesta.ok) {
      throw new Error(`El gateway de IPFS respondió ${respuesta.status}`);
    }
    return respuesta.text();
  };
}
