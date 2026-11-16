import { type Page, expect, test } from "@playwright/test";
import { CUIT } from "./apoyo";
import { leerEstado } from "./entorno";

/**
 * HU-06 contra el entorno real (con un Ollama falso que reconoce "Juan Pérez"): antes de publicar, la ciudadana ve
 * los datos personales que encontró la revisión, los corrige o publica igual, y la queja termina anclada y verificada.
 */

test.describe.configure({ mode: "serial" });

async function ingresarComoCiudadana(page: Page) {
  const { email, clave } = leerEstado().ciudadana;
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Contraseña").fill(clave);
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page).toHaveURL("/quejas/nueva");
}

async function completar(page: Page, descripcion: string) {
  const formulario = page.getByRole("form", { name: "Nueva queja" });
  await formulario.getByLabel("CUIT de la empresa").fill(CUIT);
  // La empresa ya existe (la crea el setup): el formulario muestra su razón social y no la pide.
  await expect(page.getByText(/^Empresa: /)).toBeVisible();
  await formulario.getByLabel("Categoría").selectOption("telecomunicaciones");
  await formulario.getByLabel("Qué pasó").fill(descripcion);
  await formulario.getByLabel("Fecha del hecho").fill("2026-11-01");
  return formulario;
}

test("sin sesión, la página de nueva queja lleva a ingresar", async ({ page }) => {
  await page.goto("/quejas/nueva");

  await expect(page).toHaveURL("/login");
});

test("avisa los datos personales, la ciudadana los quita y la queja se publica y se verifica", async ({ page }) => {
  await ingresarComoCiudadana(page);
  const formulario = await completar(
    page,
    "Me atendió Juan Pérez en la sucursal, le di mi DNI 30.123.456 y nunca me devolvieron el cobro duplicado.",
  );
  await formulario.getByRole("button", { name: "Revisar y publicar" }).click();

  const aviso = page.getByRole("alert").filter({ hasText: "permanente" });
  await expect(aviso).toContainText("datos personales");
  await expect(aviso.getByText("Juan Pérez")).toBeVisible();
  await expect(aviso.getByText("30.123.456")).toBeVisible();
  await expect(page).toHaveURL("/quejas/nueva");

  await aviso.getByRole("button", { name: "Corregir el texto" }).click();
  await formulario
    .getByLabel("Qué pasó")
    .fill("Me atendieron en la sucursal, mostré mi documento y nunca me devolvieron el cobro duplicado.");
  await expect(aviso).toHaveCount(0);
  await formulario.getByRole("button", { name: "Revisar y publicar" }).click();

  await expect(page).toHaveURL(/\/quejas\/[0-9a-f-]{36}$/);
  const badge = page.getByRole("region", { name: "Verificación de la queja" }).getByRole("status");
  await expect(async () => {
    await page.reload();
    await expect(badge).toHaveText("Verificada", { timeout: 5_000 });
  }).toPass({ timeout: 60_000 });
  await expect(page.getByText("mostré mi documento")).toBeVisible();
  await expect(page.getByText("Juan Pérez")).toHaveCount(0);
});

test("si la ciudadana confirma, la queja se publica tal cual", async ({ page }) => {
  await ingresarComoCiudadana(page);
  const formulario = await completar(page, "La vendedora, María Gómez, me negó la garantía del lavarropas.");
  await formulario.getByRole("button", { name: "Revisar y publicar" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "permanente" }).getByText("María Gómez")).toBeVisible();

  await page.getByRole("button", { name: "Publicar igual" }).click();

  await expect(page).toHaveURL(/\/quejas\/[0-9a-f-]{36}$/);
});
