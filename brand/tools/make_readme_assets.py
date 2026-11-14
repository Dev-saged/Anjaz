"""يولّد docs/readme/*.svg: بطاقات المزايا، أزرار التواصل، العناوين، شارة غزة. الخطوط مضمّنة (مجموعة جزئية)."""
import base64, io, os, re
from fontTools import subset
from fontTools.ttLib import TTFont
from xml.sax.saxutils import escape

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs", "readme")
FONTS = {
    "AnjD": os.path.join(ROOT, "app/src/main/res/font/alexandria_bold.ttf"),
    "AnjB": os.path.join(ROOT, "app/src/main/res/font/plex_ar_medium.ttf"),
}
DRAW = os.path.join(ROOT, "app/src/main/res/drawable")
NAVY, NAVY2, EM = "#102E52", "#1d4a7c", "#24C27D"

ICONS = {
    "tasks": '<rect x="4" y="4" width="16" height="16" rx="4"/><path d="M8.5 12.5l2.5 2.5 4.5-5"/>',
    "sos": '<path d="M12 3.5l9 16H3z"/><path d="M12 10v4M12 17v.4"/>',
    "chat": '<path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-7l-4 3v-3H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"/>',
    "map": '<path d="M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/>',
    "report": '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 13h6M9 17h6"/>',
    "offline": '<path d="M3 3l18 18M5 10a10 10 0 0 1 4-2.5M19 10a10 10 0 0 0-2.5-2M8.5 14a5 5 0 0 1 3-1.3M15.5 14a5 5 0 0 0-1-.9"/><circle cx="12" cy="18" r="1"/>',
    "download": '<path d="M12 4v11m-4-4l4 4 4-4M5 20h14"/>',
    "image": '<rect x="3.5" y="4.5" width="17" height="15" rx="3"/><circle cx="9" cy="10" r="1.6"/><path d="M4 17l5-4 4 3 3-2 4 3"/>',
    "dev": '<path d="M9 8l-4 4 4 4M15 8l4 4-4 4M13.5 6l-3 12"/>',
}
FEATURES = [
    ("tasks", "المهام", "إسناد ومتابعة بدورة حالة كاملة"),
    ("sos", "الاستغاثة", "نداء بالموقع وإشعار عاجل فوري"),
    ("chat", "المحادثات", "غرف مباشرة مع إيصالات قراءة"),
    ("map", "الدليل الميداني", "من المنطقة إلى المربع إلى المخيم"),
    ("report", "التقارير", "PDF وExcel ونسخ احتياطي بضغطة"),
    ("offline", "بلا إنترنت", "حفظ محلي ومزامنة عند عودة الاتصال"),
]
CONTACTS = [
    ("whatsapp", "واتساب", "+972 59 785 7250"),
    ("instagram", "إنستغرام", "sajedd.0"),
    ("facebook", "فيسبوك", "saged.t"),
    ("x", "إكس", "saged_al"),
    ("github", "غيت هب", "Dev-saged"),
]
HEADINGS = [("features", "image", "المزايا"), ("ui", "image", "الواجهة"), ("install", "download", "التحميل"), ("dev", "dev", "المطوّر")]

texts = {"AnjD": set(), "AnjB": set()}


def t(font, s):
    texts[font].update(s)
    return s


def social_paths(name):
    x = open(os.path.join(DRAW, f"ic_social_{name}.xml"), encoding="utf-8").read()
    out = []
    for m in re.finditer(r"<path(.*?)/>", x, re.S):
        a = m.group(1)
        d = re.search(r'pathData="([^"]*)"', a).group(1)
        filled = 'fillColor="#FFFFFFFF"' in a
        out.append(f'<path d="{d}" fill="#fff" stroke="none"/>' if filled else f'<path d="{d}" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>')
    return "".join(out)


def icon_g(paths, cx, cy, size, stroke="#fff", w=1.8, own=False):
    sc = size / 24
    attrs = "" if own else f' fill="none" stroke="{stroke}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"'
    return f'<g transform="translate({cx - size / 2} {cy - size / 2}) scale({sc})"{attrs}>{paths}</g>'


def defs(uid):
    return (f'<defs><linearGradient id="g{uid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{NAVY2}"/><stop offset="1" stop-color="{NAVY}"/></linearGradient>'
            f'<linearGradient id="e{uid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2fd18b"/><stop offset="1" stop-color="#1fa86c"/></linearGradient></defs>')


def wrap(w, h, body, uid):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{{STYLE}}{defs(uid)}{body}</svg>'


files = {}
for i, (ic, title, sub) in enumerate(FEATURES):
    w, h = 244, 118
    body = (f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="22" fill="url(#g{i})" stroke="{EM}" stroke-opacity=".28"/>'
            f'<circle cx="{w - 40}" cy="40" r="22" fill="{EM}" fill-opacity=".16"/>' + icon_g(ICONS[ic], w - 40, 40, 26, EM, 1.9) +
            f'<text x="{w - 74}" y="46" text-anchor="end" font-family="AnjD" font-size="17" fill="#fff">{escape(t("AnjD", title))}</text>'
            f'<text x="{w - 20}" y="92" text-anchor="end" font-family="AnjB" font-size="12.5" fill="#fff" fill-opacity=".8">{escape(t("AnjB", sub))}</text>')
    files[f"feature-{ic}.svg"] = wrap(w, h, body, i)
for i, (k, label, handle) in enumerate(CONTACTS):
    w, h = 228, 64
    body = (f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="32" fill="url(#g{i})" stroke="{EM}" stroke-opacity=".3"/>'
            f'<circle cx="{w - 32}" cy="32" r="22" fill="url(#e{i})"/>' + icon_g(social_paths(k), w - 32, 32, 22, own=True) +
            f'<text x="{w - 64}" y="29" text-anchor="end" font-family="AnjD" font-size="15" fill="#fff">{escape(t("AnjD", label))}</text>'
            f'<text x="{w - 64}" y="46" text-anchor="end" font-family="AnjB" font-size="12" fill="#fff" fill-opacity=".78" direction="ltr" unicode-bidi="isolate">{escape(t("AnjB", handle))}</text>')
    files[f"contact-{k}.svg"] = wrap(w, h, body, i)
# شارة غزة
w, h = 420, 64
flag = ('<g transform="translate(0 0)"><rect width="30" height="6.67" fill="#000"/><rect y="6.67" width="30" height="6.66" fill="#fff"/>'
        '<rect y="13.33" width="30" height="6.67" fill="#007A3D"/><path d="M0 0L11 10 0 20z" fill="#CE1126"/></g>')
files["made-in-gaza.svg"] = wrap(w, h, (
    f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" rx="32" fill="url(#g9)" stroke="{EM}" stroke-opacity=".3"/>'
    f'<g transform="translate({w - 62} 22) scale(1)">{flag}</g>'
    f'<text x="{w - 78}" y="39" text-anchor="end" font-family="AnjD" font-size="16" fill="#fff">{escape(t("AnjD", "صُنع بكل حب من فلسطين — غزة"))}</text>'
    '<path transform="translate(28 20) scale(1.1)" fill="#FF5A82" d="M12 21s-7.5-4.6-9.6-9.2C0.9 8.4 2.9 4.5 6.6 4.5c2.1 0 3.6 1.2 4.4 2.5c0.8-1.3 2.3-2.5 4.4-2.5c3.7 0 5.7 3.9 4.2 7.3C19.5 16.4 12 21 12 21z"/>'), 9)
# زر التحميل
w, h = 340, 68
files["download.svg"] = wrap(w, h, (
    f'<rect width="{w}" height="{h}" rx="34" fill="url(#e8)"/>' + icon_g(ICONS["download"], w - 44, 34, 28, NAVY, 2.2) +
    f'<text x="{w - 76}" y="31" text-anchor="end" font-family="AnjD" font-size="18" fill="{NAVY}">{escape(t("AnjD", "تحميل التطبيق"))}</text>'
    f'<text x="{w - 76}" y="50" text-anchor="end" font-family="AnjB" font-size="12" fill="{NAVY}" fill-opacity=".8">{escape(t("AnjB", "أحدث إصدار موقّع للأندرويد"))}</text>'), 8)
for i, (k, ic, title) in enumerate(HEADINGS):
    w, h = 760, 64
    body = (f'<rect x="0" y="30" width="{w - 150}" height="2" rx="1" fill="{EM}" fill-opacity=".35"/>'
            f'<circle cx="{w - 30}" cy="32" r="22" fill="url(#g{i})"/>' + icon_g(ICONS[ic], w - 30, 32, 24, EM, 1.9) +
            f'<text x="{w - 62}" y="41" text-anchor="end" font-family="AnjD" font-size="24" fill="{NAVY}">{escape(t("AnjD", title))}</text>')
    files[f"heading-{k}.svg"] = wrap(w, h, body, i)


def face(name):
    f = TTFont(FONTS[name])
    opt = subset.Options()
    opt.layout_features = ["*"]
    opt.flavor = "woff2"
    opt.notdef_outline = True
    s = subset.Subsetter(opt)
    s.populate(text="".join(sorted(texts[name])) + " 0123456789")
    s.subset(f)
    buf = io.BytesIO()
    f.flavor = "woff2"
    f.save(buf)
    b64 = base64.b64encode(buf.getvalue()).decode()
    return f"@font-face{{font-family:'{name}';src:url(data:font/woff2;base64,{b64}) format('woff2')}}"


os.makedirs(OUT, exist_ok=True)
style = "<style>" + face("AnjD") + face("AnjB") + "text{font-feature-settings:'tnum'}</style>"
for n, svg in files.items():
    open(os.path.join(OUT, n), "w", encoding="utf-8").write(svg.replace("{STYLE}", style))
print(len(files), "svg")
