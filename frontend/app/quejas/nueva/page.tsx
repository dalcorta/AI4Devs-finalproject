"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { AvisoDatosPersonales } from "@/components/AvisoDatosPersonales";
import {
  api,
  type AvisoDeDatosPersonales,
  DatosInvalidos,
  DatosPersonalesDetectados,
  ErrorDeApi,
  NoAutorizado,
} from "@/lib/api";
import { cerrarSesion, leerSesion } from "@/lib/sesion";

const MAX_DESCRIPCION = 5000;
const CATEGORIAS = [
  "telecomunicaciones",
  "bancos",
  "seguros",
  "salud",
  "transporte",
  "servicios públicos",
  "comercio electrónico",
  "electrodomésticos",
  "otros",
];

function hoy(): string {
  const ahora = new Date();
  return new Date(ahora.getTime() - ahora.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

export default function NuevaQueja() {
  const router = useRouter();
  const descripcionRef = useRef<HTMLTextAreaElement>(null);
  const [token, setToken] = useState<string | null>(null);
  const [cuit, setCuit] = useState("");
  const [empresaConocida, setEmpresaConocida] = useState<string | null>(null);
  const [razonSocial, setRazonSocial] = useState("");
  const [categoria, setCategoria] = useState(CATEGORIAS[0]);
  const [descripcion, setDescripcion] = useState("");
  const [monto, setMonto] = useState("");
  const [fechaHecho, setFechaHecho] = useState("");
  const [aviso, setAviso] = useState<AvisoDeDatosPersonales | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    const sesion = leerSesion();
    if (sesion?.rol !== "CIUDADANO") {
      router.replace("/login");
      return;
    }
    setToken(sesion.token);
  }, [router]);

  // Con un CUIT completo se busca la empresa: si ya está registrada, su razón social no la cambia el denunciante.
  useEffect(() => {
    const limpio = cuit.replace(/\D/g, "");
    setEmpresaConocida(null);
    if (limpio.length !== 11) {
      return;
    }
    let vigente = true;
    api
      .buscarEmpresas(limpio)
      .then((empresas) => {
        const empresa = empresas.find((e) => e.cuit === limpio);
        if (vigente && empresa) {
          setEmpresaConocida(empresa.razonSocial);
        }
      })
      .catch(() => undefined);
    return () => {
      vigente = false;
    };
  }, [cuit]);

  async function enviar(confirmar: boolean) {
    if (!token) {
      return;
    }
    setEnviando(true);
    setError(null);
    setErrores({});
    try {
      const id = await api.registrarQueja(
        token,
        {
          cuitEmpresa: cuit.replace(/\D/g, ""),
          razonSocial: empresaConocida ? null : razonSocial,
          categoria,
          descripcion,
          montoReclamado: monto.trim() === "" ? null : Number(monto),
          fechaHecho,
        },
        confirmar,
      );
      router.push(`/quejas/${id}`);
    } catch (e) {
      if (e instanceof NoAutorizado) {
        cerrarSesion();
        router.replace("/login");
      } else if (e instanceof DatosPersonalesDetectados) {
        setAviso(e.aviso);
      } else if (e instanceof DatosInvalidos) {
        setErrores(e.errores);
        setError(e.message);
      } else {
        setError(e instanceof ErrorDeApi ? e.message : "No se pudo enviar la queja. Probá de nuevo en unos segundos.");
      }
    } finally {
      setEnviando(false);
    }
  }

  function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    void enviar(false);
  }

  /** Lleva el cursor al primer dato encontrado (las posiciones son del texto sin espacios al principio). */
  function corregir() {
    const area = descripcionRef.current;
    if (!area) {
      return;
    }
    area.focus();
    const primero = aviso?.hallazgos[0];
    if (primero) {
      const desplazamiento = descripcion.length - descripcion.trimStart().length;
      area.setSelectionRange(primero.inicio + desplazamiento, primero.fin + desplazamiento);
    }
  }

  if (!token) {
    return <p className="tenue">Cargando…</p>;
  }

  return (
    <>
      <div className="encabezado-panel">
        <h1>Nueva queja</h1>
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
      <p className="alerta alerta-aviso">
        La descripción se publica <strong>de forma permanente</strong>: va a IPFS y su huella queda en la blockchain.
        Tus datos (nombre, DNI, email, teléfono) quedan en reserva y no se publican, así que no los escribas en el
        texto, ni los de otras personas. Antes de publicar, el sistema revisa el texto y te avisa si encuentra alguno.
      </p>
      <form className="formulario formulario-ancho" onSubmit={alEnviar} aria-label="Nueva queja">
        <label>
          CUIT de la empresa
          <input inputMode="numeric" required value={cuit} onChange={(e) => setCuit(e.target.value)} />
          {errores.cuitEmpresa && <span className="error-campo">{errores.cuitEmpresa}</span>}
        </label>
        {empresaConocida ? (
          <p className="tenue">Empresa: {empresaConocida}</p>
        ) : (
          <label>
            Razón social (si la empresa todavía no tiene quejas)
            <input value={razonSocial} onChange={(e) => setRazonSocial(e.target.value)} />
            {errores.razonSocial && <span className="error-campo">{errores.razonSocial}</span>}
          </label>
        )}
        <label>
          Categoría
          <select value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            {CATEGORIAS.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
        <label>
          Qué pasó
          <textarea
            ref={descripcionRef}
            required
            rows={6}
            maxLength={MAX_DESCRIPCION}
            value={descripcion}
            onChange={(e) => {
              setDescripcion(e.target.value);
              // La confirmación vale para el texto revisado: si cambia, se vuelve a revisar.
              setAviso(null);
            }}
          />
          {errores.descripcion && <span className="error-campo">{errores.descripcion}</span>}
        </label>
        <label>
          Monto reclamado (opcional)
          <input type="number" min="0" step="0.01" value={monto} onChange={(e) => setMonto(e.target.value)} />
          {errores.montoReclamado && <span className="error-campo">{errores.montoReclamado}</span>}
        </label>
        <label>
          Fecha del hecho
          <input type="date" required max={hoy()} value={fechaHecho} onChange={(e) => setFechaHecho(e.target.value)} />
          {errores.fechaHecho && <span className="error-campo">{errores.fechaHecho}</span>}
        </label>
        {!aviso && (
          <button type="submit" disabled={enviando}>
            {enviando ? "Revisando…" : "Revisar y publicar"}
          </button>
        )}
      </form>
      {aviso && (
        <AvisoDatosPersonales
          aviso={aviso}
          enviando={enviando}
          alCorregir={corregir}
          alPublicarIgual={() => void enviar(true)}
        />
      )}
      {error && (
        <p className="alerta alerta-error" role="status">
          {error}
        </p>
      )}
    </>
  );
}
