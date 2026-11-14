import type { NextConfig } from "next";

// El navegador habla con la API del backend a través de este mismo origen (sin abrir CORS en el backend).
// La verificación de integridad NO pasa por acá: va directo a IPFS y a la cadena desde el navegador.
const backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8081";

const config: NextConfig = {
  // Los E2E compilan con su propio contrato y puertos en otra carpeta, sin pisar el build de desarrollo.
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  // Los E2E compilan en una máquina chica: el chequeo de tipos ya lo hace `npm run typecheck` por separado.
  typescript: { ignoreBuildErrors: process.env.NEXT_SIN_CHEQUEO_DE_TIPOS === "1" },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default config;
