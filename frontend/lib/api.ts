// Tipos de la API pública del backend (readme, sección 4). Se consulta por el mismo origen: next.config.ts la reenvía.

export type EmpresaResumen = { cuit: string; razonSocial: string };

export type QuejaResumen = {
  id: string;
  onchainId: number | null;
  ipfsCid: string | null;
  contentHash: string | null;
  estado: string;
  categoria: string;
  fechaHecho: string;
};

export type CambioDeEstado = { estadoAnterior: string; estadoNuevo: string; txHash: string | null; createdAt: string };

export type QuejaDetalle = {
  id: string;
  onchainId: number | null;
  ipfsCid: string | null;
  contentHash: string | null;
  estado: string;
  historialEstados: CambioDeEstado[];
  evidenciasCids: string[];
  /** null si la empresa no respondió. El texto no viene acá: se lee de IPFS y se verifica contra la cadena. */
  respuesta: { anchorStatus: string; txHash: string | null; createdAt: string } | null;
};

export type QuejaRecibida = {
  id: string;
  onchainId: number | null;
  categoria: string;
  fechaHecho: string;
  estado: string;
  /** Si ya se puede responder (la queja está anclada). */
  anclada: boolean;
  /** Estado de anclaje de la respuesta, o null si todavía no respondió. */
  respuesta: string | null;
};

export class NoEncontrado extends Error {}

/** Token vencido, inválido o de un rol sin permiso: hay que volver a iniciar sesión. */
export class NoAutorizado extends Error {}

/** Un rechazo con motivo que el backend explica (409, 400, credenciales inválidas). */
export class ErrorDeApi extends Error {}

export type Sesion = { token: string; rol: "CIUDADANO" | "FUNCIONARIO" | "EMPRESA" };

export type ItemDeBandeja = {
  id: string;
  empresa: string;
  triageEstado: "SUGERIDO" | "MANUAL" | "PENDIENTE";
  categoriaSugerida: string | null;
  resumen: string | null;
  prioridad: "BAJA" | "MEDIA" | "ALTA" | null;
  articulosSugeridos: string[];
  estado: string;
  createdAt: string;
};

async function motivo(respuesta: Response): Promise<string> {
  try {
    const cuerpo = (await respuesta.json()) as { mensaje?: string };
    return cuerpo.mensaje ?? `La API respondió ${respuesta.status}`;
  } catch {
    return `La API respondió ${respuesta.status}`;
  }
}

async function conSesion(ruta: string, token: string, init: RequestInit = {}): Promise<Response> {
  const respuesta = await fetch(ruta, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...(init.headers as Record<string, string> | undefined) },
  });
  if (respuesta.status === 401 || respuesta.status === 403) {
    throw new NoAutorizado(ruta);
  }
  if (!respuesta.ok) {
    throw new ErrorDeApi(await motivo(respuesta));
  }
  return respuesta;
}

async function obtener<T>(ruta: string): Promise<T> {
  const respuesta = await fetch(ruta, { headers: { Accept: "application/json" } });
  if (respuesta.status === 404) {
    throw new NoEncontrado(ruta);
  }
  if (!respuesta.ok) {
    throw new Error(`La API respondió ${respuesta.status}`);
  }
  return (await respuesta.json()) as T;
}

export const api = {
  buscarEmpresas: (q: string) => obtener<EmpresaResumen[]>(`/api/empresas?q=${encodeURIComponent(q)}`),
  quejasDeEmpresa: (cuit: string, page: number) =>
    obtener<{ items: QuejaResumen[] }>(`/api/empresas/${encodeURIComponent(cuit)}/quejas?page=${page}`),
  queja: (id: string) => obtener<QuejaDetalle>(`/api/quejas/${encodeURIComponent(id)}`),

  async login(email: string, password: string): Promise<Sesion> {
    const respuesta = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!respuesta.ok) {
      throw new ErrorDeApi(await motivo(respuesta));
    }
    return (await respuesta.json()) as Sesion;
  },

  async bandeja(token: string, estado: string | null, page = 0): Promise<{ items: ItemDeBandeja[] }> {
    const filtro = estado ? `&estado=${encodeURIComponent(estado)}` : "";
    return (await (await conSesion(`/api/panel/quejas?page=${page}${filtro}`, token)).json()) as {
      items: ItemDeBandeja[];
    };
  },

  async bandejaEmpresa(
    token: string,
    page = 0,
  ): Promise<{ empresa: EmpresaResumen; items: QuejaRecibida[] }> {
    return (await (await conSesion(`/api/empresa/quejas?page=${page}`, token)).json()) as {
      empresa: EmpresaResumen;
      items: QuejaRecibida[];
    };
  },

  async responder(token: string, id: string, texto: string): Promise<void> {
    await conSesion(`/api/quejas/${encodeURIComponent(id)}/respuesta`, token, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ texto }),
    });
  },

  async cambiarEstado(token: string, id: string, estado: string, comentario: string): Promise<void> {
    await conSesion(`/api/quejas/${encodeURIComponent(id)}/estado`, token, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ estado, comentario }),
    });
  },
};

/** El listado no informa si hay más páginas: si vino una página llena, puede haber otra. */
export const TAMANIO_PAGINA = 20;
