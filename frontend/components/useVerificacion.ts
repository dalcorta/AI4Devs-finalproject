"use client";

import { useEffect, useState } from "react";
import { crearLectorDeAnclajes, crearLectorDeIpfs, crearLectorDeRespuestas } from "@/lib/chain";
import { config } from "@/lib/config";
import {
  type DatosDelBackend,
  type Resultado,
  type ResultadoDeRespuesta,
  type RespuestaDelBackend,
  verificarQueja,
  verificarRespuesta,
} from "@/lib/verify";

const dependencias = {
  leerAnclaje: crearLectorDeAnclajes({ rpcUrl: config.rpcUrl, contrato: config.contrato }),
  leerIpfs: crearLectorDeIpfs(config.ipfsGateway),
  leerRespuesta: crearLectorDeRespuestas({ rpcUrl: config.rpcUrl, contrato: config.contrato }),
};

/** Corre la verificación en el navegador; reintentar vuelve a consultar IPFS y la cadena. */
export function useVerificacion(datos: DatosDelBackend | null) {
  const [resultado, setResultado] = useState<Resultado>({ estado: "VERIFICANDO" });
  const [intento, setIntento] = useState(0);
  const { onchainId = null, ipfsCid = null, contentHash = null } = datos ?? {};

  useEffect(() => {
    if (datos === null) {
      return;
    }
    let vigente = true;
    setResultado({ estado: "VERIFICANDO" });
    verificarQueja({ onchainId, ipfsCid, contentHash }, dependencias).then((r) => {
      if (vigente) {
        setResultado(r);
      }
    });
    return () => {
      vigente = false;
    };
    // datos se compara por sus campos, no por identidad del objeto.
  }, [datos === null, onchainId, ipfsCid, contentHash, intento]);

  return { resultado, reintentar: () => setIntento((n) => n + 1) };
}

/** Verifica la respuesta de la empresa contra la cadena e IPFS, igual que la queja. */
export function useVerificacionDeRespuesta(datos: RespuestaDelBackend | null) {
  const [resultado, setResultado] = useState<ResultadoDeRespuesta>({ estado: "VERIFICANDO" });
  const [intento, setIntento] = useState(0);
  const onchainId = datos?.onchainId ?? null;
  const anchorStatus = datos?.respuesta?.anchorStatus ?? null;

  useEffect(() => {
    if (datos === null) {
      return;
    }
    let vigente = true;
    setResultado({ estado: "VERIFICANDO" });
    verificarRespuesta({ onchainId, respuesta: anchorStatus ? { anchorStatus } : null }, dependencias).then((r) => {
      if (vigente) {
        setResultado(r);
      }
    });
    return () => {
      vigente = false;
    };
  }, [datos === null, onchainId, anchorStatus, intento]);

  return { resultado, reintentar: () => setIntento((n) => n + 1) };
}
