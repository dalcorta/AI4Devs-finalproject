// Imita la API de chat de Ollama para los E2E: los escenarios no dependen de bajar ni de correr un modelo.
// Devuelve como nombres de personas los de una lista fija que aparezcan en el texto, con el formato real de Ollama.
import { createServer } from "node:http";

const NOMBRES_CONOCIDOS = ["Juan Pérez", "María Gómez"];
const puerto = Number(process.argv[2] ?? 11500);

createServer((pedido, respuesta) => {
  let cuerpo = "";
  pedido.on("data", (parte) => (cuerpo += parte));
  pedido.on("end", () => {
    if (pedido.method !== "POST" || pedido.url !== "/api/chat") {
      respuesta.writeHead(404).end();
      return;
    }
    const { model, messages } = JSON.parse(cuerpo);
    const texto = messages.find((m) => m.role === "user")?.content ?? "";
    const nombres = NOMBRES_CONOCIDOS.filter((n) => texto.includes(n));
    respuesta.writeHead(200, { "Content-Type": "application/json" });
    respuesta.end(
      JSON.stringify({ model, done: true, message: { role: "assistant", content: JSON.stringify({ nombres }) } }),
    );
  });
}).listen(puerto, "127.0.0.1");
