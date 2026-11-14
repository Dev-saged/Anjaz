import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from outline_text import outline

OUT = os.path.join(os.path.dirname(__file__), "..")
FONT = os.path.join(os.path.dirname(__file__), "fonts", "Alexandria-Bold.ttf")

def oklch(l, c, h):
    a, b = c * math.cos(math.radians(h)), c * math.sin(math.radians(h))
    l_ = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m_ = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s_ = (l - 0.0894841775 * a - 1.2914855480 * b) ** 3
    rgb = (4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
           -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
           -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_)
    f = lambda x: 12.92 * x if x <= 0.0031308 else 1.055 * x ** (1 / 2.4) - 0.055
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(f(max(0, min(1, v))) * 255))) for v in rgb)

NAVY = oklch(0.30, 0.075, 255)
EMERALD = oklch(0.72, 0.16, 158)
INK = oklch(0.20, 0.04, 255)
SURFACE = oklch(0.98, 0.005, 250)

def symbol(width=32, gap=(46, -26), stone=40, rx=11, R=84, ring=NAVY, dot=EMERALD):
    cx = cy = 128
    p = lambda r, a: (cx + r * math.cos(math.radians(a)), cy - r * math.sin(math.radians(a)))
    ro, ri, cr = R + width / 2, R - width / 2, width / 2
    a1, a2 = gap
    f = lambda t: "%.2f %.2f" % t
    d = (f"M{f(p(ro,a1))}A{ro} {ro} 0 1 0 {f(p(ro,a2))}"
         f"A{cr} {cr} 0 0 0 {f(p(ri,a2))}"
         f"A{ri} {ri} 0 1 1 {f(p(ri,a1))}"
         f"A{cr} {cr} 0 0 0 {f(p(ro,a1))}Z")
    (ux, uy), (lx, ly) = p(R, a1), p(R, a2)
    sx, sy = (ux + lx) / 2 + width * 0.3, (uy + ly) / 2
    r = (f'<rect x="{sx - stone/2:.2f}" y="{sy - stone/2:.2f}" width="{stone}" height="{stone}" rx="{rx}" '
         f'transform="rotate(-8 {sx:.2f} {sy:.2f})" fill="{dot}"/>')
    return f'<path d="{d}" fill="{ring}"/>' + r

def svg(vb, body, title):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"><title>{title}</title>{body}</svg>\n'

def wordmark(fill, height):
    d, (x0, y0, x1, y1) = outline(FONT, "إنجاز", 200)
    s = height / (y1 - y0)
    return d, (x0, y0, x1, y1), s, f'<path d="{d}" fill="{fill}" transform="translate({-x0*s:.2f} {-y0*s:.2f}) scale({s:.5f})"/>', (x1 - x0) * s

def write(name, content):
    with open(os.path.join(OUT, name), "w") as fh: fh.write(content)

write("anjaz-symbol.svg", svg("0 0 256 256", symbol(), "إنجاز"))
write("anjaz-symbol-small.svg", svg("0 0 256 256", symbol(width=42, gap=(56, -36), stone=50, rx=13, R=80), "إنجاز"))

H = 186
_, _, s, wm, wm_w = wordmark(NAVY, H)
gap = 56
W = wm_w + gap + 256
body = f'<g transform="translate({wm_w + gap:.2f} 0)">{symbol()}</g><g transform="translate(0 {(256-H)/2:.2f})">{wm}</g>'
write("anjaz-horizontal.svg", svg(f"0 0 {W:.0f} 256", body, "إنجاز"))

H2 = 120
_, _, s2, wm2, wm2_w = wordmark(NAVY, H2)
W2 = max(256, wm2_w)
body = (f'<g transform="translate({(W2-256)/2:.2f} 0)">{symbol()}</g>'
        f'<g transform="translate({(W2-wm2_w)/2:.2f} 312)">{wm2}</g>')
write("anjaz-stacked.svg", svg(f"0 0 {W2:.0f} {312+H2:.0f}", body, "إنجاز"))

_, _, _, wmw, _ = wordmark(NAVY, 150)
write("anjaz-wordmark.svg", svg(f"0 0 {wm_w:.0f} 150", wmw, "إنجاز"))
for n, v in [("navy", NAVY), ("emerald", EMERALD), ("ink", INK), ("surface", SURFACE)]: print(n, v)

_, _, _, wm_l, _ = wordmark(SURFACE, H)
body = f'<g transform="translate({wm_w + gap:.2f} 0)">{symbol(ring=SURFACE)}</g><g transform="translate(0 {(256-H)/2:.2f})">{wm_l}</g>'
write("anjaz-horizontal-on-dark.svg", svg(f"0 0 {W:.0f} 256", body, "إنجاز"))
write("anjaz-symbol-on-dark.svg", svg("0 0 256 256", symbol(ring=SURFACE), "إنجاز"))
