import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "LibroAbierto",
  description: "Libro de quejas de consumo público y verificable",
};

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="es-AR">
      <body>
        <header className="sitio">
          <div className="contenedor">
            <Link href="/" className="marca">
              LibroAbierto
            </Link>
            <span>Quejas de consumo públicas y verificables</span>
          </div>
        </header>
        <main className="contenedor">{children}</main>
      </body>
    </html>
  );
}
