"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BadgeVerificacion } from "@/components/BadgeVerificacion";
import { useVerificacion } from "@/components/useVerificacion";
import { api, NoEncontrado, type QuejaDetalle } from "@/lib/api";
import { enlaceTx } from "@/lib/config";

type Documento = {
  empresa?: { cuit?: string; razon_social?: string };
  queja?: { categoria?: string; descripcion?: string; fecha_hecho?: string; monto_reclamado_ars?: number };
  registrada_en?: string;
};

function Tx({ hash }: { hash: string | null }) {
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

export default function DetalleDeQueja() {
  const { id } = useParams<{ id: string }>();
  const [queja, setQueja] = useState<QuejaDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { resultado, reintentar } = useVerificacion(queja);

  useEffect(() => {
    let vigente = true;
    api
      .queja(id)
      .then((q) => vigente && setQueja(q))
      .catch((e) => vigente && setError(e instanceof NoEncontrado ? "No existe esa queja." : "No se pudo cargar la queja."));
    return () => {
      vigente = false;
    };
  }, [id]);

  if (error) {
    return <p className="alerta alerta-error">{error}</p>;
  }
  if (queja === null) {
    return <p className="tenue">Cargando…</p>;
  }

  // Solo se muestra contenido que pasó la verificación: una versión manipulada nunca aparece como auténtica.
  const documento = resultado.estado === "VERIFICADA" ? (resultado.documento as Documento) : null;

  return (
    <>
      <h1>Queja</h1>
      <p>
        <BadgeVerificacion resultado={resultado} />
      </p>
      {resultado.motivo && <p className={resultado.estado === "COMPROMETIDA" ? "alerta alerta-error" : "tenue"}>{resultado.motivo}</p>}
      {resultado.estado === "NO_VERIFICABLE" && (
        <button className="secundario" onClick={reintentar}>
          Reintentar la verificación
        </button>
      )}

      {documento && (
        <section className="panel">
          <dl className="datos">
            <dt>Empresa</dt>
            <dd>
              {documento.empresa?.razon_social} (CUIT {documento.empresa?.cuit})
            </dd>
            <dt>Categoría</dt>
            <dd>{documento.queja?.categoria}</dd>
            <dt>Fecha del hecho</dt>
            <dd>{documento.queja?.fecha_hecho}</dd>
            {documento.queja?.monto_reclamado_ars !== undefined && (
              <>
                <dt>Monto reclamado</dt>
                <dd>$ {documento.queja.monto_reclamado_ars.toLocaleString("es-AR")}</dd>
              </>
            )}
            <dt>Descripción</dt>
            <dd>{documento.queja?.descripcion}</dd>
            <dt>Registrada</dt>
            <dd>{documento.registrada_en}</dd>
          </dl>
        </section>
      )}
      {resultado.estado === "PENDIENTE" && (
        <p className="tenue">El contenido se va a poder leer y verificar cuando termine el anclaje en la blockchain.</p>
      )}

      <h2>Datos de verificación</h2>
      <dl className="datos">
        <dt>Id en la blockchain</dt>
        <dd>{queja.onchainId ?? <span className="tenue">sin anclar</span>}</dd>
        <dt>CID en IPFS</dt>
        <dd>{queja.ipfsCid ? <code>{queja.ipfsCid}</code> : <span className="tenue">sin publicar</span>}</dd>
        <dt>Hash del contenido</dt>
        <dd>{queja.contentHash ? <code>{queja.contentHash}</code> : <span className="tenue">sin calcular</span>}</dd>
        <dt>Estado</dt>
        <dd>{queja.estado}</dd>
      </dl>

      <h2>Historial de estados</h2>
      {queja.historialEstados.length === 0 ? (
        <p className="tenue">Todavía no tuvo cambios de estado.</p>
      ) : (
        <ul className="lista">
          {queja.historialEstados.map((c) => (
            <li key={`${c.createdAt}-${c.estadoNuevo}`}>
              <span>
                {c.estadoAnterior} → {c.estadoNuevo} <span className="tenue">· {new Date(c.createdAt).toLocaleString("es-AR")}</span>
              </span>
              <Tx hash={c.txHash} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
