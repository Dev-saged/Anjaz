"""يولّد docs/media/update-flow.gif من نموذج الشاشة الرئيسية: بانر التحديث بحالاته ثم إشعار «تم التحميل»."""
import os, shutil, subprocess, sys, tempfile
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PROTO = os.path.join(ROOT, "brand", "prototype")
OUT = os.path.join(ROOT, "docs", "media")
CHROME = shutil.which("google-chrome") or shutil.which("chromium")

CSS = """
@font-face{font-family:"Alexandria";src:url(fonts/Alexandria-Bold.ttf);font-weight:600 700}
.ub{position:absolute;inset-inline:12px;top:34px;z-index:50;border-radius:22px;overflow:hidden;color:#fff;
 background:linear-gradient(135deg,var(--navy),var(--navy-3));border:1px solid oklch(.72 .16 158/.35);
 box-shadow:0 14px 30px -8px oklch(.2 .07 255/.6)}
.ub .r{display:flex;align-items:center;gap:12px;padding:12px 14px;min-height:76px}
.ub .ic{width:44px;height:44px;border-radius:50%;background:oklch(.72 .16 158/.16);display:grid;place-items:center;flex:none}
.ub .t{flex:1}.ub h4{font:700 15px "Alexandria";line-height:1.4}.ub p{font-size:12px;opacity:.82}
.ub .bt{height:44px;padding:0 18px;border-radius:16px;background:var(--em);color:oklch(.2 .04 255);font-weight:600;display:grid;place-items:center}
.ub .ln{height:4px;background:oklch(1 0 0/.14)}.ub .ln i{display:block;height:100%;background:var(--em)}
.ub svg{width:26px;height:26px;fill:none;stroke:var(--em);stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.scrim{position:absolute;inset:0;background:oklch(.15 .03 255/.55);z-index:40}
.nt{position:absolute;inset-inline:12px;top:44px;z-index:60;background:#fff;border-radius:20px;padding:12px 14px;box-shadow:0 12px 28px -10px #000a}
.nt small{display:flex;align-items:center;gap:6px;color:var(--mute);font-size:11px}
.nt small b{width:14px;height:14px;border-radius:4px;background:var(--navy);display:block}
.nt h5{font:600 14px "Plex Ar";margin-top:4px}.nt p{font-size:12px;color:var(--mute)}
"""
CHECK = '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'
CLOUD = '<svg viewBox="0 0 24 24"><path d="M7 18a4 4 0 0 1-.6-7.96A5.5 5.5 0 0 1 17 8.5 4.8 4.8 0 0 1 17.5 18z"/><path d="M12 11v5m-2-2l2 2 2-2"/></svg>'


def ring(p):
    return (f'<svg viewBox="0 0 44 44" style="width:44px;height:44px"><circle cx="22" cy="22" r="19" stroke="oklch(1 0 0/.18)" stroke-width="3.5" fill="none"/>'
            f'<circle cx="22" cy="22" r="19" stroke="oklch(.72 .16 158)" stroke-width="3.5" fill="none" stroke-linecap="round" '
            f'stroke-dasharray="{119.4*p/100:.1f} 119.4" transform="rotate(-90 22 22)"/></svg>')


def banner(icon, title, sub, btn=None, pct=None):
    b = f'<div class="bt">{btn}</div>' if btn else ""
    l = f'<div class="ln"><i style="width:{pct}%"></i></div>' if pct is not None else ""
    return f'<div class="ub"><div class="r"><div class="ic">{icon}</div><div class="t"><h4>{title}</h4><p>{sub}</p></div>{b}</div>{l}</div>'


V = '<span class="num">0.3.0</span>'
FRAMES = [
    (banner(CLOUD, f"تحديث جديد {V}", "مزايا وإصلاحات جاهزة للتحميل", "تحميل"), 1700),
    (banner(ring(12), "جارٍ التنزيل", 'يستمر في الخلفية — <span class="num">12%</span>', None, 12), 650),
    (banner(ring(47), "جارٍ التنزيل", 'يستمر في الخلفية — <span class="num">47%</span>', None, 47), 650),
    (banner(ring(86), "جارٍ التنزيل", 'يستمر في الخلفية — <span class="num">86%</span>', None, 86), 650),
    (banner(CHECK, "تم التحميل", f"النسخة {V} محفوظة وجاهزة للتثبيت", "تثبيت"), 2000),
    ('<div class="scrim"></div><div class="nt"><small><b></b>إنجاز · الآن</small>'
     f'<h5>تم تحميل إنجاز {V}</h5><p>النسخة جاهزة، اضغط للتثبيت</p></div>', 2300),
]


def main():
    work = tempfile.mkdtemp()
    shutil.copytree(os.path.join(PROTO, "fonts"), os.path.join(work, "fonts"))
    shutil.copy(os.path.join(ROOT, "brand", "tools", "fonts", "Alexandria-Bold.ttf"), os.path.join(work, "fonts"))
    html = open(os.path.join(PROTO, "home-a.html"), encoding="utf-8").read()
    imgs, durs = [], []
    for i, (inner, ms) in enumerate(FRAMES):
        page = html.replace("</style>", CSS + "</style>", 1)
        page = page.replace('<div class="phone">', '<div class="phone">' + inner, 1)
        f = os.path.join(work, f"f{i}.html")
        open(f, "w", encoding="utf-8").write(page)
        png = os.path.join(work, f"f{i}.png")
        subprocess.run([CHROME, "--headless", "--no-sandbox", "--hide-scrollbars", "--force-device-scale-factor=1.5",
                        f"--screenshot={png}", "--window-size=440,820", "--virtual-time-budget=2000", "file://" + f],
                       check=True, capture_output=True)
        imgs.append(Image.open(png).convert("RGB"))
        durs.append(ms)
    os.makedirs(OUT, exist_ok=True)
    pal = [im.quantize(256, method=Image.Quantize.MEDIANCUT) for im in imgs]
    pal[0].save(os.path.join(OUT, "update-flow.gif"), save_all=True, append_images=pal[1:], duration=durs, loop=0, optimize=True)
    imgs[4].save(os.path.join(OUT, "update-ready.png"), optimize=True)
    imgs[5].save(os.path.join(OUT, "update-notification.png"), optimize=True)
    imgs[0].save(os.path.join(OUT, "update-available.png"), optimize=True)


if __name__ == "__main__":
    sys.exit(main())
