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
  /** Transacción de registro confirmada, o null. Es informativa: la verificación lee el contrato por su cuenta. */
  txHash: string | null;
};

export type CambioDeEstado = { estadoAnterior: string; estadoNuevo: string; txHash: string | null; createdAt: string };

export type QuejaDetalle = {
  id: string;
  onchainId: number | null;
  ipfsCid: string | null;
  contentHash: string | null;
  /** Transacción de registro confirmada, o null; como en QuejaResumen. */
  txHash: string | null;
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

/** HU-06: un posible dato personal en el texto que se iba a publicar (posiciones del texto ya recortado). */
export type Hallazgo = {
  tipo: "NOMBRE" | "DNI" | "CUIL" | "EMAIL" | "TELEFONO" | "TARJETA" | "CBU";
  fragmento: string;
  inicio: number;
  fin: number;
};

export type AvisoDeDatosPersonales = { campo: string; revisionCompleta: boolean; hallazgos: Hallazgo[] };

/** 422 de HU-06: no se guardó nada; el autor quita los datos o reenvía confirmando. */
export class DatosPersonalesDetectados extends ErrorDeApi {
  constructor(
    mensaje: string,
    readonly aviso: AvisoDeDatosPersonales,
  ) {
    super(mensaje);
  }
}

export type NuevaQueja = {
  cuitEmpresa: string;
  razonSocial: string | null;
  categoria: string;
  descripcion: string;
  montoReclamado: number | null;
  fechaHecho: string;
};

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

type CuerpoDeError = { mensaje?: string; errores?: Record<string, string>; datosPersonales?: AvisoDeDatosPersonales };

async function cuerpoDeError(respuesta: Response): Promise<CuerpoDeError> {
  try {
    return (await respuesta.json()) as CuerpoDeError;
  } catch {
    return {};
  }
}

async function motivo(respuesta: Response): Promise<string> {
  return (await cuerpoDeError(respuesta)).mensaje ?? `La API respondió ${respuesta.status}`;
}

/** Un rechazo por datos inválidos, con el motivo de cada campo para mostrarlo junto a él. */
export class DatosInvalidos extends ErrorDeApi {
  constructor(
    mensaje: string,
    readonly errores: Record<string, string>,
  ) {
    super(mensaje);
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
    const cuerpo = await cuerpoDeError(respuesta);
    const mensaje = cuerpo.mensaje ?? `La API respondió ${respuesta.status}`;
    if (respuesta.status === 422 && cuerpo.datosPersonales) {
      throw new DatosPersonalesDetectados(mensaje, cuerpo.datosPersonales);
    }
    if (respuesta.status === 400 && cuerpo.errores) {
      throw new DatosInvalidos(mensaje, cuerpo.errores);
    }
    throw new ErrorDeApi(mensaje);
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

  /** Devuelve el id de la queja. Con `confirmar`, se publica aunque parezca tener datos personales (HU-06). */
  async registrarQueja(token: string, queja: NuevaQueja, confirmar: boolean): Promise<string> {
    const respuesta = await conSesion("/api/quejas", token, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...queja, confirmarDatosPersonales: confirmar }),
    });
    return ((await respuesta.json()) as { id: string }).id;
  },

  async responder(token: string, id: string, texto: string, confirmar = false): Promise<void> {
    await conSesion(`/api/quejas/${encodeURIComponent(id)}/respuesta`, token, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ texto, confirmarDatosPersonales: confirmar }),
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
