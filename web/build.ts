/**
 * Renders the site to static files in ../site, for Cloudflare (Workers static assets), which
 * serves files rather than running Deno.
 *
 * Every page comes from the same handler the Deno server uses, so the static copy cannot
 * drift from the code: CI rebuilds it and fails if the committed copy differs.
 *
 *   node web/build.ts        (Node 22+)
 *   deno run --allow-read --allow-write web/build.ts
 */
import { copyFileSync, mkdirSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { handler, SECURITY_HEADERS } from "./main.ts";

const ORIGIN = "https://squeeze.fit";
const OUT = fileURLToPath(new URL("../site/", import.meta.url));
const STATIC = fileURLToPath(new URL("./static/", import.meta.url));

async function render(route: string): Promise<string> {
  const response = await handler(new Request(ORIGIN + route));
  return await response.text();
}

function write(file: string, text: string) {
  const path = OUT + file;
  mkdirSync(path.substring(0, path.lastIndexOf("/")), { recursive: true });
  writeFileSync(path, text);
}

rmSync(OUT, { recursive: true, force: true });

write("index.html", await render("/"));
write("privacy/index.html", await render("/privacy"));
write("privacy-policy/index.html", await render("/privacy-policy"));
write("404.html", await render("/this-page-does-not-exist"));
write("robots.txt", await render("/robots.txt"));
write("sitemap.xml", await render("/sitemap.xml"));
write("health", "ok");

mkdirSync(OUT + "static", { recursive: true });
for (const name of readdirSync(STATIC)) copyFileSync(STATIC + name, OUT + "static/" + name);

// The same headers the Deno server sends, in Cloudflare's _headers format.
write(
  "_headers",
  "/*\n" + Object.entries(SECURITY_HEADERS).map(([k, v]) => `  ${k}: ${v}`).join("\n") +
    "\n  cache-control: public, max-age=300\n",
);

console.log("site/ written");
