import { enlaceTx } from "@/lib/config";

/** Hash de una transacción, enlazado al explorador de bloques si hay uno configurado (Anvil no tiene). */
export function Tx({ hash }: { hash: string | null }) {
  if (!hash) {
    return <span className="tenue">sin anclar</span>;
  }
  const enlace = enlaceTx(hash);
  return enlace ? (
    <a href={enlace} target="_blank" rel="noreferrer">
      <code>{hash}</code>
    </a>
  ) : (
    <code>{hash}</code>
  );
}
