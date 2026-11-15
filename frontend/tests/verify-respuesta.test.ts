import { describe, expect, it, vi } from "vitest";
import vectores from "../../shared/test-vectors/canonical-hash.json";
import { type Anclaje, type DependenciasDeRespuesta, verificarRespuesta } from "@/lib/verify";

const vector = vectores.vectors.find((v) => v.name === "respuesta_empresa_ejemplo")!;
const DOCUMENTO = vector.input;
const HASH = vector.keccak256;
const CID = "bafy-respuesta-on-chain";
// El documento de ejemplo responde a la queja on-chain 1, cuyo hash es el del vector de la queja.
const HASH_QUEJA = vectores.vectors.find((v) => v.name === "queja_publica_ejemplo")!.keccak256;

function dependencias(respuesta: Anclaje | null, contenido: string = DOCUMENTO, hashQueja: string = HASH_QUEJA) {
  return {
    leerRespuesta: vi.fn(async () => respuesta),
    leerAnclaje: vi.fn(async () => ({ contentHash: hashQueja, ipfsCid: "bafy-queja" })),
    leerIpfs: vi.fn(async () => contenido),
  } satisfies DependenciasDeRespuesta;
}

const anclada = { contentHash: HASH, ipfsCid: CID };

describe("verificarRespuesta", () => {
  it("una queja sin anclar no puede tener respuesta y no consulta nada", async () => {
    const deps = dependencias(null);

    expect((await verificarRespuesta({ onchainId: null, respuesta: null }, deps)).estado).toBe("SIN_RESPUESTA");
    expect(deps.leerRespuesta).not.toHaveBeenCalled();
  });

  it("verifica la respuesta con el CID y el hash del contrato, y solo entonces trae el documento", async () => {
    const deps = dependencias(anclada);

    const resultado = await verificarRespuesta({ onchainId: 1, respuesta: { anchorStatus: "CONFIRMADA" } }, deps);

    expect(resultado.estado).toBe("VERIFICADA");
    expect(resultado.documento).toEqual(JSON.parse(DOCUMENTO));
    expect(deps.leerRespuesta).toHaveBeenCalledWith(1n);
    expect(deps.leerIpfs).toHaveBeenCalledWith(CID);
  });

  it("muestra la respuesta anclada aunque el sitio diga que no hay: el backend no puede ocultarla", async () => {
    const resultado = await verificarRespuesta({ onchainId: 1, respuesta: null }, dependencias(anclada));

    expect(resultado.estado).toBe("VERIFICADA");
  });

  it("sin respuesta en la cadena ni en el sitio, no hay respuesta", async () => {
    const deps = dependencias(null);

    expect((await verificarRespuesta({ onchainId: 1, respuesta: null }, deps)).estado).toBe("SIN_RESPUESTA");
    expect(deps.leerIpfs).not.toHaveBeenCalled();
  });

  it("una respuesta que el sitio todavía está anclando queda pendiente", async () => {
    for (const anchorStatus of ["PENDIENTE", "ENVIADA"]) {
      const resultado = await verificarRespuesta({ onchainId: 1, respuesta: { anchorStatus } }, dependencias(null));

      expect(resultado.estado).toBe("PENDIENTE");
    }
  });

  it("si el sitio dice que está confirmada pero la cadena no la tiene, está comprometida", async () => {
    const resultado = await verificarRespuesta(
      { onchainId: 1, respuesta: { anchorStatus: "CONFIRMADA" } },
      dependencias(null),
    );

    expect(resultado.estado).toBe("COMPROMETIDA");
    expect(resultado.documento).toBeUndefined();
  });

  it("un contenido manipulado está comprometido y no se muestra", async () => {
    const resultado = await verificarRespuesta(
      { onchainId: 1, respuesta: { anchorStatus: "CONFIRMADA" } },
      dependencias(anclada, DOCUMENTO.replace("bonificación", "disculpa")),
    );

    expect(resultado.estado).toBe("COMPROMETIDA");
    expect(resultado.motivo).toMatch(/hash/i);
    expect(resultado.documento).toBeUndefined();
  });

  it("un documento auténtico que referencia otra queja está comprometido", async () => {
    const otraQueja = `0x${"9".repeat(64)}`;

    const deOtraQueja = await verificarRespuesta(
      { onchainId: 1, respuesta: { anchorStatus: "CONFIRMADA" } },
      dependencias(anclada, DOCUMENTO, otraQueja),
    );
    const otroId = await verificarRespuesta(
      { onchainId: 2, respuesta: { anchorStatus: "CONFIRMADA" } },
      dependencias(anclada),
    );

    expect(deOtraQueja.estado).toBe("COMPROMETIDA");
    expect(deOtraQueja.motivo).toMatch(/otra queja/i);
    expect(otroId.estado).toBe("COMPROMETIDA");
  });

  it("si la cadena o IPFS no responden, no se puede verificar (no es evidencia de manipulación)", async () => {
    const cadenaCaida = dependencias(anclada);
    cadenaCaida.leerRespuesta.mockRejectedValue(new Error("RPC caído"));
    const ipfsCaido = dependencias(anclada);
    ipfsCaido.leerIpfs.mockRejectedValue(new Error("gateway caído"));
    const datos = { onchainId: 1, respuesta: { anchorStatus: "CONFIRMADA" } };

    expect((await verificarRespuesta(datos, cadenaCaida)).estado).toBe("NO_VERIFICABLE");
    expect((await verificarRespuesta(datos, ipfsCaido)).estado).toBe("NO_VERIFICABLE");
  });
});
