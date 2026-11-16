import { expect, test } from "@playwright/test";
import { registrarYEsperarAnclaje } from "./apoyo";
import { leerEstado } from "./entorno";

/**
 * HU-04 contra el entorno real: la funcionaria entra al panel, cambia el estado de una queja anclada y el cambio
 * queda visible en la vista pública con su transacción, sin exponer el comentario interno.
 */

const DESCRIPCION = "Cobro de un servicio que nunca fue contratado, sin respuesta a los reclamos.";
const COMENTARIO = "Se pidió a la empresa la constancia de alta del servicio.";

let quejaId: string;

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  quejaId = await registrarYEsperarAnclaje(request, DESCRIPCION);
});

test("sin sesión, el panel lleva a ingresar", async ({ page }) => {
  await page.goto("/panel");

  await expect(page).toHaveURL("/login");
});

test("una ciudadana entra a presentar quejas, no al panel", async ({ page }) => {
  const { email, clave } = leerEstado().ciudadana;
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Contraseña").fill(clave);
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page).toHaveURL("/quejas/nueva");

  await page.goto("/panel");
  await expect(page).toHaveURL("/login");
});

test("la funcionaria cambia el estado y queda en la vista pública con su transacción", async ({ page }) => {
  const { email, clave } = leerEstado().funcionaria;
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Contraseña").fill(clave);
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page).toHaveURL("/panel");

  // El triage está deshabilitado en E2E: la queja aparece con el triage pendiente.
  const cambio = page.getByRole("form", { name: `Cambiar estado de la queja ${quejaId}` });
  const fila = page.getByRole("listitem").filter({ has: cambio });
  await expect(fila.getByText("Triage pendiente")).toBeVisible();
  // Se mira el badge de estado, no el texto suelto: el select ya ofrece "En revisión" antes del cambio.
  await expect(fila.locator(".badge", { hasText: "Registrada" })).toBeVisible();

  await cambio.getByLabel("Nuevo estado").selectOption("EN_REVISION");
  await cambio.getByLabel("Comentario interno").fill(COMENTARIO);
  await cambio.getByRole("button", { name: "Cambiar estado" }).click();
  await expect(fila.locator(".badge", { hasText: "En revisión" })).toBeVisible();

  // El worker lo ancla con la clave del funcionario: la vista pública muestra el cambio con su hash de transacción.
  await expect
    .poll(
      async () => {
        await page.goto(`/quejas/${quejaId}`);
        return (await page.locator("li", { hasText: "REGISTRADA → EN_REVISION" }).locator("code").count()) > 0;
      },
      { message: "El cambio de estado no quedó anclado a tiempo", timeout: 60_000 },
    )
    .toBe(true);
  await expect(page.getByText("EN_REVISION", { exact: true })).toBeVisible();
  await expect(page.getByText(COMENTARIO)).toHaveCount(0);
});
