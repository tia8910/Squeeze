/**
 * The brand sheet's tokens, as CSS.
 *
 * Values are transcribed from the same source the Android app uses, so the site and the app
 * are the same product rather than two things with a shared name. Where the app has a
 * Kotlin constant, this has a custom property with the identical hex.
 *
 * Inlined into every page rather than served as a separate file. The whole stylesheet is a
 * few kilobytes, so a second round trip would cost more than it saves, and it removes any
 * chance of a page rendering unstyled while the CSS is still in flight.
 */
export const CSS = `
:root {
  --navy: #081c45;
  --blue: #1768ff;
  --blue-deep: #0e4bd8;
  --ice: #f4f7ff;
  --muted: #69738a;
  --line: #e7ebf3;
  --body: #34415c;
  --card: #ffffff;
  --ground: #ffffff;
  --sunken: #fafbfe;
  --radius-card: 24px;
  --radius-tile: 16px;
  --maxw: 1080px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --navy: #e9effb;
    --blue: #4c8cff;
    --blue-deep: #1768ff;
    --ice: #13233f;
    --muted: #93a0ba;
    --line: #22304a;
    --body: #b4c0d6;
    --card: #101a2c;
    --ground: #07101f;
    --sunken: #16223a;
  }
}

* { box-sizing: border-box; }

html { -webkit-text-size-adjust: 100%; }

body {
  margin: 0;
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, system-ui, sans-serif;
  color: var(--navy);
  background: var(--ground);
  line-height: 1.6;
  /* Aligned to the app's own rendering, which sets its type a little tighter than default. */
  letter-spacing: -0.01em;
}

/*
 * Horizontal padding only, and set as longhand on purpose. Several elements carry both
 * .wrap and a layout class that sets vertical padding; the shorthand here would win on
 * specificity and silently reset their top and bottom to zero, collapsing the spacing
 * between every section.
 */
.wrap { max-width: var(--maxw); margin: 0 auto; padding-left: 24px; padding-right: 24px; }

/* --- Header --- */
header {
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  background: color-mix(in srgb, var(--ground) 88%, transparent);
  backdrop-filter: blur(10px);
  z-index: 10;
}
.bar { display: flex; align-items: center; justify-content: space-between; height: 68px; }
.brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: inherit; }
.brand img { width: 34px; height: 34px; }
.brand span { font-weight: 800; font-size: 19px; letter-spacing: -0.5px; }
.brand span i { color: var(--blue); font-style: normal; }
.bar nav { display: flex; gap: 22px; align-items: center; }
.bar nav a { color: var(--muted); text-decoration: none; font-size: 14px; font-weight: 600; }
.bar nav a:hover { color: var(--blue); }

/* --- Hero --- */
.hero { text-align: center; padding-top: 72px; padding-bottom: 56px; }
.hero img.mark { width: 132px; height: 132px; }
.hero h1 {
  font-size: clamp(40px, 8vw, 68px);
  font-weight: 900;
  letter-spacing: -0.05em;
  margin: 20px 0 0;
  line-height: 1;
}
.hero h1 i { color: var(--blue); font-style: normal; }
.tagline {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.42em;
  margin: 20px 0 0;
  text-transform: uppercase;
}
.tagline i { color: var(--blue); font-style: normal; }
.lede {
  font-size: clamp(17px, 2.4vw, 20px);
  color: var(--body);
  max-width: 620px;
  margin: 26px auto 0;
}

/* --- Buttons --- */
.cta { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; margin-top: 32px; }
.btn {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  border-radius: var(--radius-tile);
  padding: 15px 28px;
  font-weight: 800;
  font-size: 15px;
  text-decoration: none;
  border: 1.5px solid transparent;
}
.btn.primary { background: linear-gradient(135deg, var(--blue), var(--blue-deep)); color: #fff; }
.btn.secondary { border-color: #8db3ff; color: var(--blue); }
.btn:focus-visible { outline: 3px solid var(--blue); outline-offset: 3px; }

/* --- Cards --- */
.grid { display: grid; gap: 16px; }
.grid.four { grid-template-columns: repeat(4, 1fr); }
.grid.three { grid-template-columns: repeat(3, 1fr); }
.grid.two { grid-template-columns: repeat(2, 1fr); }

.card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  padding: 24px;
}
.card h3 { margin: 0 0 8px; font-size: 17px; letter-spacing: -0.02em; }
.card p { margin: 0; color: var(--muted); font-size: 14px; }
.card svg { width: 30px; height: 30px; stroke: var(--blue); fill: none; stroke-width: 2; margin-bottom: 14px; }

section { padding-top: 56px; padding-bottom: 56px; }
section > h2 {
  font-size: clamp(26px, 4vw, 34px);
  font-weight: 800;
  letter-spacing: -0.03em;
  margin: 0 0 10px;
}
section > .sub { color: var(--muted); margin: 0 0 28px; max-width: 640px; font-size: 15px; }

/* --- Emphasis panel --- */
.panel {
  background: var(--ice);
  border-radius: var(--radius-card);
  padding: 34px;
}
.panel h2 { font-size: clamp(24px, 3.4vw, 30px); margin-top: 0; }
.panel .note { font-size: 14px; color: var(--muted); margin-bottom: 0; }

code.perm {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 7px;
  padding: 2px 7px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* --- Prose, for the policy page --- */
.prose { max-width: 720px; }
.prose h2 { font-size: 22px; margin: 36px 0 10px; letter-spacing: -0.02em; }
.prose h2:first-of-type { margin-top: 8px; }
.prose p, .prose li { color: var(--body); font-size: 15px; }
.prose ul { padding-left: 20px; }
.prose li { margin-bottom: 7px; }
.prose strong { color: var(--navy); }
.updated { color: var(--muted); font-size: 14px; }

/* --- Footer --- */
footer { border-top: 1px solid var(--line); padding: 34px 0; margin-top: 34px; }
.footrow { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; align-items: center; }
footer a { color: var(--muted); text-decoration: none; font-size: 14px; margin-right: 18px; }
footer a:hover { color: var(--blue); }
.copy { color: var(--muted); font-size: 14px; }

@media (max-width: 900px) {
  .grid.four { grid-template-columns: repeat(2, 1fr); }
  .grid.three, .grid.two { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .grid.four { grid-template-columns: 1fr; }
  .bar nav { gap: 14px; }
  .bar nav a.hide-sm { display: none; }
  .hero { padding-top: 48px; padding-bottom: 40px; }
  .tagline { letter-spacing: 0.22em; }
}
`;
