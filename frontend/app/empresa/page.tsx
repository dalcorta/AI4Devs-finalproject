"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import { api, type EmpresaResumen, ErrorDeApi, NoAutorizado, type QuejaRecibida, TAMANIO_PAGINA } from "@/lib/api";
import { NOMBRES, type Estado } from "@/lib/estados";
import { cerrarSesion, leerSesion } from "@/lib/sesion";

const MAX_TEXTO = 3000;

const RESPUESTA: Record<string, { texto: string; clase: string }> = {
  PENDIENTE: { texto: "Respuesta enviada, por anclar", clase: "badge badge-pendiente" },
  ENVIADA: { texto: "Respuesta enviada, por anclar", clase: "badge badge-pendiente" },
  CONFIRMADA: { texto: "Respondida", clase: "badge badge-ok" },
  FALLIDA: { texto: "Falló el anclaje de la respuesta", clase: "badge badge-error" },
};

function Responder({ item, token, alResponder }: { item: QuejaRecibida; token: string; alResponder: () => void }) {
  const [texto, setTexto] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      await api.responder(token, item.id, texto);
      alResponder();
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : "No se pudo enviar la respuesta.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="respuesta-form" onSubmit={enviar} aria-label={`Responder la queja ${item.id}`}>
      <textarea
        aria-label="Respuesta pública"
        required
        maxLength={MAX_TEXTO}
        rows={4}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
      />
      <button type="submit" disabled={enviando || texto.trim() === ""}>
        {enviando ? "Enviando…" : "Publicar respuesta"}
      </button>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

export default function PanelDeEmpresa() {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [pagina, setPagina] = useState(0);
  const [empresa, setEmpresa] = useState<EmpresaResumen | null>(null);
  const [items, setItems] = useState<QuejaRecibida[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const sesion = leerSesion();
    if (sesion?.rol !== "EMPRESA") {
      router.replace("/login");
      return;
    }
    setToken(sesion.token);
  }, [router]);

  const cargar = useCallback(async () => {
    if (!token) {
      return;
    }
    setError(null);
    try {
      const bandeja = await api.bandejaEmpresa(token, pagina);
      setEmpresa(bandeja.empresa);
      setItems(bandeja.items);
    } catch (e) {
      if (e instanceof NoAutorizado) {
        cerrarSesion();
        router.replace("/login");
        return;
      }
      setError("No se pudieron cargar las quejas.");
    }
  }, [token, pagina, router]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  if (!token) {
    return <p className="tenue">Cargando…</p>;
  }

  return (
    <>
      <div className="encabezado-panel">
        <h1>{empresa ? empresa.razonSocial : "Quejas recibidas"}</h1>
        <button
          className="secundario"
          onClick={() => {
            cerrarSesion();
            router.replace("/login");
          }}
        >
          Cerrar sesión
        </button>
      </div>
      {empresa && <p className="tenue">CUIT {empresa.cuit}. Primero las quejas que todavía no respondieron.</p>}
      <p className="alerta alerta-aviso">
        La respuesta es <strong>pública y permanente</strong>: se publica en IPFS y su huella queda anclada en la
        blockchain, así que no se puede editar ni borrar. Hay una sola respuesta por queja. No incluyas datos personales
        del consumidor (nombre, DNI, teléfono, número de cliente o de cuenta).
      </p>
      {error && <p className="alerta alerta-error">{error}</p>}
      {items === null && !error && <p className="tenue">Cargando…</p>}
      {items !== null && items.length === 0 && <p className="tenue">No hay quejas contra la empresa.</p>}
      {items !== null && items.length > 0 && (
        <ul className="lista bandeja">
          {items.map((item) => (
            <li key={item.id}>
              <div className="fila-bandeja">
                <div>
                  <Link href={`/quejas/${item.id}`}>Queja {item.onchainId !== null ? `#${item.onchainId}` : ""}</Link>{" "}
                  <span className="tenue">
                    · {item.categoria} · {item.fechaHecho}
                  </span>
                  <div className="etiquetas">
                    <span className="badge badge-neutro">{NOMBRES[item.estado as Estado] ?? item.estado}</span>
                    {item.respuesta && (
                      <span className={RESPUESTA[item.respuesta]?.clase ?? "badge badge-neutro"}>
                        {RESPUESTA[item.respuesta]?.texto ?? item.respuesta}
                      </span>
                    )}
                  </div>
                </div>
                {item.respuesta === null &&
                  (item.anclada ? (
                    <Responder item={item} token={token} alResponder={() => void cargar()} />
                  ) : (
                    <span className="tenue">Se puede responder cuando termine su anclaje.</span>
                  ))}
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="paginacion">
        <button className="secundario" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
          Anteriores
        </button>
        <button
          className="secundario"
          disabled={items === null || items.length < TAMANIO_PAGINA}
          onClick={() => setPagina((p) => p + 1)}
        >
          Siguientes
        </button>
      </div>
    </>
  );
}
