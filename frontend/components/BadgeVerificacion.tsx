import type { Resultado } from "@/lib/verify";

const TEXTOS: Record<Resultado["estado"], { texto: string; clase: string }> = {
  VERIFICANDO: { texto: "Verificando…", clase: "badge badge-neutro" },
  PENDIENTE: { texto: "Pendiente de anclaje", clase: "badge badge-pendiente" },
  VERIFICADA: { texto: "Verificada", clase: "badge badge-ok" },
  COMPROMETIDA: { texto: "Integridad comprometida", clase: "badge badge-error" },
  NO_VERIFICABLE: { texto: "No se pudo verificar", clase: "badge badge-aviso" },
};

export function BadgeVerificacion({ resultado }: { resultado: Resultado }) {
  const { texto, clase } = TEXTOS[resultado.estado];
  return (
    <span className={clase} title={resultado.motivo} role="status">
      {texto}
    </span>
  );
}
