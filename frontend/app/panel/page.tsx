"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import { api, ErrorDeApi, type ItemDeBandeja, NoAutorizado, TAMANIO_PAGINA } from "@/lib/api";
import { ESTADOS, NOMBRES, siguientes, type Estado } from "@/lib/estados";
import { cerrarSesion, leerSesion } from "@/lib/sesion";

const TRIAGE: Record<ItemDeBandeja["triageEstado"], { texto: string; clase: string }> = {
  SUGERIDO: { texto: "Sugerencia de IA", clase: "badge badge-neutro" },
  MANUAL: { texto: "Triage manual", clase: "badge badge-aviso" },
  PENDIENTE: { texto: "Triage pendiente", clase: "badge badge-pendiente" },
};

function nombre(estado: string) {
  return NOMBRES[estado as Estado] ?? estado;
}

function CambioDeEstado({ item, token, alCambiar }: { item: ItemDeBandeja; token: string; alCambiar: () => void }) {
  const opciones = siguientes(item.estado);
  const [nuevo, setNuevo] = useState<string>(opciones[0] ?? "");
  const [comentario, setComentario] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (opciones.length === 0) {
    return <span className="tenue">Estado final</span>;
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      await api.cambiarEstado(token, item.id, nuevo, comentario);
      setComentario("");
      alCambiar();
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : "No se pudo cambiar el estado.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="cambio" onSubmit={enviar} aria-label={`Cambiar estado de la queja ${item.id}`}>
      <select aria-label="Nuevo estado" value={nuevo} onChange={(e) => setNuevo(e.target.value)}>
        {opciones.map((o) => (
          <option key={o} value={o}>
            {NOMBRES[o]}
          </option>
        ))}
      </select>
      <input
        aria-label="Comentario interno"
        placeholder="Comentario interno (opcional)"
        maxLength={1000}
        value={comentario}
        onChange={(e) => setComentario(e.target.value)}
      />
      <button type="submit" disabled={enviando}>
        {enviando ? "Guardando…" : "Cambiar estado"}
      </button>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

export default function Panel() {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [estado, setEstado] = useState<string>("");
  const [pagina, setPagina] = useState(0);
  const [items, setItems] = useState<ItemDeBandeja[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const sesion = leerSesion();
    if (sesion?.rol !== "FUNCIONARIO") {
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
      setItems((await api.bandeja(token, estado || null, pagina)).items);
    } catch (e) {
      if (e instanceof NoAutorizado) {
        cerrarSesion();
        router.replace("/login");
        return;
      }
      setError("No se pudo cargar la bandeja.");
    }
  }, [token, estado, pagina, router]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  if (!token) {
    return <p className="tenue">Cargando…</p>;
  }

  return (
    <>
      <div className="encabezado-panel">
        <h1>Bandeja</h1>
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
      <p className="tenue">
        Quejas ancladas en la blockchain: primero las que necesitan triage manual, después por prioridad y antigüedad.
        La clasificación es una sugerencia generada por IA.
      </p>
      <label className="filtro">
        Estado
        <select
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value);
            setPagina(0);
          }}
        >
          <option value="">Todos</option>
          {ESTADOS.map((e) => (
            <option key={e} value={e}>
              {NOMBRES[e]}
            </option>
          ))}
        </select>
      </label>
      {error && <p className="alerta alerta-error">{error}</p>}
      {items === null && !error && <p className="tenue">Cargando…</p>}
      {items !== null && items.length === 0 && <p className="tenue">No hay quejas para mostrar.</p>}
      {items !== null && items.length > 0 && (
        <ul className="lista bandeja">
          {items.map((item) => (
            <li key={item.id}>
              <div className="fila-bandeja">
                <div>
                  <Link href={`/quejas/${item.id}`}>{item.empresa}</Link>{" "}
                  <span className="tenue">· {new Date(item.createdAt).toLocaleDateString("es-AR")}</span>
                  <div className="etiquetas">
                    <span className={TRIAGE[item.triageEstado].clase}>{TRIAGE[item.triageEstado].texto}</span>
                    {item.prioridad && <span className="badge badge-pendiente">Prioridad {item.prioridad.toLowerCase()}</span>}
                    <span className="badge badge-ok">{nombre(item.estado)}</span>
                  </div>
                  {item.triageEstado === "SUGERIDO" && (
                    <p className="sugerencia">
                      <strong>{item.categoriaSugerida}.</strong> {item.resumen}
                      {item.articulosSugeridos.length > 0 && (
                        <span className="tenue"> Ley 24.240, art. {item.articulosSugeridos.join(", ")}.</span>
                      )}
                    </p>
                  )}
                </div>
                <CambioDeEstado item={item} token={token} alCambiar={() => void cargar()} />
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
