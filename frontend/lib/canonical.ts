import canonicalize from "canonicalize";
import { type Hex, keccak256, toBytes } from "viem";

/** Forma canónica RFC 8785 (JCS) de un texto JSON. Es la misma que calcula el backend antes de anclar. */
export function canonicalizar(json: string): string {
  const canonico = canonicalize(JSON.parse(json));
  if (canonico === undefined) {
    throw new Error("El contenido no se puede canonicalizar");
  }
  return canonico;
}

/** keccak256 de los bytes UTF-8 de la forma canónica: el contentHash que guarda el contrato. */
export function hashCanonico(json: string): Hex {
  return keccak256(toBytes(canonicalizar(json)));
}
