import { CSS } from "./styles.ts";

/** GitHub repository, used for the source and release links. */
const REPO = "https://github.com/tia8910/Squeeze";

/**
 * Escapes text destined for HTML.
 *
 * Everything rendered here is currently a literal in this file, so nothing is escaped out
 * of necessity today. It exists so that the first piece of dynamic content — a release tag,
 * a query parameter, anything read from a request — has an obvious correct thing to reach
 * for rather than being interpolated raw.
 */
export function esc(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function layout(opts: {
  title: string;
  description: string;
  path: string;
  body: string;
}): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(opts.title)}</title>
<meta name="description" content="${esc(opts.description)}">
<meta name="theme-color" content="#1768ff">
<meta property="og:title" content="${esc(opts.title)}">
<meta property="og:description" content="${esc(opts.description)}">
<meta property="og:type" content="website">
<meta property="og:image" content="/static/logo.png">
<link rel="icon" href="/static/logo.png" type="image/png">
<style>${CSS}</style>
</head>
<body>
<header>
  <div class="wrap bar">
    <a class="brand" href="/">
      <img src="/static/logo.png" alt="" width="34" height="34">
      <span>squeeze<i>.fit</i></span>
    </a>
    <nav>
      <a class="hide-sm" href="/#features">Features</a>
      <a class="hide-sm" href="/#accuracy">Accuracy</a>
      <a href="/privacy">Privacy</a>
      <a href="${REPO}" rel="noopener">Source</a>
    </nav>
  </div>
</header>
${opts.body}
<footer>
  <div class="wrap footrow">
    <div>
      <a href="/">Home</a>
      <a href="/privacy">Privacy policy</a>
      <a href="${REPO}" rel="noopener">Source code</a>
    </div>
    <div class="copy">Squeeze.fit · measurements stay on your phone.</div>
  </div>
</footer>
</body>
</html>`;
}

/** The four feature glyphs, taken from the brand sheet's own inline SVG. */
const GLYPHS = {
  scan: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4"/><circle cx="12" cy="12" r="3"/></svg>`,
  trends: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 17l6-6 4 4 8-9"/><path d="M15 6h6v6"/></svg>`,
  motivate:
    `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 21h8M12 17v4M7 4h10v4a5 5 0 01-10 0V4z"/><path d="M7 6H4v2a4 4 0 004 4M17 6h3v2a4 4 0 01-4 4"/></svg>`,
  privacy:
    `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l8 4v5c0 5-3.4 8.6-8 9-4.6-.4-8-4-8-9V7l8-4z"/><rect x="9" y="10" width="6" height="5" rx="1"/><path d="M10 10V9a2 2 0 014 0v1"/></svg>`,
};

export function homePage(): string {
  const body = `
<main>
  <div class="wrap hero">
    <img class="mark" src="/static/logo.png" alt="Squeeze.fit" width="132" height="132">
    <h1>squeeze<i>.fit</i></h1>
    <p class="tagline">Small steps. <i>Big change.</i></p>
    <p class="lede">
      Body composition tracking that runs entirely on your phone. Measure with a photo or a
      tape, and see what is actually changing, separated from the noise.
    </p>
    <div class="cta">
      <a class="btn primary" href="${REPO}/releases" rel="noopener">Get the app</a>
      <a class="btn secondary" href="#accuracy">How it works</a>
    </div>
  </div>

  <section class="wrap" id="features">
    <h2>What it does</h2>
    <p class="sub">Four things, done properly, instead of a dashboard of numbers nobody acts on.</p>
    <div class="grid four">
      <div class="card">${GLYPHS.scan}<h3>Smart Scan</h3><p>A body scan from one front photo. Side and back views are optional, and add measured depth when you use them.</p></div>
      <div class="card">${GLYPHS.trends}<h3>Track Trends</h3><p>A filter built for irregular, noisy measurements, so a real change is reported as one, and scatter is not.</p></div>
      <div class="card">${GLYPHS.motivate}<h3>Stay Motivated</h3><p>Consistency is what the maths needs, so consistency is what gets celebrated, in either direction.</p></div>
      <div class="card">${GLYPHS.privacy}<h3>Privacy First</h3><p>Photos never leave your phone. No analytics, no ads. Backup is optional, to your own Google Drive.</p></div>
    </div>
  </section>

  <section class="wrap" id="accuracy">
    <h2>Accuracy is not the same as repeatability</h2>
    <p class="sub">
      This distinction is the reason the app exists, and most tools in this category get it wrong.
    </p>
    <div class="grid two">
      <div class="card">
        <h3>Accuracy is a fixed offset</h3>
        <p>
          How far an estimate sits from a DEXA scan is mostly a personal, systematic offset.
          It barely moves between measurements, so when you compare yourself to yourself, it
          cancels out almost entirely.
        </p>
      </div>
      <div class="card">
        <h3>Repeatability is what hides a trend</h3>
        <p>
          Random scatter between readings is what actually buries a real change. So the trend
          filter weights each measurement by its <strong>precision</strong>, not by how close
          it lands to a lab result.
        </p>
      </div>
    </div>
    <div class="card" style="margin-top:16px">
      <h3>What that means in practice</h3>
      <p>
        Every estimate is shown with its confidence interval, and the app says
        <em>&ldquo;no confirmed change yet&rdquo;</em> until the movement is larger than your own
        measurement noise. It will not draw a confident arrow through scatter, that is the
        one thing guaranteed to mislead you about your own body.
      </p>
    </div>
  </section>

  <section class="wrap">
    <div class="panel">
      <h2>Your photos never leave your phone</h2>
      <p style="color:var(--body);max-width:640px">
        The body scan and every calculation run on your phone, with models built into the app.
        Photographs are stored encrypted and are never uploaded. The only connection the app
        ever makes is an optional backup of your numbers to a hidden folder in your own Google
        Drive, and only if you sign in.
      </p>
      <p class="note">
        No analytics, no ads, no servers of ours receiving your data.
      </p>
      <div class="cta" style="justify-content:flex-start;margin-top:22px">
        <a class="btn secondary" href="/privacy">Read the privacy policy</a>
      </div>
    </div>
  </section>

  <section class="wrap">
    <h2>How a scan works</h2>
    <p class="sub">Two models run on your device. Nothing is uploaded, and everything stays in the app's encrypted private storage.</p>
    <div class="grid three">
      <div class="card"><h3>1 &middot; Pose and silhouette</h3><p>A pose model locates your joints and a segmenter separates you from the background. The joints bound the search; the silhouette decides the exact level.</p></div>
      <div class="card"><h3>2 &middot; Real measurements</h3><p>Your stated height turns pixels into centimetres. Cross-sections are treated as ellipses rather than circles, which is what makes a side photo worth taking.</p></div>
      <div class="card"><h3>3 &middot; Photos kept, encrypted</h3><p>Your scan photo is saved so you can look back at it, encrypted with a key in your phone's secure hardware. It never leaves the device, and deleting a measurement deletes its photo.</p></div>
    </div>
  </section>
</main>`;

  return layout({
    title: "Squeeze.fit · body composition that stays on your phone",
    description:
      "Track body fat, training and nutrition from a photo or a tape measure. Photos are analysed on your phone and never uploaded. No ads, no analytics.",
    path: "/",
    body,
  });
}

export function privacyPage(): string {
  const body = `
<main class="wrap">
  <section class="prose">
    <h1 style="font-size:clamp(30px,5vw,42px);letter-spacing:-0.03em;margin-bottom:6px">Privacy policy</h1>
    <p class="updated">Last updated 4 October 2026</p>

    <p>
      Squeeze.fit is a body composition, training and nutrition app. Its body scan, physique
      analysis and every calculation run on your phone. This policy describes what the app and
      this website do with information. The short version: your photographs never leave your
      phone, we run no servers that receive your data, and the only network connection the app
      makes is an optional backup to your own Google Drive.
    </p>

    <h2>What the app stores on your phone</h2>
    <ul>
      <li><strong>Your profile:</strong> height, year of birth, the equation variant, your goal, sports, training days and time, and favourite foods.</li>
      <li><strong>Your measurements:</strong> weights, circumferences, skinfolds, body fat estimates and any reference results you enter.</li>
      <li><strong>Your training:</strong> logged sets, sessions and the physique scores from each scan.</li>
      <li><strong>Your scan photographs:</strong> encrypted, and described in full below.</li>
      <li><strong>Your settings:</strong> theme, sound and screenshot preferences.</li>
    </ul>
    <p>
      All of it is written to an encrypted database in the app's private storage, which other
      apps cannot read. Deleting the app deletes the data on the phone with it.
    </p>

    <h2>Photographs</h2>
    <p>
      Scan photographs are analysed on your phone by models built into the app. They are saved
      so you can look back at them, encrypted with a key held in your phone's secure hardware,
      and they do not appear in your gallery. <strong>Photographs are never uploaded</strong>,
      including to your Google Drive backup. Deleting a measurement deletes its photograph.
    </p>
    <p>
      The camera permission is used only while you take a scan photo or photograph a gym
      machine. You can pick an existing photo instead.
    </p>

    <h2>Google sign in and Drive backup (optional)</h2>
    <p>
      You can sign in with your Google account to back up your data. If you do:
    </p>
    <ul>
      <li>The app receives your Google account's name and email address, to show which account is signed in.</li>
      <li>It asks Google for the <code class="perm">drive.appdata</code> permission only. This lets the app read and write a hidden folder in your own Google Drive that only Squeeze.fit can see. It gives no access to any other file in your Drive.</li>
      <li>The backup contains your profile, measurements, training logs, plans and settings. It does not contain photographs.</li>
      <li>The backup is sent directly from your phone to Google over an encrypted connection. It is stored in your Google account, subject to <a href="https://policies.google.com/privacy" rel="noopener" style="color:var(--blue)">Google's privacy policy</a>. We do not receive a copy and cannot access it.</li>
    </ul>
    <p>
      If you do not sign in, the app makes no network connections. To stop backing up, choose
      Sign out in the app. To delete the backup, open Google Drive, go to Settings, then
      Manage apps, find Squeeze.fit and choose Delete hidden app data. You can also remove the
      app's access at <a href="https://myaccount.google.com/permissions" rel="noopener" style="color:var(--blue)">myaccount.google.com/permissions</a>.
    </p>

    <h2>Purchases</h2>
    <p>
      Purchases are handled by Google Play. We receive confirmation that a purchase was made,
      never your payment details.
    </p>

    <h2>What we do not do</h2>
    <ul>
      <li>No analytics, telemetry, crash reporting or usage tracking.</li>
      <li>No advertising and no advertising identifiers.</li>
      <li>No servers of our own that receive your data, and no accounts with us.</li>
      <li>No selling or sharing of data with anyone.</li>
    </ul>

    <h2>Sharing, when you choose it</h2>
    <p>
      The celebration screen has a Share button. It hands a short text summary (body fat
      percentage, entry count and days tracked) to Android's share sheet, and you pick where it
      goes. No photograph or measurement history is attached.
    </p>

    <h2>This website</h2>
    <p>
      This site serves static pages. It sets no cookies, runs no analytics and embeds no
      third party scripts. Our hosting provider processes standard request logs, such as IP
      address and user agent, to serve pages and prevent abuse.
    </p>

    <h2>Children</h2>
    <p>
      The app is not intended for children under 13, and the body composition equations it uses
      are not validated for them.
    </p>

    <h2>Your rights and deleting your data</h2>
    <p>
      Your data lives on your phone and, if you choose, in your own Google Drive. You can delete
      everything on the phone by deleting the app or clearing its storage in Android settings,
      and delete the backup as described above. We hold no copy to disclose, correct or delete.
    </p>

    <h2>Changes</h2>
    <p>
      If this policy changes, the revised version will be published here with a new date, and
      material changes will be noted in the app's release notes.
    </p>

    <h2>Contact</h2>
    <p>
      Questions about this policy can be raised as an issue on
      <a href="${REPO}/issues" rel="noopener" style="color:var(--blue)">the project's GitHub repository</a>.
    </p>
  </section>
</main>`;

  return layout({
    title: "Privacy policy · Squeeze.fit",
    description:
      "Squeeze.fit analyses your photos on your phone and never uploads them. The only connection it makes is an optional backup to your own Google Drive.",
    path: "/privacy",
    body,
  });
}

export function notFoundPage(): string {
  const body = `
<main class="wrap hero">
  <h1 style="font-size:64px;margin-bottom:0">404</h1>
  <p class="lede">That page does not exist.</p>
  <div class="cta"><a class="btn primary" href="/">Back to the start</a></div>
</main>`;

  return layout({
    title: "Not found · Squeeze.fit",
    description: "That page does not exist.",
    path: "/404",
    body,
  });
}
