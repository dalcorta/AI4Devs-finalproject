import type { AvisoDeDatosPersonales, Hallazgo } from "@/lib/api";

const TIPOS: Record<Hallazgo["tipo"], string> = {
  NOMBRE: "Nombre de una persona",
  DNI: "DNI",
  CUIL: "CUIL",
  EMAIL: "Email",
  TELEFONO: "Teléfono",
  TARJETA: "Número de tarjeta",
  CBU: "CBU",
};

/**
 * HU-06: lo que se encontró antes de publicar. El texto todavía no se guardó; quien escribe decide si lo corrige o lo
 * publica igual. La revisión puede equivocarse en los dos sentidos, por eso se muestra como "parece".
 */
export function AvisoDatosPersonales({
  aviso,
  alCorregir,
  alPublicarIgual,
  enviando = false,
}: {
  aviso: AvisoDeDatosPersonales;
  alCorregir: () => void;
  alPublicarIgual: () => void;
  enviando?: boolean;
}) {
  return (
    <div className="alerta alerta-aviso aviso-datos" role="alert">
      <p>
        {aviso.hallazgos.length > 0
          ? "El texto parece incluir datos personales. Quitalos antes de publicar: "
          : "Revisá el texto antes de publicar: "}
        la publicación es <strong>permanente</strong> y no se puede borrar.
      </p>
      {aviso.hallazgos.length > 0 && (
        <ul className="hallazgos">
          {aviso.hallazgos.map((h) => (
            <li key={`${h.inicio}-${h.fin}`}>
              <span className="tenue">{TIPOS[h.tipo] ?? h.tipo}</span>: <mark>{h.fragmento}</mark>
            </li>
          ))}
        </ul>
      )}
      {!aviso.revisionCompleta && (
        <p>
          No se pudo revisar si hay nombres de personas (el revisor automático no está disponible). Fijate vos que no
          haya nombres tuyos ni de terceros.
        </p>
      )}
      <div className="acciones">
        <button type="button" onClick={alCorregir}>
          Corregir el texto
        </button>
        <button type="button" className="secundario" onClick={alPublicarIgual} disabled={enviando}>
          Publicar igual
        </button>
      </div>
    </div>
  );
}
