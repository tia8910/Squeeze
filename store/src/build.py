"""Renders the Play Store graphics in store/ with headless Chromium.

Run from the repository root: python3 store/src/build.py
The screens show the app's real layout and features with sample numbers.
"""
import pathlib
import subprocess

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "store"
SRC = OUT / "src"
FONT = (ROOT / "app/src/main/res/font").as_uri()
ICON = (OUT / "icon-512.png").as_uri()
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"

CSS = f"""
@font-face {{ font-family: J; src: url({FONT}/jakarta_regular.ttf); font-weight: 400; }}
@font-face {{ font-family: J; src: url({FONT}/jakarta_medium.ttf); font-weight: 500; }}
@font-face {{ font-family: J; src: url({FONT}/jakarta_semibold.ttf); font-weight: 600; }}
@font-face {{ font-family: J; src: url({FONT}/jakarta_bold.ttf); font-weight: 700; }}
@font-face {{ font-family: J; src: url({FONT}/jakarta_extrabold.ttf); font-weight: 800; }}
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
body {{ font-family: J; color: #081C45; overflow: hidden; }}
.shot {{ width: 1080px; height: 1920px; position: relative; overflow: hidden;
  background: radial-gradient(900px 700px at 85% 10%, #2f7dff 0%, transparent 60%),
              radial-gradient(800px 800px at 0% 100%, #0e4bd8 0%, transparent 60%),
              linear-gradient(160deg, #0b2a6b 0%, #081C45 70%); }}
.cap {{ position: absolute; top: 120px; left: 90px; right: 90px; color: #fff; }}
.cap .kick {{ font-weight: 700; font-size: 30px; letter-spacing: 6px; color: #8DB3FF; }}
.cap h1 {{ font-weight: 800; font-size: 84px; line-height: 1.08; margin-top: 22px; }}
.cap p {{ font-size: 36px; line-height: 1.4; color: #c9d8f5; margin-top: 24px; }}
.phone {{ position: absolute; left: 110px; right: 110px; top: 620px; height: 1500px;
  background: #F6F8FC; border-radius: 70px; border: 14px solid #0a1630;
  box-shadow: 0 40px 120px rgba(0,0,0,.45); overflow: hidden; padding: 70px 44px 0; }}
.bar {{ display: flex; justify-content: space-between; font-weight: 600; font-size: 24px; color: #34415C; margin-bottom: 34px; }}
.title {{ font-weight: 800; font-size: 46px; }}
.sub {{ font-size: 26px; color: #5B6479; margin-top: 6px; }}
.card {{ background: #fff; border-radius: 36px; padding: 34px; margin-top: 26px;
  box-shadow: 0 6px 24px rgba(8,28,69,.06); border: 1px solid #E7EBF3; }}
.hero {{ background: linear-gradient(135deg, #36A0FF, #0757EA 60%, #081C45); color: #fff; border: none; }}
.label {{ font-weight: 700; font-size: 20px; letter-spacing: 3px; color: #1768FF; }}
.hero .label {{ color: rgba(255,255,255,.8); }}
.row {{ display: flex; align-items: center; gap: 22px; }}
.big {{ font-weight: 800; font-size: 72px; }}
.muted {{ color: #5B6479; }}
.tiles {{ display: flex; gap: 20px; margin-top: 22px; }}
.tile {{ flex: 1; background: #fff; border-radius: 30px; padding: 26px; border: 1px solid #E7EBF3; }}
.tile b {{ display: block; font-size: 40px; font-weight: 800; margin-top: 6px; }}
.tile span {{ font-size: 22px; color: #5B6479; }}
.btn {{ background: #1768FF; color: #fff; border-radius: 999px; text-align: center; padding: 28px; font-weight: 700; font-size: 30px; margin-top: 26px; }}
.ring {{ width: 230px; height: 230px; border-radius: 50%; display: grid; place-items: center; flex: none; }}
.ring i {{ width: 186px; height: 186px; border-radius: 50%; display: grid; place-items: center; font-style: normal; text-align: center; }}
.mrow {{ display: flex; align-items: center; justify-content: space-between; padding: 20px 0; border-bottom: 1px solid #E7EBF3; font-size: 28px; font-weight: 600; }}
.mrow:last-child {{ border: none; }}
.track {{ width: 260px; height: 16px; background: #EEF2F9; border-radius: 99px; overflow: hidden; }}
.track div {{ height: 100%; border-radius: 99px; }}
.pill {{ width: 110px; text-align: center; font-size: 20px; font-weight: 700; padding: 8px 16px; border-radius: 99px; }}
.check {{ width: 54px; height: 54px; border-radius: 18px; background: #E8F0FF; display: grid; place-items: center; flex: none; }}
.priv {{ display: flex; gap: 24px; align-items: center; padding: 22px 0; border-bottom: 1px solid #E7EBF3; }}
.priv:last-child {{ border: none; }}
.priv b {{ font-size: 29px; display: block; }}
.priv span {{ font-size: 23px; color: #5B6479; }}
.week {{ display: flex; justify-content: space-between; margin-top: 26px; }}
.day {{ width: 96px; padding: 18px 0; border-radius: 26px; text-align: center; background: #fff; border: 1px solid #E7EBF3; font-weight: 700; font-size: 24px; }}
.day small {{ display: block; font-size: 20px; color: #5B6479; font-weight: 500; margin-top: 4px; }}
.day.on {{ background: #1768FF; color: #fff; border-color: #1768FF; }}
.day.on small {{ color: #d8e6ff; }}
.set {{ display: flex; justify-content: space-between; align-items: center; padding: 20px 0; border-bottom: 1px solid #E7EBF3; font-size: 27px; }}
.set:last-child {{ border: none; }}
.next {{ color: #16A34A; font-weight: 700; font-size: 23px; }}
.meal {{ display: flex; gap: 24px; padding: 18px 0; }}
.dot {{ width: 22px; height: 22px; border-radius: 50%; background: #1768FF; margin-top: 8px; flex: none; box-shadow: 0 0 0 8px #E8F0FF; }}
.meal b {{ font-size: 28px; display: block; }}
.meal span {{ font-size: 23px; color: #5B6479; }}
"""

CHECK = '<svg width="30" height="30" viewBox="0 0 24 24"><path d="M5 12.5l4.2 4.2L19 7" fill="none" stroke="#1768FF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'
SHIELD = '<svg width="120" height="120" viewBox="0 0 24 24"><path d="M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5z" fill="rgba(255,255,255,.18)" stroke="#fff" stroke-width="1.4"/><path d="M8 12l3 3 5-6" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'


def ring(pct, inner, color="#fff", track="rgba(255,255,255,.22)", bg="transparent"):
    return (f'<div class="ring" style="background: conic-gradient({color} {pct}%, {track} 0)">'
            f'<i style="background:{bg}">{inner}</i></div>')


def bar(label, pct, color, tag, tag_bg, tag_fg):
    return (f'<div class="mrow"><span style="width:190px">{label}</span>'
            f'<div class="track"><div style="width:{pct}%;background:{color}"></div></div>'
            f'<span class="pill" style="background:{tag_bg};color:{tag_fg}">{tag}</span></div>')


STATUS = '<div class="bar"><span>9:41</span><span>●●● ▮</span></div>'

SCREENS = {
    "1-scan": ("ONE PHOTO", "Your body fat from a single photo",
               "Body fat and lean mass from a front photo and your weight.", f"""
        {STATUS}
        <div class="title">Good morning</div><div class="sub">Your body this week</div>
        <div class="card hero"><div class="label">BODY FAT</div>
          <div class="row" style="margin-top:18px">
            {ring(62, '<span style="font-size:46px;font-weight:800">18.4%</span>', bg="#0b4fe0")}
            <div><div style="font-size:26px;opacity:.85">Trend</div>
              <div style="font-size:40px;font-weight:800">down 0.6%</div>
              <div style="font-size:24px;opacity:.85;margin-top:6px">over 4 weeks of real change,<br>not daily noise</div></div>
          </div></div>
        <div class="tiles"><div class="tile"><span>Weight</span><b>76.0 kg</b></div>
          <div class="tile"><span>Lean mass</span><b>62.0 kg</b></div></div>
        <div class="btn">Scan my body</div>
        <div class="card"><div class="label">ANALYSED ON THIS PHONE</div>
          <div class="sub" style="font-size:25px;margin-top:10px">The photo never leaves your device.</div></div>
    """),
    "2-physique": ("PHYSIQUE ANALYSIS", "See what to bring up next",
                   "Every muscle group read from your scan, matched to your goal.", f"""
        {STATUS}
        <div class="title">Physique</div><div class="sub">From today's scan</div>
        <div class="card">
          {bar("Shoulders", 82, "#1768FF", "Strong", "#E8F0FF", "#0E4BD8")}
          {bar("Arms", 76, "#1768FF", "Strong", "#E8F0FF", "#0E4BD8")}
          {bar("Back", 58, "#36A0FF", "Solid", "#EEF2F9", "#34415C")}
          {bar("Chest", 55, "#36A0FF", "Solid", "#EEF2F9", "#34415C")}
          {bar("Legs", 34, "#F59E0B", "Focus", "#FEF3C7", "#92400E")}
          {bar("Abs", 28, "#F59E0B", "Focus", "#FEF3C7", "#92400E")}
        </div>
        <div class="card" style="border-color:#F59E0B55;background:#FFFBEB">
          <div class="label" style="color:#B45309">YOUR FOCUS</div>
          <div style="font-size:32px;font-weight:800;margin-top:12px">1. Legs</div>
          <div class="sub">Two lower body days added to your week</div>
          <div style="font-size:32px;font-weight:800;margin-top:18px">2. Abs</div>
          <div class="sub">Shows as body fat drops, keep the deficit</div>
        </div>
    """),
    "3-privacy": ("PRIVATE BY DESIGN", "Your photos never leave your phone",
                  "No account. No ads. No tracking. Everything stays encrypted on your device.", f"""
        {STATUS}
        <div class="card hero" style="text-align:center;padding:44px">{SHIELD}
          <div style="font-size:40px;font-weight:800;margin-top:14px">Privacy centre</div>
          <div style="font-size:25px;opacity:.85;margin-top:6px">What happens to your data</div></div>
        <div class="card">
          <div class="priv"><div class="check">{CHECK}</div><div><b>Analysed on this phone</b><span>Scans run on device, no cloud</span></div></div>
          <div class="priv"><div class="check">{CHECK}</div><div><b>Photos never uploaded</b><span>Not to us, not to anyone</span></div></div>
          <div class="priv"><div class="check">{CHECK}</div><div><b>Encrypted storage</b><span>Locked with your fingerprint or face</span></div></div>
          <div class="priv"><div class="check">{CHECK}</div><div><b>No ads, no analytics</b><span>Nothing tracks what you do</span></div></div>
          <div class="priv"><div class="check">{CHECK}</div><div><b>Backup is your choice</b><span>Only to a hidden folder in your own Drive</span></div></div>
        </div>
    """),
    "4-training": ("TRAINING", "A week built for your body",
                   "Gym, calisthenics, running or yoga. Coached set by set.", f"""
        {STATUS}
        <div class="title">This week</div><div class="sub">Recomp, 4 days, evening training</div>
        <div class="week">
          <div class="day">M<small>Upper</small></div><div class="day on">T<small>Legs</small></div>
          <div class="day">W<small>Rest</small></div><div class="day">T<small>Push</small></div>
          <div class="day">F<small>Pull</small></div><div class="day">S<small>Run</small></div>
          <div class="day">S<small>Rest</small></div></div>
        <div class="card hero"><div class="label">TODAY</div>
          <div style="font-size:44px;font-weight:800;margin-top:10px">Legs and core</div>
          <div style="font-size:25px;opacity:.85;margin-top:6px">6 exercises · about 55 min</div></div>
        <div class="card">
          <div class="set"><div><b>Back squat</b><div class="muted" style="font-size:23px">4 sets of 8 at 80 kg</div></div><span class="next">Next 82.5 kg</span></div>
          <div class="set"><div><b>Romanian deadlift</b><div class="muted" style="font-size:23px">3 sets of 10 at 70 kg</div></div><span class="next">Hold</span></div>
          <div class="set"><div><b>Walking lunge</b><div class="muted" style="font-size:23px">3 sets of 12 each side</div></div><span class="next">Add 2 reps</span></div>
          <div class="set"><div><b>Hanging leg raise</b><div class="muted" style="font-size:23px">3 sets of 12</div></div><span class="next">Next 15</span></div>
        </div>
    """),
    "5-nutrition": ("NUTRITION", "Meals from foods you actually like",
                    "Calories and macros from your body, timed around your training.", f"""
        {STATUS}
        <div class="title">Today's fuel</div><div class="sub">Training day</div>
        <div class="card"><div class="row">
          {ring(70, '<div><div style="font-size:52px;font-weight:800">2,350</div><div class="muted" style="font-size:22px">kcal</div></div>', color="#1768FF", track="#EEF2F9", bg="#fff")}
          <div style="flex:1">
            <div class="mrow"><span>Protein</span><span>165 g</span></div>
            <div class="mrow"><span>Carbs</span><span>250 g</span></div>
            <div class="mrow"><span>Fat</span><span>70 g</span></div></div></div></div>
        <div class="card"><div class="label">MEALS</div>
          <div class="meal"><div class="dot"></div><div><b>Breakfast · 7:30</b><span>Oats, Greek yogurt, berries</span></div></div>
          <div class="meal"><div class="dot"></div><div><b>Lunch · 13:00</b><span>Chicken, rice, roasted vegetables</span></div></div>
          <div class="meal"><div class="dot"></div><div><b>Before training · 17:30</b><span>Banana and whey shake</span></div></div>
          <div class="meal"><div class="dot"></div><div><b>Dinner · 20:30</b><span>Salmon, potatoes, salad</span></div></div>
        </div>
        <div class="card"><div class="label">11 MICRONUTRIENTS TRACKED</div>
          <div class="sub" style="margin-top:8px">Vitamin D is low this week. Add eggs or oily fish.</div></div>
    """),
}

FEATURE = f"""
<div style="width:1024px;height:500px;position:relative;overflow:hidden;color:#fff;
  background: radial-gradient(500px 400px at 90% 0%, #2f7dff, transparent 65%),
              linear-gradient(135deg,#0b2a6b,#081C45 70%);">
  <img src="{ICON}" style="position:absolute;right:90px;top:110px;width:280px;height:280px;border-radius:64px;box-shadow:0 30px 80px rgba(0,0,0,.45)">
  <div style="position:absolute;left:70px;top:92px;width:560px">
    <div style="font-weight:700;font-size:20px;letter-spacing:5px;color:#8DB3FF">SQUEEZE FIT</div>
    <div style="font-weight:800;font-size:56px;line-height:1.08;margin-top:16px">Body fat from one photo. Coached on your phone.</div>
    <div style="display:inline-flex;align-items:center;gap:12px;margin-top:28px;background:rgba(255,255,255,.12);
      border:1px solid rgba(255,255,255,.25);border-radius:99px;padding:14px 24px;font-weight:700;font-size:22px">
      🔒 Photos never leave your phone</div>
  </div>
</div>"""


def render(name, body, w, h):
    html = SRC / f"{name}.html"
    png = OUT / f"{name}.png"
    html.write_text(f"<!doctype html><meta charset=utf-8><style>{CSS}</style><body>{body}</body>")
    subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--hide-scrollbars", "--disable-gpu",
                    f"--window-size={w},{h + 300}", f"--screenshot={png}", html.as_uri()],
                   check=True, capture_output=True)
    html.unlink()
    # Headless Chromium's viewport is shorter than its window, so render tall and crop.
    Image.open(png).crop((0, 0, w, h)).convert("RGB").save(png)


if __name__ == "__main__":
    render("feature-1024x500", FEATURE, 1024, 500)
    for name, (kick, head, sub, screen) in SCREENS.items():
        body = (f'<div class="shot"><div class="cap"><div class="kick">{kick}</div><h1>{head}</h1><p>{sub}</p></div>'
                f'<div class="phone">{screen}</div></div>')
        render(f"screenshot-{name}", body, 1080, 1920)
    print("done")
