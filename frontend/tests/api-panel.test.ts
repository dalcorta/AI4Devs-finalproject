import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorDeApi, NoAutorizado, api } from "@/lib/api";

function responder(status: number, cuerpo?: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status })),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("cliente de la API del panel", () => {
  it("manda el token y el cambio de estado", async () => {
    const fetch = vi.fn(async () => new Response(null, { status: 202 }));
    vi.stubGlobal("fetch", fetch);

    await api.cambiarEstado("token-x", "id-1", "EN_REVISION", "Se pidió documentación.");

    expect(fetch).toHaveBeenCalledWith("/api/quejas/id-1/estado", {
      method: "PATCH",
      headers: { Authorization: "Bearer token-x", "Content-Type": "application/json" },
      body: JSON.stringify({ estado: "EN_REVISION", comentario: "Se pidió documentación." }),
    });
  });

  it("un 409 trae el motivo que explica el backend", async () => {
    responder(409, { mensaje: "No se puede pasar de REGISTRADA a RESUELTA" });

    await expect(api.cambiarEstado("t", "id", "RESUELTA", "")).rejects.toEqual(
      new ErrorDeApi("No se puede pasar de REGISTRADA a RESUELTA"),
    );
  });

  it("un 401 o 403 es una sesión no autorizada", async () => {
    responder(401);
    await expect(api.bandeja("vencido", null)).rejects.toBeInstanceOf(NoAutorizado);
    responder(403);
    await expect(api.bandeja("de-ciudadano", null)).rejects.toBeInstanceOf(NoAutorizado);
  });

  it("un login rechazado no es un error de red", async () => {
    responder(401, { mensaje: "Email o contraseña incorrectos" });

    await expect(api.login("a@example.test", "x")).rejects.toEqual(new ErrorDeApi("Email o contraseña incorrectos"));
  });
});
