import { type ChildProcess, execFileSync, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { createWriteStream, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import bcrypt from "bcryptjs";
import pg from "pg";
import { type Abi, type Address, type Hex, createPublicClient, createWalletClient, http, keccak256, toBytes } from "viem";
import { privateKeyToAccount } from "viem/accounts";
import { foundry } from "viem/chains";
import {
  ARCHIVO_ESTADO,
  BACKEND_URL,
  BASE_E2E,
  type Estado,
  FRONTEND_URL,
  IPFS_API_URL,
  IPFS_GATEWAY,
  POSTGRES,
  PUERTO_BACKEND,
  PUERTO_FRONTEND,
  RPC_URL,
} from "./entorno";

/**
 * Arma un entorno completo y aislado sobre los servicios de compose: base recreada, contrato recién desplegado,
 * backend y frontend propios en puertos de E2E y una ciudadana verificada. Si falta un requisito, falla con un
 * mensaje que dice qué hacer en lugar de quedarse esperando.
 */

// Claves de desarrollo públicas que Anvil genera por defecto; no valen nada fuera de una cadena local.
const ADMIN = privateKeyToAccount("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
const RELAYER_KEY = "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
const RELAYER = privateKeyToAccount(`0x${RELAYER_KEY}`);

const RAIZ = join(import.meta.dirname, "..", "..");
const FRONTEND = join(RAIZ, "frontend");
const BACKEND = join(RAIZ, "backend");
const ARTEFACTO = join(RAIZ, "contracts", "out", "ComplaintRegistry.sol", "ComplaintRegistry.json");
const LOGS = join(FRONTEND, "test-results", "e2e-logs");
const NEXT = join(FRONTEND, "node_modules", "next", "dist", "bin", "next");

/** Procesos lanzados por el setup: si algo falla a mitad de camino, se detienen antes de propagar el error. */
const lanzados: ChildProcess[] = [];

async function responde(url: string, init?: RequestInit): Promise<boolean> {
  try {
    await fetch(url, { ...init, signal: AbortSignal.timeout(2000) });
    return true;
  } catch {
    return false;
  }
}

async function esperar(descripcion: string, condicion: () => Promise<boolean>, segundos: number, proceso?: ChildProcess) {
  const limite = Date.now() + segundos * 1000;
  while (Date.now() < limite) {
    if (proceso && proceso.exitCode !== null) {
      throw new Error(`${descripcion}: el proceso terminó con código ${proceso.exitCode}. Ver ${LOGS}`);
    }
    if (await condicion()) {
      return;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`${descripcion}: no respondió en ${segundos} s. Ver ${LOGS}`);
}

function lanzar(nombre: string, comando: string, args: string[], cwd: string, env: Record<string, string>) {
  const log = createWriteStream(join(LOGS, `${nombre}.log`));
  const proceso = spawn(comando, args, { cwd, env: { ...process.env, ...env }, stdio: ["ignore", "pipe", "pipe"] });
  proceso.stdout?.pipe(log);
  proceso.stderr?.pipe(log);
  lanzados.push(proceso);
  return proceso;
}

async function exigirServiciosDeCompose() {
  const faltan: string[] = [];
  if (!(await responde(RPC_URL, { method: "POST", body: "{}" }))) faltan.push(`Anvil (${RPC_URL})`);
  if (!(await responde(`${IPFS_API_URL}/api/v0/version`, { method: "POST" }))) faltan.push(`Kubo (${IPFS_API_URL})`);
  if (!(await responde(`${IPFS_GATEWAY}/ipfs/bafkqaaa`))) faltan.push(`gateway de IPFS (${IPFS_GATEWAY})`);
  const db = new pg.Client({ ...POSTGRES, database: "postgres" });
  try {
    await db.connect();
  } catch {
    faltan.push("PostgreSQL (127.0.0.1:5432)");
  } finally {
    await db.end().catch(() => undefined);
  }
  if (faltan.length > 0) {
    throw new Error(`Faltan servicios del entorno local: ${faltan.join(", ")}. Desde la raíz: podman-compose up -d`);
  }
  for (const [puerto, quien] of [
    [PUERTO_BACKEND, "backend"],
    [PUERTO_FRONTEND, "frontend"],
  ] as const) {
    if (await responde(`http://127.0.0.1:${puerto}`)) {
      throw new Error(`El puerto ${puerto} (${quien} de los E2E) ya está en uso`);
    }
  }
}

async function recrearBase() {
  const db = new pg.Client({ ...POSTGRES, database: "postgres" });
  await db.connect();
  await db.query(`DROP DATABASE IF EXISTS ${BASE_E2E} WITH (FORCE)`);
  await db.query(`CREATE DATABASE ${BASE_E2E}`);
  await db.end();
}

async function desplegarContrato(): Promise<{ contrato: Address; bloque: bigint }> {
  if (!existsSync(ARTEFACTO)) {
    throw new Error("Falta el artefacto del contrato: correr `forge build` en contracts/");
  }
  const { abi, bytecode } = JSON.parse(readFileSync(ARTEFACTO, "utf8")) as { abi: Abi; bytecode: { object: Hex } };
  const publico = createPublicClient({ chain: foundry, transport: http(RPC_URL) });
  const admin = createWalletClient({ account: ADMIN, chain: foundry, transport: http(RPC_URL) });
  const recibo = await publico.waitForTransactionReceipt({
    hash: await admin.deployContract({ abi, bytecode: bytecode.object, args: [ADMIN.address] }),
  });
  const contrato = recibo.contractAddress as Address;
  await publico.waitForTransactionReceipt({
    hash: await admin.writeContract({
      address: contrato,
      abi,
      functionName: "grantRole",
      args: [keccak256(toBytes("RELAYER_ROLE")), RELAYER.address],
    }),
  });
  return { contrato, bloque: recibo.blockNumber };
}

async function crearCiudadana(): Promise<{ email: string; clave: string }> {
  const email = `ciudadana-e2e-${Date.now()}@example.test`;
  const clave = randomBytes(18).toString("base64url");
  const db = new pg.Client({ ...POSTGRES, database: BASE_E2E });
  await db.connect();
  await db.query(
    `INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada)
     VALUES (gen_random_uuid(), $1, $2, 'CIUDADANO', 'Persona Ficticia E2E', true)`,
    [email, await bcrypt.hash(clave, 10)],
  );
  await db.end();
  return { email, clave };
}

export default async function globalSetup() {
  try {
    await armarEntorno();
  } catch (error) {
    lanzados.forEach((p) => p.kill());
    throw error;
  }
}

async function armarEntorno() {
  mkdirSync(LOGS, { recursive: true });
  await exigirServiciosDeCompose();
  await recrearBase();
  const { contrato, bloque } = await desplegarContrato();

  // Se compila el backend actual: los E2E nunca deben correr contra un jar viejo.
  execFileSync("mvn", ["-q", "package", "-DskipTests"], { cwd: BACKEND, stdio: "inherit" });

  // El frontend se compila ANTES de levantar el backend: con 4 GB de RAM, la compilación de Next y la JVM juntas
  // hacían que el OOM killer del sistema matara procesos. Las variables NEXT_PUBLIC_* se incrustan al compilar: por
  // eso el frontend de E2E se compila con este contrato.
  const envFrontend = {
    NEXT_DIST_DIR: ".next-e2e",
    BACKEND_URL,
    NEXT_PUBLIC_RPC_URL: RPC_URL,
    NEXT_PUBLIC_CONTRACT_ADDRESS: contrato,
    NEXT_PUBLIC_IPFS_GATEWAY: IPFS_GATEWAY,
    NEXT_PUBLIC_EXPLORER_TX_URL: "",
  };
  execFileSync(process.execPath, [NEXT, "build"], {
    cwd: FRONTEND,
    env: {
      ...process.env,
      ...envFrontend,
      NEXT_SIN_CHEQUEO_DE_TIPOS: "1",
      // Con un tope propio, si falta memoria falla la compilación y no el resto del sistema.
      NODE_OPTIONS: "--max-old-space-size=1024",
    },
    stdio: "inherit",
  });

  const backend = lanzar("backend", "java", ["-Xmx384m", "-jar", "target/backend-0.1.0-SNAPSHOT.jar"], BACKEND, {
    SERVER_PORT: String(PUERTO_BACKEND),
    DB_URL: `jdbc:postgresql://127.0.0.1:5432/${BASE_E2E}`,
    DB_USER: POSTGRES.user,
    DB_PASSWORD: POSTGRES.password,
    IPFS_API_URL,
    RPC_URL,
    CONTRACT_ADDRESS: contrato,
    RELAYER_PRIVATE_KEY: RELAYER_KEY,
    JWT_SECRET: randomBytes(48).toString("base64"),
    TRIAGE_HABILITADO: "false",
    WORKER_INTERVALO: "PT1S",
    LISTENER_INTERVALO: "PT1S",
    LISTENER_BLOQUE_INICIAL: String(bloque),
  });
  await esperar("Backend de E2E", () => responde(`${BACKEND_URL}/api/empresas?q=ab`), 120, backend);

  // Next se lanza con node directamente (no con npx) para que el pid guardado sea el del servidor.
  const frontend = lanzar(
    "frontend",
    process.execPath,
    [NEXT, "start", "-H", "127.0.0.1", "-p", String(PUERTO_FRONTEND)],
    FRONTEND,
    envFrontend,
  );
  await esperar("Frontend de E2E", () => responde(`${FRONTEND_URL}/`), 60, frontend);

  const ciudadana = await crearCiudadana();
  const estado: Estado = { contrato, ...ciudadana, pids: [backend.pid!, frontend.pid!] };
  writeFileSync(ARCHIVO_ESTADO, JSON.stringify(estado, null, 2));
}
