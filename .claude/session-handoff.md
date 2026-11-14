# Handoff — v0.8.0
- الحالة: فرع feature/v080-geo يحوي خريطة حيّة، حضور GPS، مهام متكررة، تقرير أسبوعي، صور قبل/بعد، مكالمات WebRTC، قفل بصمة، بحث شامل. الـ Worker منشور.
- قرارات: osmdroid+OSM؛ WebRTC mesh حتى 4 بإشارات Firestore؛ STUN فقط؛ لا خدمة أمامية للمكالمات.
- مفتوح: لم يُبنَ/يُجرَّب على جهاز؛ قواعد Firestore تُنشر يدوياً من backend/firebase/firestore.rules؛ أسرار GitHub (CLOUDFLARE_*) يدوية؛ المكالمات خلف NAT صارم تحتاج TURN.
- الخطوة التالية: تأكد من نجاح CI على آخر commit، ادمج الفرع إلى main، ثم جرّب على جهاز.
