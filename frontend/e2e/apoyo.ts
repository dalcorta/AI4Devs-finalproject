import { type APIRequestContext, expect } from "@playwright/test";
import { leerEstado } from "./entorno";

export const CUIT = "30712345671";
export const RAZON_SOCIAL = "Telecomunicaciones del Sur S.A.";

/** Registra una queja por la API como la ciudadana de prueba y espera a que quede anclada y confirmada. */
export async function registrarYEsperarAnclaje(request: APIRequestContext, descripcion: string): Promise<string> {
  const { email, clave } = leerEstado().ciudadana;
  const login = await request.post("/api/auth/login", { data: { email, password: clave } });
  expect(login.ok()).toBe(true);
  const { token } = (await login.json()) as { token: string };

  const alta = await request.post("/api/quejas", {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      cuitEmpresa: CUIT,
      razonSocial: RAZON_SOCIAL,
      categoria: "telecomunicaciones",
      descripcion,
      montoReclamado: 15000,
      fechaHecho: "2026-11-01",
    },
  });
  expect(alta.status()).toBe(202);
  const { id } = (await alta.json()) as { id: string };

  await expect
    .poll(
      async () => ((await (await request.get(`/api/quejas/${id}`)).json()) as { onchainId: number | null }).onchainId,
      { message: "La queja no quedó anclada y confirmada a tiempo", timeout: 60_000 },
    )
    .not.toBeNull();
  return id;
}
