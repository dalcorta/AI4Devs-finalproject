"use client";

import Link from "next/link";
import { type FormEvent, useState } from "react";
import { api, type EmpresaResumen } from "@/lib/api";

export default function Inicio() {
  const [q, setQ] = useState("");
  const [empresas, setEmpresas] = useState<EmpresaResumen[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function buscar(evento: FormEvent) {
    evento.preventDefault();
    if (q.trim().length < 2) {
      setError("Escribí al menos 2 caracteres.");
      return;
    }
    setCargando(true);
    setError(null);
    try {
      setEmpresas(await api.buscarEmpresas(q.trim()));
    } catch {
      setError("No se pudo consultar el listado. Probá de nuevo en unos segundos.");
    } finally {
      setCargando(false);
    }
  }

  return (
    <>
      <h1>Buscá una empresa</h1>
      <p className="tenue">
        Cada queja se publica en IPFS y su huella se registra en una blockchain. Tu navegador comprueba que nadie la
        haya modificado, sin depender de este sitio.
      </p>
      <form className="busqueda" onSubmit={buscar} role="search">
        <input
          aria-label="CUIT o razón social"
          placeholder="CUIT o razón social"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button type="submit" disabled={cargando}>
          {cargando ? "Buscando…" : "Buscar"}
        </button>
      </form>
      {error && <p className="alerta alerta-error">{error}</p>}
      {empresas !== null && empresas.length === 0 && <p className="tenue">No hay empresas que coincidan.</p>}
      {empresas !== null && empresas.length > 0 && (
        <ul className="lista">
          {empresas.map((e) => (
            <li key={e.cuit}>
              <Link href={`/empresas/${e.cuit}`}>{e.razonSocial}</Link>
              <span className="tenue">CUIT {e.cuit}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
