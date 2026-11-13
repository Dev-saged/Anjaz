import re, os
root = os.path.join(os.path.dirname(__file__), "..", "prototype")
src = open(os.path.join(root, "screens.html")).read() + open(os.path.join(root, "home-a.html")).read()
syms = {}
for m in re.finditer(r'<symbol id="([\w-]+)"[^>]*>(.*?)</symbol>', src, re.S):
    syms.setdefault(m.group(1), m.group(2))

def num(x): return ("%g" % float(x))
def conv(body):
    out = []
    for t in re.finditer(r'<(path|rect|circle)\b([^>]*)/>', body):
        kind, attrs = t.group(1), dict(re.findall(r'([\w-]+)="([^"]*)"', t.group(2)))
        if kind == "path": out.append(attrs["d"])
        elif kind == "circle":
            cx, cy, r = float(attrs["cx"]), float(attrs["cy"]), float(attrs["r"])
            out.append(f"M{num(cx-r)},{num(cy)}a{num(r)},{num(r)} 0 1,0 {num(2*r)},0a{num(r)},{num(r)} 0 1,0 {num(-2*r)},0z")
        else:
            x, y, w, h = (float(attrs[k]) for k in "x y width height".split()); r = float(attrs.get("rx", 0))
            out.append(f"M{num(x+r)},{num(y)}H{num(x+w-r)}a{num(r)},{num(r)} 0 0 1 {num(r)},{num(r)}V{num(y+h-r)}a{num(r)},{num(r)} 0 0 1 {num(-r)},{num(r)}H{num(x+r)}a{num(r)},{num(r)} 0 0 1 {num(-r)},{num(-r)}V{num(y+r)}a{num(r)},{num(r)} 0 0 1 {num(r)},{num(-r)}z")
    return out

names = ["home","tasks","chat","more","bell","plus","sos","search","back","user","lock","mic","send","clip","play","meet","report","map","shield","log","cloud","gear","loc","check","img"]
camel = lambda n: "".join(p.capitalize() for p in n.split("-"))
lines = ['package com.anjaz.app.ui.icons', '',
 'import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.SolidColor', 'import androidx.compose.ui.graphics.StrokeCap', 'import androidx.compose.ui.graphics.StrokeJoin',
 'import androidx.compose.ui.graphics.vector.ImageVector', 'import androidx.compose.ui.graphics.vector.addPathNodes', 'import androidx.compose.ui.unit.dp', '',
 'object AnjazIcons {']
for n in names:
    ds = conv(syms[n]); mirror = "true" if n == "back" else "false"
    args = ",\n            ".join('"%s"' % d for d in ds)
    lines.append(f'    val {camel(n)}: ImageVector by lazy {{\n        build("{n}", {mirror},\n            {args})\n    }}\n')
lines += ['    val all: Map<String, ImageVector> by lazy {', '        mapOf(' + ", ".join(f'"{n}" to {camel(n)}' for n in names) + ')', '    }', '',
 '    private fun build(name: String, mirror: Boolean, vararg paths: String): ImageVector =',
 '        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {',
 '            paths.forEach {',
 '                addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)',
 '            }',
 '        }.build()', '}', '']
out = os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/java/com/anjaz/app/ui/icons/Icons.kt")
os.makedirs(os.path.dirname(out), exist_ok=True); open(out, "w").write("\n".join(lines))
print(len(names), "icons ->", out)
