import type { NextConfig } from "next";

// El navegador habla con la API del backend a través de este mismo origen (sin abrir CORS en el backend).
// La verificación de integridad NO pasa por acá: va directo a IPFS y a la cadena desde el navegador.
const backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8081";

const config: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default config;
