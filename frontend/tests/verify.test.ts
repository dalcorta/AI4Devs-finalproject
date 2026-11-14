import { describe, expect, it, vi } from "vitest";
import vectores from "../../shared/test-vectors/canonical-hash.json";
import { type Anclaje, type Dependencias, verificarQueja } from "@/lib/verify";

const vector = vectores.vectors[0];
const DOCUMENTO = vector.input; // Con espacios y saltos: lo que importa es su forma canónica.
const HASH = vector.keccak256;
const CID = "bafy-cid-on-chain";

function dependencias(anclaje: Anclaje | null, contenido: string = DOCUMENTO) {
  return {
    leerAnclaje: vi.fn(async () => anclaje),
    leerIpfs: vi.fn(async () => contenido),
  } satisfies Dependencias;
}

const anclada = { onchainId: 1, ipfsCid: CID, contentHash: HASH };

describe("verificarQueja", () => {
  it("una queja sin onchainId está pendiente de anclaje y no consulta nada", async () => {
    const deps = dependencias(null);

    const resultado = await verificarQueja({ onchainId: null, ipfsCid: null, contentHash: null }, deps);

    expect(resultado.estado).toBe("PENDIENTE");
    expect(deps.leerAnclaje).not.toHaveBeenCalled();
    expect(deps.leerIpfs).not.toHaveBeenCalled();
  });

  it("verifica el contenido descargado con el CID de la cadena contra el hash de la cadena", async () => {
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH });

    const resultado = await verificarQueja(anclada, deps);

    expect(resultado.estado).toBe("VERIFICADA");
    expect(deps.leerAnclaje).toHaveBeenCalledWith(1n);
    expect(deps.leerIpfs).toHaveBeenCalledWith(CID);
  });

  it("solo una queja verificada trae el documento, para mostrar únicamente contenido auténtico", async () => {
    const verificada = await verificarQueja(anclada, dependencias({ ipfsCid: CID, contentHash: HASH }));
    const manipulada = await verificarQueja(
      anclada,
      dependencias({ ipfsCid: CID, contentHash: HASH }, DOCUMENTO.replace("15 días", "2 días")),
    );

    expect(verificada.documento).toEqual(JSON.parse(DOCUMENTO));
    expect(manipulada.documento).toBeUndefined();
  });

  it("si el contenido servido fue manipulado, la integridad está comprometida", async () => {
    const manipulado = DOCUMENTO.replace("15 días", "2 días");
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH }, manipulado);

    const resultado = await verificarQueja(anclada, deps);

    expect(resultado.estado).toBe("COMPROMETIDA");
    expect(resultado.motivo).toMatch(/hash/i);
  });

  it("el CID que informa el backend no se usa: el contenido se baja con el de la cadena", async () => {
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH });

    const resultado = await verificarQueja({ ...anclada, ipfsCid: "bafy-cid-del-backend" }, deps);

    expect(resultado.estado).toBe("COMPROMETIDA");
    expect(deps.leerIpfs).not.toHaveBeenCalledWith("bafy-cid-del-backend");
  });

  it("si el hash que informa el backend no es el de la cadena, la integridad está comprometida", async () => {
    const otro = `0x${"1".repeat(64)}`;
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH });

    const resultado = await verificarQueja({ ...anclada, contentHash: otro }, deps);

    expect(resultado.estado).toBe("COMPROMETIDA");
  });

  it("el hash se compara sin distinguir mayúsculas", async () => {
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH.toUpperCase().replace("0X", "0x") });

    expect((await verificarQueja(anclada, deps)).estado).toBe("VERIFICADA");
  });

  it("si la cadena no tiene una queja con ese id, la integridad está comprometida", async () => {
    const deps = dependencias(null);

    const resultado = await verificarQueja(anclada, deps);

    expect(resultado.estado).toBe("COMPROMETIDA");
    expect(deps.leerIpfs).not.toHaveBeenCalled();
  });

  it("un contenido que no es JSON está comprometido", async () => {
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH }, "<html>no es la queja</html>");

    expect((await verificarQueja(anclada, deps)).estado).toBe("COMPROMETIDA");
  });

  it("si IPFS no responde, no se pudo verificar: no es lo mismo que comprometida", async () => {
    const deps = dependencias({ ipfsCid: CID, contentHash: HASH });
    deps.leerIpfs.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    expect((await verificarQueja(anclada, deps)).estado).toBe("NO_VERIFICABLE");
  });

  it("si la cadena no responde, no se pudo verificar", async () => {
    const deps = dependencias(null);
    deps.leerAnclaje.mockRejectedValueOnce(new Error("RPC caído"));

    expect((await verificarQueja(anclada, deps)).estado).toBe("NO_VERIFICABLE");
  });
});
