"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { api, ErrorDeApi } from "@/lib/api";
import { guardarSesion } from "@/lib/sesion";

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
      if (sesion.rol !== "FUNCIONARIO") {
        setError("Por ahora este acceso es solo para funcionarios del organismo.");
        return;
      }
      guardarSesion(sesion);
      router.push("/panel");
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : "No se pudo conectar. Probá de nuevo en unos segundos.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <h1>Ingresar</h1>
      <p className="tenue">Acceso del organismo de defensa del consumidor.</p>
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
