import { describe, expect, it } from "vitest";
import { ESTADOS, siguientes } from "@/lib/estados";

// La misma máquina de estados que ComplaintRegistry.sol y EstadoQueja en el backend.
describe("máquina de estados de la queja", () => {
  it("ofrece solo las transiciones del contrato", () => {
    expect(siguientes("REGISTRADA")).toEqual(["EN_REVISION"]);
    expect(siguientes("EN_REVISION")).toEqual(["DERIVADA"]);
    expect(siguientes("DERIVADA")).toEqual(["RESUELTA", "DESESTIMADA"]);
    expect(siguientes("RESUELTA")).toEqual([]);
    expect(siguientes("DESESTIMADA")).toEqual([]);
  });

  it("tiene los estados en el orden del enum del contrato", () => {
    expect(ESTADOS).toEqual(["REGISTRADA", "EN_REVISION", "DERIVADA", "RESUELTA", "DESESTIMADA"]);
  });

  it("un estado desconocido no ofrece transiciones", () => {
    expect(siguientes("INVENTADO")).toEqual([]);
  });
});
