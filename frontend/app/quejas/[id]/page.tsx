"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BadgeVerificacion } from "@/components/BadgeVerificacion";
import { Tx } from "@/components/Tx";
import { useVerificacion, useVerificacionDeRespuesta } from "@/components/useVerificacion";
import { api, NoEncontrado, type QuejaDetalle } from "@/lib/api";

type Documento = {
  empresa?: { cuit?: string; razon_social?: string };
  queja?: { categoria?: string; descripcion?: string; fecha_hecho?: string; monto_reclamado_ars?: number };
  registrada_en?: string;
};

type DocumentoDeRespuesta = { respuesta?: { texto?: string }; respondida_en?: string };

/** La respuesta pública de la empresa: se muestra solo si su contenido quedó verificado contra la cadena. */
function RespuestaDeLaEmpresa({ queja }: { queja: QuejaDetalle }) {
  const { resultado, reintentar } = useVerificacionDeRespuesta(queja);

  if (resultado.estado === "SIN_RESPUESTA") {
    return <p className="tenue">La empresa todavía no respondió.</p>;
  }
  const documento = resultado.estado === "VERIFICADA" ? (resultado.documento as DocumentoDeRespuesta) : null;
  return (
    <section aria-label="Respuesta de la empresa">
      <p>
        <BadgeVerificacion resultado={{ ...resultado, estado: resultado.estado }} />
      </p>
      {resultado.motivo && (
        <p className={resultado.estado === "COMPROMETIDA" ? "alerta alerta-error" : "tenue"}>{resultado.motivo}</p>
      )}
      {resultado.estado === "NO_VERIFICABLE" && (
        <button className="secundario" onClick={reintentar}>
          Reintentar la verificación de la respuesta
        </button>
      )}
      {resultado.estado === "PENDIENTE" && (
        <p className="tenue">La empresa respondió; la respuesta se va a poder leer cuando termine su anclaje.</p>
      )}
      {documento && (
        <div className="panel">
          <p className="respuesta">{documento.respuesta?.texto}</p>
          <p className="tenue">Respondida el {documento.respondida_en}</p>
        </div>
      )}
      {queja.respuesta?.txHash && (
        <p className="tenue">
          Transacción: <Tx hash={queja.respuesta.txHash} />
        </p>
      )}
    </section>
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
      <section aria-label="Verificación de la queja">
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
      </section>

      <h2>Respuesta de la empresa</h2>
      <RespuestaDeLaEmpresa queja={queja} />

      <h2>Datos de verificación</h2>
      <dl className="datos">
        <dt>Id en la blockchain</dt>
        <dd>{queja.onchainId ?? <span className="tenue">sin anclar</span>}</dd>
        <dt>CID en IPFS</dt>
        <dd>{queja.ipfsCid ? <code>{queja.ipfsCid}</code> : <span className="tenue">sin publicar</span>}</dd>
        <dt>Hash del contenido</dt>
        <dd>{queja.contentHash ? <code>{queja.contentHash}</code> : <span className="tenue">sin calcular</span>}</dd>
        <dt>Transacción de registro</dt>
        <dd>
          <Tx hash={queja.txHash} />
        </dd>
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
