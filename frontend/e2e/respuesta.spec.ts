import { expect, test } from "@playwright/test";
import { registrarYEsperarAnclaje } from "./apoyo";
import { leerEstado } from "./entorno";

/**
 * HU-05 contra el entorno real: la empresa denunciada responde desde su panel, la respuesta se publica en IPFS, se
 * ancla y la vista pública la muestra verificada en el navegador. Hay una sola respuesta por queja.
 */

const DESCRIPCION = "Me cobraron dos veces el abono de noviembre y no me devolvieron la diferencia.";
const TEXTO = "Verificamos el doble cobro y lo reintegramos en la factura de diciembre.";

let quejaId: string;

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  quejaId = await registrarYEsperarAnclaje(request, DESCRIPCION);
});

test("sin sesión, el panel de la empresa lleva a ingresar", async ({ page }) => {
  await page.goto("/empresa");

  await expect(page).toHaveURL("/login");
});

test("una respuesta con datos del consumidor muestra el aviso antes de publicarse", async ({ page }) => {
  const { email, clave } = leerEstado().empresa;
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Contraseña").fill(clave);
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page).toHaveURL("/empresa");

  const formulario = page.getByRole("form", { name: `Responder la queja ${quejaId}` });
  await formulario.getByLabel("Respuesta pública").fill("Llamamos al cliente al 11 4567-8901 y no atendió.");
  await formulario.getByRole("button", { name: "Publicar respuesta" }).click();

  const aviso = formulario.getByRole("alert");
  await expect(aviso.getByText("11 4567-8901")).toBeVisible();
  await expect(aviso.getByText("Teléfono")).toBeVisible();
  // No se guardó nada: la fila sigue ofreciendo responder.
  await page.reload();
  await expect(page.getByRole("form", { name: `Responder la queja ${quejaId}` })).toBeVisible();
});

test("la empresa responde y la respuesta se ve verificada en la vista pública", async ({ page }) => {
  const { email, clave } = leerEstado().empresa;
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Contraseña").fill(clave);
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page).toHaveURL("/empresa");
  await expect(page.getByText("pública y permanente")).toBeVisible();

  const formulario = page.getByRole("form", { name: `Responder la queja ${quejaId}` });
  const fila = page.getByRole("listitem").filter({ has: page.locator(`a[href="/quejas/${quejaId}"]`) });
  await formulario.getByLabel("Respuesta pública").fill(TEXTO);
  await formulario.getByRole("button", { name: "Publicar respuesta" }).click();

  // Una vez respondida, la fila ya no ofrece responder otra vez.
  await expect(fila.getByText(/Respuesta enviada|Respondida/)).toBeVisible();
  await expect(formulario).toHaveCount(0);

  // El worker la publica y la ancla; la página verifica al cargar, así que se recarga hasta que quede anclada.
  const seccion = page.getByRole("region", { name: "Respuesta de la empresa" });
  await page.goto(`/quejas/${quejaId}`);
  await expect(async () => {
    await page.reload();
    await expect(seccion.getByRole("status")).toHaveText("Verificada", { timeout: 5_000 });
  }).toPass({ timeout: 60_000 });
  await expect(seccion.getByText(TEXTO)).toBeVisible();
});

test("una segunda respuesta a la misma queja es rechazada", async ({ request }) => {
  const { email, clave } = leerEstado().empresa;
  const login = await request.post("/api/auth/login", { data: { email, password: clave } });
  const { token } = (await login.json()) as { token: string };

  const segunda = await request.post(`/api/quejas/${quejaId}/respuesta`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { texto: "Otra respuesta." },
  });

  expect(segunda.status()).toBe(409);
});
