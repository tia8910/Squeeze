/**
 * The coach's eye, for the measurement lab only.
 *
 * ## Why this exists
 *
 * The Android scan reads two silhouette widths and maps their ratio onto adiposity. That
 * ratio has been wrong five times on the same body — 36.6%, 8.00%, 6.22%, 6.83%, 5.0% — and
 * each failure was a band landing on something that was not the body part it was named after:
 * the ribs, the arms, a waistband, the length of a sideways frame.
 *
 * Underneath those bugs is a limit that fixing bands does not remove. **A width ratio cannot
 * see what a coach sees.** A coach does not measure anything; they read markers — whether the
 * upper abdominals separate, whether the flank folds when relaxed, whether the oblique line
 * is visible, how the skin sits over the lower back — and place the body on a ladder they
 * have calibrated against hundreds of others. Two people with an identical waist-to-hip ratio
 * can be eight points apart, and the coach is right about which is which.
 *
 * This endpoint asks a vision model to do that, in that order: name the markers, then place
 * the body. Not "guess a number" — the marker list is the working, and it is returned so a
 * reading can be argued with rather than merely believed.
 *
 * ## What it is not
 *
 * **The app never calls this.** The Android build has no `android.permission.INTERNET`; that
 * is its central privacy claim and the only reason the claim is worth anything is that
 * Android enforces it. Adding a network call to the scan would end that, and the Play data
 * safety declaration would have to say that photographs of the user's body are uploaded.
 *
 * So this serves the **lab** and nothing else. Its output is ground truth for a labelled
 * corpus — the thing that has been missing all along, and the reason anchors have been fitted
 * to one person's judgement of one photograph at a time. What ships to users is not this
 * endpoint; it is better constants derived from what this endpoint labelled.
 *
 * ## Why it is gated
 *
 * The public site is deployed. An unauthenticated endpoint that forwards images to a paid
 * vision API is an open relay on someone else's bill, and worse, an invitation to send other
 * people's photographs through it. It requires a bearer token, and it disables itself
 * entirely unless both the token and the model key are configured — so the ordinary
 * deployment simply does not offer it.
 */

/**
 * Model that does the assessment. Overridable so a cheaper one can be tried.
 *
 * Read lazily rather than into a top-level constant: a module-level `Deno.env.get` runs on
 * import, so every consumer — including `deno test`, which only wants the pure parser —
 * would need `--allow-env` merely to load the file.
 */
function model(): string {
  return Deno.env.get("LAB_VISION_MODEL") ?? "claude-opus-5";
}

const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
const ANTHROPIC_VERSION = "2023-06-01";

/** Largest image accepted, before base64 expansion. A phone photo is well under this. */
const MAX_IMAGE_BYTES = 6 * 1024 * 1024;

const ALLOWED_MEDIA_TYPES = ["image/jpeg", "image/png", "image/webp"];

export interface CoachAssessment {
  /** The range a coach would actually say out loud, rather than a false point estimate. */
  percentLow: number;
  percentHigh: number;
  /** The visible evidence, in the coach's own terms. This is the working, not decoration. */
  markers: string[];
  /** "high" | "moderate" | "low" — how much the photograph itself supports a judgement. */
  confidence: string;
  /** What would make the next photograph a better one to judge. */
  limits: string;
  model: string;
}

/**
 * The assessment brief.
 *
 * Written as a method rather than a request for a number, because the failure mode of asking
 * a model for a percentage is that it produces a confident, well-formatted average of its
 * training data with no relationship to the person in the photograph. Requiring the markers
 * first, and requiring them to be things actually visible in *this* image, is what keeps the
 * answer attached to the body.
 *
 * The ladders are sex-specific and about ten points apart at equivalent appearance. Women
 * carry more essential fat, so a woman and a man who look equally lean are not the same
 * number — judging a woman on the male ladder reports her roughly ten points leaner than she
 * is, which is the single most common way this kind of assessment goes wrong.
 */
function brief(sex: "male" | "female", context: string): string {
  const maleLadder = `
  6-8%   deep separation, visible vascularity across the abdomen, serratus clearly defined
  9-12%  full abdominal definition relaxed, obliques visible, no flank fold
  13-15% upper abdominals defined, lower ones faint, flat flank
  16-19% flat but undefined abdomen, faint outline at most, slight flank softness
  20-24% no definition, small belly, visible flank fold when relaxed
  25-29% rounded abdomen, clear flank fold, chest begins to soften
  30%+   abdomen protrudes past the chest line in profile`;

  const femaleLadder = `
  14-17% deep definition, visible vascularity, very little hip and thigh softness
  18-21% abdominal definition relaxed, defined obliques, tight glute-hamstring tie-in
  22-25% flat abdomen, faint upper definition, soft but shaped hips and thighs
  26-30% flat to slightly soft abdomen, no definition, curved hips and thighs
  31-35% soft abdomen, visible flank, fuller hips and upper thighs
  36-40% rounded abdomen, clear flank fold, fuller throughout
  41%+   abdomen and hips carry visible depth in profile`;

  return `You are an experienced physique coach making a visual body-fat assessment from a
photograph, the way you would for a client standing in front of you.

Work in this order and do not skip the first step.

1. MARKERS. List only what you can actually see in THIS photograph. Abdominal definition and
   how far down it runs. Whether the obliques are visible. Whether the flank folds when
   relaxed. Chest and shoulder definition. Vascularity. How the skin sits at the lower back
   and above the waistband. If lighting, clothing, distance or pose hides a marker, say that
   instead of inferring it — a marker you cannot see is not evidence.

2. PLACEMENT. Put the body on this ladder for a ${sex} subject:
${sex === "male" ? maleLadder : femaleLadder}

3. RANGE. Give the range you would actually say out loud. A coach says "about fifteen to
   seventeen", not "16.2". Two to four points wide. Wider if the photograph is poor.

Be accurate rather than kind. Reading someone lean when they are not is the failure that
makes an assessment worthless — it is what the tool you are correcting has done five times.
Equally, do not overcorrect: a genuinely lean subject must read lean.

${context}

Reply with JSON only, no prose around it:
{"percentLow": number, "percentHigh": number, "markers": [string], "confidence": "high" | "moderate" | "low", "limits": string}`;
}

/** Whether the endpoint is configured at all. */
export function labApiEnabled(): boolean {
  return Boolean(Deno.env.get("ANTHROPIC_API_KEY") && Deno.env.get("LAB_TOKEN"));
}

function json(body: unknown, status: number, headers: Record<string, string>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

/**
 * Handles `POST /lab/assess`.
 *
 * @param securityHeaders the site's standard headers, applied here too
 */
export async function handleAssess(
  request: Request,
  securityHeaders: Record<string, string>,
): Promise<Response> {
  if (request.method !== "POST") {
    return json({ error: "POST only" }, 405, { allow: "POST", ...securityHeaders });
  }

  // Disabled rather than broken when unconfigured, so the public deployment simply does not
  // offer it and no key is ever implied to exist.
  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  const token = Deno.env.get("LAB_TOKEN");
  if (!apiKey || !token) {
    return json({ error: "lab assessment is not configured on this deployment" }, 503, securityHeaders);
  }

  const offered = request.headers.get("authorization");
  if (offered !== `Bearer ${token}`) {
    return json({ error: "unauthorised" }, 401, securityHeaders);
  }

  let payload: {
    imageBase64?: string;
    mediaType?: string;
    sex?: string;
    heightCm?: number;
    weightKg?: number;
  };
  try {
    payload = await request.json();
  } catch {
    return json({ error: "body must be JSON" }, 400, securityHeaders);
  }

  const { imageBase64, mediaType, sex } = payload;
  if (!imageBase64) return json({ error: "imageBase64 is required" }, 400, securityHeaders);
  if (!mediaType || !ALLOWED_MEDIA_TYPES.includes(mediaType)) {
    return json(
      { error: `mediaType must be one of ${ALLOWED_MEDIA_TYPES.join(", ")}` },
      400,
      securityHeaders,
    );
  }
  if (sex !== "male" && sex !== "female") {
    return json({ error: "sex must be male or female" }, 400, securityHeaders);
  }

  // base64 carries four characters per three bytes.
  if ((imageBase64.length * 3) / 4 > MAX_IMAGE_BYTES) {
    return json({ error: "image too large" }, 413, securityHeaders);
  }

  // Height and weight are offered because they narrow a judgement the way they would for a
  // coach who knows them — a given outline at 60 kg and at 95 kg is not the same body fat.
  const context = [
    payload.heightCm ? `The subject is ${payload.heightCm} cm tall.` : "",
    payload.weightKg ? `The subject weighs ${payload.weightKg} kg.` : "",
  ].filter(Boolean).join(" ") ||
    "No height or weight is available; judge from the photograph alone.";

  const upstream = await fetch(ANTHROPIC_URL, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": ANTHROPIC_VERSION,
    },
    body: JSON.stringify({
      model: model(),
      max_tokens: 1024,
      messages: [{
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mediaType, data: imageBase64 } },
          { type: "text", text: brief(sex, context) },
        ],
      }],
    }),
  });

  if (!upstream.ok) {
    // The upstream body can carry the key back in an error echo, so it is not forwarded.
    return json(
      { error: `assessment failed upstream (${upstream.status})` },
      502,
      securityHeaders,
    );
  }

  const completion = await upstream.json();
  const text: string = completion?.content?.[0]?.text ?? "";

  const parsed = parseAssessment(text);
  if (!parsed) {
    return json({ error: "could not parse the assessment", raw: text }, 502, securityHeaders);
  }

  return json({ ...parsed, model: model() } satisfies CoachAssessment, 200, securityHeaders);
}

/**
 * Pulls the JSON object out of a reply.
 *
 * Tolerant of a fenced block or a stray sentence around it, because a hard failure on a
 * well-formed judgement wrapped in one line of prose would throw away a paid call for
 * nothing. Not tolerant of missing numbers: an assessment without a range is not one.
 */
export function parseAssessment(
  text: string,
): Omit<CoachAssessment, "model"> | null {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;

  let raw: Record<string, unknown>;
  try {
    raw = JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }

  const low = Number(raw.percentLow);
  const high = Number(raw.percentHigh);
  if (!Number.isFinite(low) || !Number.isFinite(high)) return null;
  if (low < 2 || high > 70 || high < low) return null;

  return {
    percentLow: low,
    percentHigh: high,
    markers: Array.isArray(raw.markers) ? raw.markers.map(String) : [],
    confidence: typeof raw.confidence === "string" ? raw.confidence : "moderate",
    limits: typeof raw.limits === "string" ? raw.limits : "",
  };
}
