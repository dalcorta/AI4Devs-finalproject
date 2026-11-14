import { defineConfig, devices } from "@playwright/test";
import { FRONTEND_URL } from "./e2e/entorno";

// Requisitos: servicios de compose levantados (podman-compose up -d), `forge build` en contracts/ y Chromium de
// Playwright (npx playwright install chromium). El setup compila backend y frontend y los levanta en puertos propios.
export default defineConfig({
  testDir: "e2e",
  globalSetup: "./e2e/global-setup.ts",
  globalTeardown: "./e2e/global-teardown.ts",
  // Un solo worker: los escenarios comparten la queja anclada en el setup del archivo y la máquina es chica.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: { baseURL: FRONTEND_URL, trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
