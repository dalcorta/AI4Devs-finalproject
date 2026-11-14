import type { Address } from "viem";

// Next solo incrusta las variables NEXT_PUBLIC_* escritas literalmente: por eso no se leen en un bucle.
export const config = {
  rpcUrl: process.env.NEXT_PUBLIC_RPC_URL ?? "http://127.0.0.1:8545",
  contrato: (process.env.NEXT_PUBLIC_CONTRACT_ADDRESS ?? "0x5FbDB2315678afecb367f032d93F642f64180aa3") as Address,
  ipfsGateway: process.env.NEXT_PUBLIC_IPFS_GATEWAY ?? "http://127.0.0.1:8080",
  explorerTxUrl: process.env.NEXT_PUBLIC_EXPLORER_TX_URL ?? "",
};

/** Enlace al explorador de bloques, o null si no hay (como en Anvil). */
export function enlaceTx(txHash: string): string | null {
  return config.explorerTxUrl ? config.explorerTxUrl.replace("{tx}", txHash) : null;
}
