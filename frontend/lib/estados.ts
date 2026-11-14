// La máquina de estados de ComplaintRegistry.sol (y de EstadoQueja en el backend). El backend y el contrato la
// validan igual: acá solo sirve para ofrecer al funcionario los pasos posibles.
export const ESTADOS = ["REGISTRADA", "EN_REVISION", "DERIVADA", "RESUELTA", "DESESTIMADA"] as const;
export type Estado = (typeof ESTADOS)[number];

const TRANSICIONES: Record<Estado, Estado[]> = {
  REGISTRADA: ["EN_REVISION"],
  EN_REVISION: ["DERIVADA"],
  DERIVADA: ["RESUELTA", "DESESTIMADA"],
  RESUELTA: [],
  DESESTIMADA: [],
};

export function siguientes(estado: string): Estado[] {
  return (ESTADOS as readonly string[]).includes(estado) ? TRANSICIONES[estado as Estado] : [];
}

export const NOMBRES: Record<Estado, string> = {
  REGISTRADA: "Registrada",
  EN_REVISION: "En revisión",
  DERIVADA: "Derivada",
  RESUELTA: "Resuelta",
  DESESTIMADA: "Desestimada",
};
