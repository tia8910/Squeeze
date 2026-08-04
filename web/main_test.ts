import { handler } from "./main.ts";

/**
 * Route behaviour, including the cases that are easy to get wrong and invisible in review:
 * path traversal out of the static directory, and method handling.
 */

function get(path: string, method = "GET"): Promise<Response> {
  return Promise.resolve(handler(new Request(`https://squeeze.fit${path}`, { method })));
}

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

Deno.test("home page renders with the brand and the tagline", async () => {
  const response = await get("/");
  const body = await response.text();

  assert(response.status === 200, `expected 200, got ${response.status}`);
  assert(body.includes("squeeze"), "wordmark missing");
  assert(body.includes("Small steps."), "tagline missing");
  assert(
    response.headers.get("content-type")?.includes("text/html") === true,
    "wrong content type",
  );
});

Deno.test("privacy policy is reachable and states the core claim", async () => {
  for (const path of ["/privacy", "/privacy-policy", "/privacy/"]) {
    const response = await get(path);
    const body = await response.text();

    assert(response.status === 200, `${path} returned ${response.status}`);
    assert(
      body.includes("android.permission.INTERNET"),
      `${path} does not name the permission the whole claim rests on`,
    );
  }
});

Deno.test("unknown paths 404 rather than erroring", async () => {
  const response = await get("/nope");
  assert(response.status === 404, `expected 404, got ${response.status}`);
});

Deno.test("static assets are served with the right type", async () => {
  const response = await get("/static/logo.png");
  assert(response.status === 200, `expected 200, got ${response.status}`);
  assert(
    response.headers.get("content-type") === "image/png",
    `wrong content type: ${response.headers.get("content-type")}`,
  );
  await response.arrayBuffer();
});

Deno.test("path traversal cannot escape the static directory", async () => {
  // Every one of these would read application source if the path were simply appended to
  // the static root, which is why the filename is rebuilt from the final segment instead.
  const attempts = [
    "/static/../main.ts",
    "/static/../../README.md",
    "/static/%2e%2e/main.ts",
    "/static/..%2Fmain.ts",
    "/static/subdir/../main.ts",
  ];

  for (const attempt of attempts) {
    const response = await get(attempt);
    assert(
      response.status === 404,
      `${attempt} returned ${response.status} instead of 404`,
    );

    const body = await response.text();
    assert(
      !body.includes("Deno.serve"),
      `${attempt} leaked source code`,
    );
  }
});

Deno.test("non-idempotent methods are refused", async () => {
  const response = await get("/", "POST");
  assert(response.status === 405, `expected 405, got ${response.status}`);
  assert(response.headers.get("allow") === "GET, HEAD", "missing Allow header");
  await response.text();
});

Deno.test("robots and sitemap are absolute and consistent", async () => {
  const robots = await (await get("/robots.txt")).text();
  assert(robots.includes("https://squeeze.fit/sitemap.xml"), "sitemap URL not absolute");

  const sitemap = await (await get("/sitemap.xml")).text();
  assert(sitemap.includes("https://squeeze.fit/privacy"), "privacy URL missing");
});

Deno.test("security headers are present on every response", async () => {
  for (const path of ["/", "/privacy", "/nope", "/static/logo.png"]) {
    const response = await get(path);
    assert(
      response.headers.get("x-content-type-options") === "nosniff",
      `${path} missing nosniff`,
    );
    assert(
      response.headers.get("x-frame-options") === "DENY",
      `${path} missing frame protection`,
    );
    await response.arrayBuffer();
  }
});
