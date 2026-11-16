"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { api, ErrorDeApi, type Sesion } from "@/lib/api";
import { guardarSesion } from "@/lib/sesion";

const PANELES: Partial<Record<Sesion["rol"], string>> = {
  CIUDADANO: "/quejas/nueva",
  FUNCIONARIO: "/panel",
  EMPRESA: "/empresa",
};

export default function Ingresar() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function ingresar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const sesion = await api.login(email, password);
      // Cada rol tiene su página: el ciudadano presenta quejas, el organismo y las empresas tienen su panel.
      const destino = PANELES[sesion.rol];
      if (!destino) {
        setError("Este rol no tiene acceso desde el sitio.");
        return;
      }
      guardarSesion(sesion);
      router.push(destino);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : "No se pudo conectar. Probá de nuevo en unos segundos.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <h1>Ingresar</h1>
      <p className="tenue">
        Para presentar una queja, y para el organismo de defensa del consumidor y las empresas.
      </p>
      <form className="formulario" onSubmit={ingresar}>
        <label>
          Email
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" disabled={enviando}>
          {enviando ? "Ingresando…" : "Ingresar"}
        </button>
      </form>
      {error && <p className="alerta alerta-error">{error}</p>}
    </>
  );
}
