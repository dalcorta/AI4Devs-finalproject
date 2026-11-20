import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const HASH = `0x${"ab".repeat(32)}`;

/** config.ts lee las variables al importarse: cada caso importa el componente con su propio entorno. */
async function tx(explorador: string) {
  vi.resetModules();
  vi.stubEnv("NEXT_PUBLIC_EXPLORER_TX_URL", explorador);
  return (await import("@/components/Tx")).Tx;
}

describe("Tx", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
  });

  it("sin explorador configurado (Anvil) muestra el hash sin enlace", async () => {
    const Tx = await tx("");
    render(<Tx hash={HASH} />);

    expect(screen.getByText(HASH).tagName).toBe("CODE");
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("con explorador, enlaza la transacción reemplazando {tx}", async () => {
    const Tx = await tx("https://amoy.polygonscan.com/tx/{tx}");
    render(<Tx hash={HASH} />);

    expect(screen.getByRole("link").getAttribute("href")).toBe(`https://amoy.polygonscan.com/tx/${HASH}`);
  });

  it("sin hash, dice que todavía no está anclada", async () => {
    const Tx = await tx("");
    render(<Tx hash={null} />);

    expect(screen.getByText("sin anclar")).toBeDefined();
  });
});
