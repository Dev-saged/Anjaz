import sys, uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

def outline(font_path, text, size=200, tracking=0):
    blob = hb.Blob.from_file_path(font_path)
    face = hb.Face(blob); font = hb.Font(face)
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties(); buf.language = "ar"
    hb.shape(font, buf, {"kern": True, "liga": True})
    tt = TTFont(font_path); gs = tt.getGlyphSet(); order = tt.getGlyphOrder()
    scale = size / tt["head"].unitsPerEm
    x = 0; pen = SVGPathPen(gs); bp = BoundsPen(gs)
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        name = order[info.codepoint]
        t = (scale, 0, 0, -scale, (x + pos.x_offset) * scale, -pos.y_offset * scale)
        gs[name].draw(TransformPen(pen, t)); gs[name].draw(TransformPen(bp, t))
        x += pos.x_advance + tracking
    return pen.getCommands(), bp.bounds

if __name__ == "__main__":
    d, b = outline(sys.argv[1], sys.argv[2])
    print(b)
