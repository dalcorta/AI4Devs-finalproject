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
};

export class NoEncontrado extends Error {}

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
};

/** El listado no informa si hay más páginas: si vino una página llena, puede haber otra. */
export const TAMANIO_PAGINA = 20;
