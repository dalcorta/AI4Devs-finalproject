import { existsSync, rmSync } from "node:fs";
import { ARCHIVO_ESTADO, leerEstado } from "./entorno";

/** Detiene el backend y el frontend de E2E. Los servicios de compose quedan como estaban. */
export default async function globalTeardown() {
  if (!existsSync(ARCHIVO_ESTADO)) {
    return;
  }
  for (const pid of leerEstado().pids) {
    try {
      process.kill(pid);
    } catch {
      // Ya terminó.
    }
  }
  rmSync(ARCHIVO_ESTADO);
}
