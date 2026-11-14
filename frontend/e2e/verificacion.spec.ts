import { type APIRequestContext, expect, test } from "@playwright/test";
import { leerEstado } from "./entorno";

/**
 * Criterios de aceptación del Ticket 6 y del recorrido E2E del readme, contra el entorno real: la queja se registra
 * por la API, el worker la publica en IPFS y la ancla en Anvil, el listener la confirma, y el navegador la verifica
 * por su cuenta. Las fallas (contenido manipulado, backend que miente, IPFS caído) se provocan interceptando la red
 * del navegador, que es exactamente lo que vería quien visita el sitio.
 */

const CUIT = "30712345671";
const RAZON_SOCIAL = "Telecomunicaciones del Sur S.A.";
const DESCRIPCION = "Corte de servicio de internet durante 15 días sin respuesta del soporte técnico.";

let quejaId: string;

async function registrarYEsperarAnclaje(request: APIRequestContext): Promise<string> {
  const { email, clave } = leerEstado();
  const login = await request.post("/api/auth/login", { data: { email, password: clave } });
  expect(login.ok()).toBe(true);
  const { token } = (await login.json()) as { token: string };

  const alta = await request.post("/api/quejas", {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      cuitEmpresa: CUIT,
      razonSocial: RAZON_SOCIAL,
      categoria: "telecomunicaciones",
      descripcion: DESCRIPCION,
      montoReclamado: 15000,
      fechaHecho: "2026-11-01",
    },
  });
  expect(alta.status()).toBe(202);
  const { id } = (await alta.json()) as { id: string };

  await expect
    .poll(async () => ((await (await request.get(`/api/quejas/${id}`)).json()) as { onchainId: number | null }).onchainId, {
      message: "La queja no quedó anclada y confirmada a tiempo",
      timeout: 60_000,
    })
    .not.toBeNull();
  return id;
}

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  quejaId = await registrarYEsperarAnclaje(request);
});

test("una queja registrada aparece verificada en la consulta pública, empezando por la búsqueda", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("CUIT o razón social").fill("telecomunicaciones del sur");
  await page.getByRole("button", { name: "Buscar" }).click();
  await page.getByRole("link", { name: RAZON_SOCIAL }).click();

  await expect(page).toHaveURL(`/empresas/${CUIT}`);
  await expect(page.getByRole("status")).toHaveText("Verificada");

  await page.getByRole("link", { name: "telecomunicaciones" }).click();
  await expect(page).toHaveURL(`/quejas/${quejaId}`);
  await expect(page.getByRole("status")).toHaveText("Verificada");
  await expect(page.getByText(DESCRIPCION)).toBeVisible();
});

test("si el gateway sirve contenido manipulado, se ve «Integridad comprometida» y no el texto alterado", async ({
  page,
}) => {
  await page.route("**/ipfs/**", async (route) => {
    const original = await route.fetch();
    await route.fulfill({ response: original, body: (await original.text()).replace("15 días", "2 días") });
  });

  await page.goto(`/quejas/${quejaId}`);

  await expect(page.getByRole("status")).toHaveText("Integridad comprometida");
  await expect(page.getByText("El hash del contenido no coincide con el anclado en la blockchain")).toBeVisible();
  await expect(page.getByText("2 días")).toHaveCount(0);
  await expect(page.getByText(DESCRIPCION)).toHaveCount(0);
});

test("si el backend informa un hash que no es el de la cadena, también está comprometida", async ({ page }) => {
  await page.route(`**/api/quejas/${quejaId}`, async (route) => {
    const original = await route.fetch();
    const queja = (await original.json()) as Record<string, unknown>;
    await route.fulfill({ response: original, json: { ...queja, contentHash: `0x${"1".repeat(64)}` } });
  });

  await page.goto(`/quejas/${quejaId}`);

  await expect(page.getByRole("status")).toHaveText("Integridad comprometida");
  await expect(page.getByText("El hash que informa el sitio no es el anclado en la blockchain")).toBeVisible();
});

test("si IPFS no responde no se acusa una alteración, y al reintentar se verifica", async ({ page }) => {
  await page.route("**/ipfs/**", (route) => route.abort("connectionrefused"));

  await page.goto(`/quejas/${quejaId}`);

  await expect(page.getByRole("status")).toHaveText("No se pudo verificar");
  await expect(page.getByText("Integridad comprometida")).toHaveCount(0);

  await page.unroute("**/ipfs/**");
  await page.getByRole("button", { name: "Reintentar la verificación" }).click();
  await expect(page.getByRole("status")).toHaveText("Verificada");
});
