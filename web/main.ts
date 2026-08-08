import { homePage, notFoundPage, privacyPage } from "./pages.ts";
import { handleAssess } from "./lab_api.ts";

/**
 * The Squeeze.fit website.
 *
 * Deliberately not a backend. The Android app is built without the internet permission,
 * which is its central and independently verifiable privacy claim — so nothing here syncs,
 * stores or receives anything from the app, and nothing here should ever start doing so.
 * What this serves is the public face of the project: what the app does, why its accuracy
 * claims are framed the way they are, and the privacy policy that a Play Store listing is
 * required to link to.
 *
 * No dependencies, by choice. `Deno.serve` is built in, so there is no import map to resolve
 * and no third-party module that can change under a deployment. The entire site is this
 * file, two modules of markup and style, and two images.
 */

/** Static assets live beside this file, resolved relative to the module rather than to cwd. */
const STATIC_ROOT = new URL("./static/", import.meta.url);

const CONTENT_TYPES: Record<string, string> = {
  png: "image/png",
  svg: "image/svg+xml",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  webp: "image/webp",
  ico: "image/x-icon",
  txt: "text/plain; charset=utf-8",
  webmanifest: "application/manifest+json",
};

/** Applied to every response. Cheap, and there is no reason for the site to be framed. */
const SECURITY_HEADERS: Record<string, string> = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "strict-origin-when-cross-origin",
  "x-frame-options": "DENY",
  // The pages are self-contained: inline style, one inline SVG set, no scripts at all.
  "content-security-policy": [
    "default-src 'none'",
    "img-src 'self'",
    "style-src 'unsafe-inline'",
    "form-action 'none'",
    "frame-ancestors 'none'",
    "base-uri 'none'",
  ].join("; "),
};

function html(markup: string, status = 200): Response {
  return new Response(markup, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      // Short, so a content fix is visible quickly, but long enough that a reload during a
      // visit is not a fresh render every time.
      "cache-control": "public, max-age=300",
      ...SECURITY_HEADERS,
    },
  });
}

/**
 * Serves a file from [STATIC_ROOT].
 *
 * The path is rebuilt from its final segment rather than being appended to the root, so a
 * request containing `..` cannot climb out of the static directory and read source. Encoded
 * traversal is covered too, because decoding happens before the segment is taken.
 */
async function serveStatic(pathname: string): Promise<Response> {
  const requested = decodeURIComponent(pathname.replace(/^\/static\//, ""));
  const name = requested.split("/").pop() ?? "";

  if (!name || name !== requested || name.startsWith(".")) {
    return html(notFoundPage(), 404);
  }

  const extension = name.split(".").pop()?.toLowerCase() ?? "";
  const contentType = CONTENT_TYPES[extension];
  if (!contentType) return html(notFoundPage(), 404);

  try {
    const bytes = await Deno.readFile(new URL(name, STATIC_ROOT));
    return new Response(bytes, {
      headers: {
        "content-type": contentType,
        // Assets are content-stable; a change ships with a new deployment.
        "cache-control": "public, max-age=86400",
        ...SECURITY_HEADERS,
      },
    });
  } catch {
    return html(notFoundPage(), 404);
  }
}

export function handler(request: Request): Response | Promise<Response> {
  const url = new URL(request.url);

  // Trailing slashes are normalised so "/privacy/" and "/privacy" are not two URLs with the
  // same content, which search engines would otherwise treat as duplicates.
  const path = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, "") : url.pathname;

  // Routed before the method check, because it is the only route that takes a POST. The
  // site is otherwise read-only and rejects every other verb at the door; keeping that
  // guard where it is, and letting exactly one path past it, is narrower than relaxing it
  // to "GET, HEAD, POST" for every URL the site serves.
  //
  // It serves the measurement lab, is bearer-token gated, and disables itself unless
  // configured — see lab_api.ts for why it exists and why the Android app must never call
  // it. It does its own method handling.
  if (path === "/lab/assess") return handleAssess(request, SECURITY_HEADERS);

  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", {
      status: 405,
      headers: { allow: "GET, HEAD", ...SECURITY_HEADERS },
    });
  }

  if (path.startsWith("/static/")) return serveStatic(path);

  switch (path) {
    case "":
    case "/":
      return html(homePage());

    case "/privacy":
    case "/privacy-policy":
      return html(privacyPage());

    case "/health":
      return new Response("ok", {
        headers: { "content-type": "text/plain; charset=utf-8", ...SECURITY_HEADERS },
      });

    case "/robots.txt":
      return new Response(
        `User-agent: *\nAllow: /\nSitemap: ${url.origin}/sitemap.xml\n`,
        {
          headers: { "content-type": "text/plain; charset=utf-8", ...SECURITY_HEADERS },
        },
      );

    case "/sitemap.xml":
      return new Response(
        `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>${url.origin}/</loc><priority>1.0</priority></url>
  <url><loc>${url.origin}/privacy</loc><priority>0.5</priority></url>
</urlset>`,
        {
          headers: { "content-type": "application/xml; charset=utf-8", ...SECURITY_HEADERS },
        },
      );

    default:
      return html(notFoundPage(), 404);
  }
}

// Guarded so the handler can be imported by tests without binding a port.
if (import.meta.main) {
  Deno.serve(handler);
}
