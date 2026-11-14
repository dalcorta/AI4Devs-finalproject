import { hashCanonico } from "./canonical";

/** Lo que el contrato tiene anclado para una queja. */
export type Anclaje = { contentHash: string; ipfsCid: string };

export type Dependencias = {
  /** null si el contrato no tiene una queja con ese id; un error si la cadena no respondió. */
  leerAnclaje: (onchainId: bigint) => Promise<Anclaje | null>;
  leerIpfs: (cid: string) => Promise<string>;
};

/** Lo que dice el backend. Solo se usa onchainId para saber qué leer; lo demás se contrasta con la cadena. */
export type DatosDelBackend = { onchainId: number | null; ipfsCid: string | null; contentHash: string | null };

export type EstadoVerificacion = "VERIFICANDO" | "PENDIENTE" | "VERIFICADA" | "COMPROMETIDA" | "NO_VERIFICABLE";
/** documento: el contenido descargado y parseado, presente solo si quedó VERIFICADA. */
export type Resultado = { estado: EstadoVerificacion; motivo?: string; documento?: unknown };

const iguales = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

/**
 * Verificación de integridad en el navegador, sin confiar en el backend: el CID y el hash salen del contrato, el
 * contenido se descarga de IPFS con ese CID y su hash canónico se recalcula acá.
 *
 * NO_VERIFICABLE (IPFS o la cadena no respondieron) es distinto de COMPROMETIDA: un corte de red no es evidencia de
 * que alguien haya alterado una queja.
 */
export async function verificarQueja(datos: DatosDelBackend, deps: Dependencias): Promise<Resultado> {
  if (datos.onchainId === null) {
    return { estado: "PENDIENTE" };
  }

  let anclaje: Anclaje | null;
  try {
    anclaje = await deps.leerAnclaje(BigInt(datos.onchainId));
  } catch {
    return { estado: "NO_VERIFICABLE", motivo: "No se pudo leer la blockchain" };
  }
  if (anclaje === null) {
    return { estado: "COMPROMETIDA", motivo: "La blockchain no tiene una queja con ese identificador" };
  }
  if (datos.contentHash !== null && !iguales(datos.contentHash, anclaje.contentHash)) {
    return { estado: "COMPROMETIDA", motivo: "El hash que informa el sitio no es el anclado en la blockchain" };
  }
  if (datos.ipfsCid !== null && datos.ipfsCid !== anclaje.ipfsCid) {
    return { estado: "COMPROMETIDA", motivo: "El CID que informa el sitio no es el anclado en la blockchain" };
  }

  let contenido: string;
  try {
    contenido = await deps.leerIpfs(anclaje.ipfsCid);
  } catch {
    return { estado: "NO_VERIFICABLE", motivo: "No se pudo descargar el contenido de IPFS" };
  }

  let calculado: string;
  try {
    calculado = hashCanonico(contenido);
  } catch {
    return { estado: "COMPROMETIDA", motivo: "El contenido descargado no es un documento JSON válido" };
  }
  if (!iguales(calculado, anclaje.contentHash)) {
    return { estado: "COMPROMETIDA", motivo: "El hash del contenido no coincide con el anclado en la blockchain" };
  }
  return { estado: "VERIFICADA", documento: JSON.parse(contenido) };
}
