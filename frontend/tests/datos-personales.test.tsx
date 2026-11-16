import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AvisoDatosPersonales } from "@/components/AvisoDatosPersonales";
import { type AvisoDeDatosPersonales, DatosPersonalesDetectados, ErrorDeApi, api } from "@/lib/api";

afterEach(() => vi.unstubAllGlobals());

const AVISO: AvisoDeDatosPersonales = {
  campo: "descripcion",
  revisionCompleta: true,
  hallazgos: [
    { tipo: "NOMBRE", fragmento: "Juan Pérez", inicio: 11, fin: 21 },
    { tipo: "DNI", fragmento: "30.123.456", inicio: 36, fin: 46 },
  ],
};

const QUEJA = {
  cuitEmpresa: "30712345671",
  razonSocial: "Empresa S.A.",
  categoria: "telecomunicaciones",
  descripcion: "Me atendió Juan Pérez.",
  montoReclamado: null,
  fechaHecho: "2026-11-01",
};

describe("cliente de la API: datos personales (HU-06)", () => {
  it("registra la queja y devuelve su id; sin confirmar, el pedido no confirma", async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({ id: "q-1", anchorStatus: "PENDIENTE" }), { status: 202 }));
    vi.stubGlobal("fetch", fetch);

    expect(await api.registrarQueja("token-x", QUEJA, false)).toBe("q-1");
    expect(fetch).toHaveBeenCalledWith("/api/quejas", {
      method: "POST",
      headers: { Authorization: "Bearer token-x", "Content-Type": "application/json" },
      body: JSON.stringify({ ...QUEJA, confirmarDatosPersonales: false }),
    });
  });

  it("un 422 trae los hallazgos para mostrárselos al autor", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ mensaje: "Parece tener datos", datosPersonales: AVISO }), { status: 422 })),
    );

    const error = await api.registrarQueja("t", QUEJA, false).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(DatosPersonalesDetectados);
    expect((error as DatosPersonalesDetectados).aviso).toEqual(AVISO);
    expect((error as DatosPersonalesDetectados).message).toBe("Parece tener datos");
  });

  it("la respuesta de la empresa manda la confirmación cuando el autor la da", async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({ id: "r-1" }), { status: 202 }));
    vi.stubGlobal("fetch", fetch);

    await api.responder("t", "q-1", "Texto.", true);

    expect(fetch).toHaveBeenCalledWith("/api/quejas/q-1/respuesta", expect.objectContaining({
      body: JSON.stringify({ texto: "Texto.", confirmarDatosPersonales: true }),
    }));
  });

  it("un 422 sin detalle de datos personales es un error común", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ mensaje: "Otro" }), { status: 422 })));

    const error = await api.responder("t", "q", "x").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error).not.toBeInstanceOf(DatosPersonalesDetectados);
  });
});

describe("AvisoDatosPersonales", () => {
  it("muestra cada fragmento con su tipo y ofrece corregir o publicar igual", () => {
    const corregir = vi.fn();
    const publicar = vi.fn();
    render(<AvisoDatosPersonales aviso={AVISO} alCorregir={corregir} alPublicarIgual={publicar} />);

    expect(screen.getByRole("alert").textContent).toContain("permanente");
    expect(screen.getByText("Juan Pérez")).toBeDefined();
    expect(screen.getByText("Nombre de una persona")).toBeDefined();
    expect(screen.getByText("30.123.456")).toBeDefined();
    expect(screen.getByText("DNI")).toBeDefined();
    expect(screen.queryByText(/no se pudo revisar/i)).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Corregir el texto" }));
    fireEvent.click(screen.getByRole("button", { name: "Publicar igual" }));
    expect(corregir).toHaveBeenCalledOnce();
    expect(publicar).toHaveBeenCalledOnce();
  });

  it("si no se pudieron revisar los nombres, lo dice aunque no haya hallazgos", () => {
    render(
      <AvisoDatosPersonales
        aviso={{ campo: "descripcion", revisionCompleta: false, hallazgos: [] }}
        alCorregir={() => undefined}
        alPublicarIgual={() => undefined}
      />,
    );

    expect(screen.getByText(/no se pudo revisar si hay nombres de personas/i)).toBeDefined();
    expect(screen.getByRole("button", { name: "Publicar igual" })).toBeDefined();
  });
});
