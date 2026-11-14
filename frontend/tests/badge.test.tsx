import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BadgeVerificacion } from "@/components/BadgeVerificacion";

describe("BadgeVerificacion", () => {
  it.each([
    ["VERIFICADA", "Verificada"],
    ["PENDIENTE", "Pendiente de anclaje"],
    ["COMPROMETIDA", "Integridad comprometida"],
    ["NO_VERIFICABLE", "No se pudo verificar"],
    ["VERIFICANDO", "Verificando…"],
  ] as const)("%s se muestra como «%s»", (estado, texto) => {
    render(<BadgeVerificacion resultado={{ estado }} />);

    expect(screen.getByText(texto)).toBeDefined();
  });

  it("el motivo de un problema queda disponible como descripción", () => {
    render(<BadgeVerificacion resultado={{ estado: "COMPROMETIDA", motivo: "El hash no coincide" }} />);

    expect(screen.getByText("Integridad comprometida").closest("[title]")?.getAttribute("title")).toBe(
      "El hash no coincide",
    );
  });
});
