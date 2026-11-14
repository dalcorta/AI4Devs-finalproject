import { readFileSync } from "node:fs";
import { join } from "node:path";

// Puertos propios de los E2E: no chocan con un backend (8081) o un frontend (3000) de desarrollo abiertos.
export const PUERTO_FRONTEND = 13000;
export const PUERTO_BACKEND = 18081;
export const FRONTEND_URL = `http://127.0.0.1:${PUERTO_FRONTEND}`;
export const BACKEND_URL = `http://127.0.0.1:${PUERTO_BACKEND}`;

// Servicios del compose.yaml (docker compose up -d / podman-compose up -d).
export const RPC_URL = process.env.E2E_RPC_URL ?? "http://127.0.0.1:8545";
export const IPFS_API_URL = process.env.E2E_IPFS_API_URL ?? "http://127.0.0.1:5001";
export const IPFS_GATEWAY = process.env.E2E_IPFS_GATEWAY ?? "http://127.0.0.1:8080";
export const POSTGRES = {
  host: "127.0.0.1",
  port: 5432,
  user: process.env.E2E_DB_USER ?? "libroabierto",
  password: process.env.E2E_DB_PASSWORD ?? "libroabierto-dev",
};
/** Base propia de los E2E, recreada en cada corrida: no toca la de desarrollo. */
export const BASE_E2E = "libroabierto_e2e";

export const ARCHIVO_ESTADO = join(import.meta.dirname, ".estado.json");

export type Estado = {
  contrato: string;
  email: string;
  clave: string;
  pids: number[];
};

export function leerEstado(): Estado {
  return JSON.parse(readFileSync(ARCHIVO_ESTADO, "utf8")) as Estado;
}
