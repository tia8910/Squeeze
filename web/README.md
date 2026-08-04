# Squeeze.fit website

The public site: what the app does, how its accuracy claims are framed, and the privacy
policy a Play Store listing has to link to.

## This is not a backend

The Android app is built **without** `android.permission.INTERNET`. That is its central
privacy claim, and the only reason the claim is worth anything is that Android enforces it —
the app physically cannot open a network connection.

So this server must never become something the app talks to. No sync endpoint, no account
system, no telemetry collector, no crash reporting. Adding any of those would require adding
the internet permission to the app, which would quietly destroy the one guarantee it makes.

If a feature ever seems to need a server, the answer is to do it on the device or not do it.

## Running it

```sh
deno task dev     # http://localhost:8000, reloads on change
deno task check   # type check
deno task test    # route tests
deno lint
```

No dependencies. `Deno.serve` is built in, so there is no import map to resolve and no
third-party module that can change under a deployment.

## Deploying

On Deno Deploy, in the project's build configuration:

| Setting | Value |
| --- | --- |
| Runtime Configuration | **Dynamic App** |
| Entrypoint | `web/main.ts` |
| Arguments | *(empty)* |
| Runtime Working Directory | *(empty — defaults to the repo root)* |
| Install command | *(empty)* |
| Build command | *(empty)* |
| Pre-deploy command | *(empty)* |

The entrypoint is case-sensitive and relative to the working directory. `Main.ts` will fail;
it is `web/main.ts`.

Nothing needs building — there is no bundler, no framework and no generated output. Deno
Deploy runs `main.ts` directly, and `Deno.serve` binds the port the platform provides.

Static files are resolved relative to the module (`import.meta.url`), not to the process
working directory, so they are found regardless of where the app is started from.

## Routes

| Path | Purpose |
| --- | --- |
| `/` | Landing page |
| `/privacy`, `/privacy-policy` | Privacy policy |
| `/static/*` | Logo assets |
| `/robots.txt`, `/sitemap.xml` | Crawler metadata |
| `/health` | Plain-text readiness check |

## Assets

`static/logo.png` and `static/logo-dark.png` are copies of the app's drawables, so the site
and the app show the same mark. If the logo is ever replaced with a higher-resolution export,
replace it in both places:

- `app/src/main/res/drawable-xxhdpi/logo_squeeze.png`
- `web/static/logo.png`

## Layout notes

Two things here are load-bearing and easy to undo by accident:

- **`.wrap` sets horizontal padding as longhand.** Several elements carry both `.wrap` and a
  layout class that sets vertical padding. Using the `padding` shorthand here wins on
  specificity and silently resets their top and bottom to zero, collapsing the spacing
  between every section.
- **The CSS is inlined into each page.** It is a few kilobytes, so a second round trip would
  cost more than it saves, and it means a page can never render unstyled.
