# Handoff — v0.8.1
- الحالة: فرع feature/v080-geo يحوي خريطة حيّة، حضور GPS، مهام متكررة، تقرير أسبوعي، صور قبل/بعد، مكالمات WebRTC، قفل بصمة، بحث شامل. الـ Worker منشور.
- قرارات: osmdroid+OSM؛ WebRTC mesh حتى 4 بإشارات Firestore؛ STUN فقط؛ لا خدمة أمامية للمكالمات.
- مفتوح: لم يُبنَ/يُجرَّب على جهاز؛ قواعد Firestore تُنشر يدوياً من backend/firebase/firestore.rules؛ أسرار GitHub (CLOUDFLARE_*) يدوية؛ المكالمات خلف NAT صارم تحتاج TURN.
- الخطوة التالية: جرّب على جهاز (خريطة، مكالمات، قفل، بحث، تحديث ABI)؛ حجم arm64 ≈ 20MB.
