import type { Sesion } from "./api";

// sessionStorage y no localStorage: la sesión del funcionario termina al cerrar la pestaña.
const CLAVE = "libroabierto.sesion";

export function guardarSesion(sesion: Sesion) {
  sessionStorage.setItem(CLAVE, JSON.stringify(sesion));
}

export function leerSesion(): Sesion | null {
  try {
    const guardada = sessionStorage.getItem(CLAVE);
    return guardada ? (JSON.parse(guardada) as Sesion) : null;
  } catch {
    return null;
  }
}

export function cerrarSesion() {
  sessionStorage.removeItem(CLAVE);
}
