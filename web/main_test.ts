import { handler } from "./main.ts";
import { parseAssessment } from "./lab_api.ts";

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

/**
 * The lab assessment endpoint.
 *
 * Nothing here calls the model. What is worth testing is the gate: an endpoint that forwards
 * images to a paid vision API from a deployed public site is an open relay if it is wrong
 * about who may use it, and the failure is silent — it works perfectly for the attacker.
 */

function post(path: string, init: RequestInit = {}): Promise<Response> {
  return Promise.resolve(handler(new Request(`https://squeeze.fit${path}`, { method: "POST", ...init })));
}

Deno.test("lab assessment refuses anything but POST", async () => {
  const response = await get("/lab/assess");

  assert(response.status === 405, `expected 405, got ${response.status}`);
  assert(response.headers.get("allow") === "POST", "allow header missing");
});

Deno.test("lab assessment is absent unless the deployment configures it", async () => {
  // The ordinary public deployment has neither variable, and must not imply a key exists.
  Deno.env.delete("ANTHROPIC_API_KEY");
  Deno.env.delete("LAB_TOKEN");

  const response = await post("/lab/assess", { body: "{}" });

  assert(response.status === 503, `expected 503, got ${response.status}`);
});

Deno.test("lab assessment rejects a wrong or missing token", async () => {
  Deno.env.set("ANTHROPIC_API_KEY", "test-key");
  Deno.env.set("LAB_TOKEN", "correct-horse");
  try {
    const missing = await post("/lab/assess", { body: "{}" });
    assert(missing.status === 401, `no token: expected 401, got ${missing.status}`);

    const wrong = await post("/lab/assess", {
      body: "{}",
      headers: { authorization: "Bearer wrong" },
    });
    assert(wrong.status === 401, `wrong token: expected 401, got ${wrong.status}`);
  } finally {
    Deno.env.delete("ANTHROPIC_API_KEY");
    Deno.env.delete("LAB_TOKEN");
  }
});

Deno.test("lab assessment validates its payload before spending a call", async () => {
  Deno.env.set("ANTHROPIC_API_KEY", "test-key");
  Deno.env.set("LAB_TOKEN", "correct-horse");
  const auth = { authorization: "Bearer correct-horse", "content-type": "application/json" };
  try {
    const notJson = await post("/lab/assess", { body: "not json", headers: auth });
    assert(notJson.status === 400, `bad json: got ${notJson.status}`);

    const noImage = await post("/lab/assess", {
      body: JSON.stringify({ sex: "male", mediaType: "image/jpeg" }),
      headers: auth,
    });
    assert(noImage.status === 400, `no image: got ${noImage.status}`);

    const badType = await post("/lab/assess", {
      body: JSON.stringify({ imageBase64: "AA", mediaType: "image/gif", sex: "male" }),
      headers: auth,
    });
    assert(badType.status === 400, `bad media type: got ${badType.status}`);

    // Sex decides which ladder is used, and the two are about ten points apart at equivalent
    // appearance. Defaulting it would silently report every woman far leaner than she is.
    const noSex = await post("/lab/assess", {
      body: JSON.stringify({ imageBase64: "AA", mediaType: "image/jpeg" }),
      headers: auth,
    });
    assert(noSex.status === 400, `no sex: got ${noSex.status}`);

    const huge = await post("/lab/assess", {
      body: JSON.stringify({
        imageBase64: "A".repeat(9 * 1024 * 1024),
        mediaType: "image/jpeg",
        sex: "male",
      }),
      headers: auth,
    });
    assert(huge.status === 413, `oversized: got ${huge.status}`);
  } finally {
    Deno.env.delete("ANTHROPIC_API_KEY");
    Deno.env.delete("LAB_TOKEN");
  }
});

Deno.test("an assessment survives being wrapped in prose or a fence", () => {
  const fenced = parseAssessment(
    'Here is my read:\n```json\n{"percentLow": 15, "percentHigh": 18, "markers": ["flat abdomen"], "confidence": "high", "limits": ""}\n```\nHope that helps.',
  );

  assert(fenced !== null, "a fenced reply should still parse");
  assert(fenced!.percentLow === 15 && fenced!.percentHigh === 18, "range lost");
  assert(fenced!.markers.length === 1, "markers lost");
});

Deno.test("an assessment without a usable range is not an assessment", () => {
  // A paid call that came back as encouragement rather than a judgement must fail loudly
  // rather than default to a number nobody chose.
  assert(parseAssessment("You look great!") === null, "prose accepted");
  assert(parseAssessment('{"markers": ["lean"]}') === null, "missing range accepted");
  assert(
    parseAssessment('{"percentLow": 30, "percentHigh": 12}') === null,
    "inverted range accepted",
  );
  assert(
    parseAssessment('{"percentLow": 0.2, "percentHigh": 1}') === null,
    "impossible range accepted",
  );
});
