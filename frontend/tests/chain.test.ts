import { type ChildProcess, spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { createServer } from "node:net";
import { homedir } from "node:os";
import { join } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
  type Abi,
  type Address,
  type Hex,
  createPublicClient,
  createWalletClient,
  http,
  keccak256,
  toBytes,
} from "viem";
import { privateKeyToAccount } from "viem/accounts";
import { foundry } from "viem/chains";
import { crearLectorDeAnclajes } from "@/lib/chain";

/**
 * El ABI del frontend está escrito a mano: este test lo contrasta con el contrato compilado por Forge, desplegado en
 * un Anvil real. Requiere `forge build` en contracts/ y Anvil instalado; si falta, el test falla (no se omite).
 */

// Claves de desarrollo públicas que Anvil genera por defecto; no valen nada fuera de esta cadena local.
const ADMIN = privateKeyToAccount("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
const RELAYER = privateKeyToAccount("0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
const ANVIL = join(homedir(), ".foundry", "bin", "anvil");
const ARTEFACTO = join(__dirname, "..", "..", "contracts", "out", "ComplaintRegistry.sol", "ComplaintRegistry.json");
const HASH: Hex = `0x${"ab".repeat(32)}`;

let anvil: ChildProcess;
let rpcUrl: string;
let contrato: Address;

async function puertoLibre(): Promise<number> {
  return new Promise((resolve) => {
    const servidor = createServer().listen(0, "127.0.0.1", () => {
      const direccion = servidor.address();
      servidor.close(() => resolve(typeof direccion === "object" && direccion ? direccion.port : 0));
    });
  });
}

beforeAll(async () => {
  expect(existsSync(ANVIL), `Anvil no está instalado en ${ANVIL}`).toBe(true);
  expect(existsSync(ARTEFACTO), "Falta el artefacto del contrato: correr `forge build` en contracts/").toBe(true);

  const puerto = await puertoLibre();
  rpcUrl = `http://127.0.0.1:${puerto}`;
  anvil = spawn(ANVIL, ["--port", String(puerto), "--silent"]);
  const publico = createPublicClient({ chain: foundry, transport: http(rpcUrl) });
  for (let i = 0; i < 100; i++) {
    try {
      await publico.getChainId();
      break;
    } catch {
      await new Promise((r) => setTimeout(r, 100));
    }
  }

  const artefacto = JSON.parse(readFileSync(ARTEFACTO, "utf8")) as { abi: Abi; bytecode: { object: Hex } };
  const admin = createWalletClient({ account: ADMIN, chain: foundry, transport: http(rpcUrl) });
  const despliegue = await admin.deployContract({
    abi: artefacto.abi,
    bytecode: artefacto.bytecode.object,
    args: [ADMIN.address],
  });
  contrato = (await publico.waitForTransactionReceipt({ hash: despliegue })).contractAddress as Address;

  const rol = await admin.writeContract({
    address: contrato,
    abi: artefacto.abi,
    functionName: "grantRole",
    args: [keccak256(toBytes("RELAYER_ROLE")), RELAYER.address],
  });
  await publico.waitForTransactionReceipt({ hash: rol });

  const relayer = createWalletClient({ account: RELAYER, chain: foundry, transport: http(rpcUrl) });
  const alta = await relayer.writeContract({
    address: contrato,
    abi: artefacto.abi,
    functionName: "fileComplaint",
    args: [30712345671n, HASH, "bafy-cid-real"],
  });
  await publico.waitForTransactionReceipt({ hash: alta });
}, 60_000);

afterAll(() => {
  anvil?.kill();
});

describe("crearLectorDeAnclajes contra un ComplaintRegistry real", () => {
  it("lee el hash y el CID anclados", async () => {
    const leer = crearLectorDeAnclajes({ rpcUrl, contrato });

    expect(await leer(1n)).toEqual({ contentHash: HASH, ipfsCid: "bafy-cid-real" });
  });

  it("un id que no existe en el contrato devuelve null", async () => {
    const leer = crearLectorDeAnclajes({ rpcUrl, contrato });

    expect(await leer(99n)).toBeNull();
  });

  it("un RPC caído es un error, no un null", async () => {
    const leer = crearLectorDeAnclajes({ rpcUrl: "http://127.0.0.1:1", contrato });

    await expect(leer(1n)).rejects.toThrow();
  });
});
