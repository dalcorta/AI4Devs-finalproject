"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BadgeVerificacion } from "@/components/BadgeVerificacion";
import { useVerificacion } from "@/components/useVerificacion";
import { api, NoEncontrado, type QuejaResumen, TAMANIO_PAGINA } from "@/lib/api";

function FilaQueja({ queja }: { queja: QuejaResumen }) {
  const { resultado } = useVerificacion(queja);
  return (
    <li>
      <span>
        <Link href={`/quejas/${queja.id}`}>{queja.categoria}</Link>{" "}
        <span className="tenue">· hecho del {queja.fechaHecho}</span>
      </span>
      <BadgeVerificacion resultado={resultado} />
    </li>
  );
}

export default function QuejasDeEmpresa() {
  const { cuit } = useParams<{ cuit: string }>();
  const [pagina, setPagina] = useState(0);
  const [quejas, setQuejas] = useState<QuejaResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    setQuejas(null);
    setError(null);
    api
      .quejasDeEmpresa(cuit, pagina)
      .then((r) => vigente && setQuejas(r.items))
      .catch((e) => {
        if (vigente) {
          setError(e instanceof NoEncontrado ? "No hay una empresa registrada con ese CUIT." : "No se pudo cargar el listado.");
        }
      });
    return () => {
      vigente = false;
    };
  }, [cuit, pagina]);

  return (
    <>
      <h1>Quejas contra CUIT {cuit}</h1>
      <p className="tenue">El estado de cada queja lo calcula tu navegador contra IPFS y la blockchain.</p>
      {error && <p className="alerta alerta-error">{error}</p>}
      {quejas === null && !error && <p className="tenue">Cargando…</p>}
      {quejas !== null && quejas.length === 0 && <p className="tenue">Esta empresa no tiene quejas registradas.</p>}
      {quejas !== null && quejas.length > 0 && (
        <ul className="lista">
          {quejas.map((q) => (
            <FilaQueja key={q.id} queja={q} />
          ))}
        </ul>
      )}
      <div className="paginacion">
        <button className="secundario" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
          Anteriores
        </button>
        <button
          className="secundario"
          disabled={quejas === null || quejas.length < TAMANIO_PAGINA}
          onClick={() => setPagina((p) => p + 1)}
        >
          Siguientes
        </button>
      </div>
    </>
  );
}
