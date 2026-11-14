# الخلفية — إنجاز

خلفية مجانية بلا بطاقة: Firebase Spark (Auth + Firestore + FCM) و Cloudflare Workers + D1.

## المحتوى
- `firebase/firestore.rules`: قواعد العزل بين المناطق والمربعات والمحادثات الخاصة. اختباراتها على المحاكي: `cd backend/firebase && npm install && npm test`.
- `worker/`: واجهة `/v1` (إنشاء الحسابات وإعادة الضبط والتعطيل، المجموعات والاجتماعات، الإشعارات، الوسائط، النسخ الاحتياطي) ومهام Cron (تصعيد المهام المتأخرة والتذكير كل 15 دقيقة، نسخ يومي). اختباراتها: `cd backend/worker && npm test`.

## الإعداد لمرة واحدة (ساجد)
1. Firebase: أنشئ مشروعاً على Spark، فعّل Authentication (Email/Password) وFirestore (وضع الإنتاج) وCloud Messaging. حمّل `google-services.json` وضعه في `app/` (إعداده عام بطبيعته؛ الحماية في القواعد).
2. انشر القواعد: `cd backend/firebase && npx firebase deploy --only firestore:rules --project <PROJECT_ID>`.
3. حساب خدمة: Project settings ← Service accounts ← Generate key. لا تضعه في المستودع.
4. Cloudflare (مجاني): `cd backend/worker && npm install && npx wrangler d1 create anjaz`، ضع `database_id` و`FIREBASE_PROJECT_ID` في `wrangler.toml`، ثم `npx wrangler d1 execute anjaz --remote --file=schema.sql`.
5. السر: `npx wrangler secret put SERVICE_ACCOUNT_JSON` والصق ملف المفتاح كاملاً، ثم `npx wrangler deploy`.
6. أول مدير مركزي يُنشأ يدوياً: أضف مستخدماً في Firebase Auth بالبريد `<username>@users.anjaz.local`، وأنشئ وثيقة `users/<uid>` في Firestore بالحقول `role=CENTRAL, active=true, username, displayName` (regionId وsquareId فارغان). كل ما بعده يُنشأ من التطبيق عبر الـ Worker.

## حالة النشر (2026-11-14)
- D1 باسم `anjaz` أُنشئ في حساب Cloudflare وطُبّق عليه `schema.sql` (المعرّف في `wrangler.toml`).
- الـ Worker منشور على https://anjaz-api.dev-mr07.workers.dev (و`WORKER_URL` مضبوط). المتبقي: مشروع Firebase، `FIREBASE_PROJECT_ID` ثم إعادة النشر، والسر `SERVICE_ACCOUNT_JSON`.

## حدود الخطة المجانية (تحقق منها قبل الإنتاج، فهي تتغير)
- Firestore Spark: 50 ألف قراءة و20 ألف كتابة يومياً. القواعد التي تستدعي `get()` تُحتسب قراءات إضافية.
- التصعيد يقرأ المهام المفتوحة فقط كل 15 دقيقة (96 تشغيلاً يومياً × عدد المهام المفتوحة).
- D1: صفّ واحد حتى 2MB؛ الوسائط ≤ 1.5MB وتُحذف بعد `MEDIA_TTL_DAYS`. النسخ الاحتياطي يشمل المناطق والمربعات والمخيمات والمستخدمين والمهام، ولا يشمل الرسائل بعد.

## ما لم يُجرَّب فعلياً
الاختبارات تحاكي واجهات Google (Identity Toolkit وFCM وFirestore REST) ولم تُشغَّل على مشروع Firebase حقيقي. أول نشر يحتاج مروراً يدوياً: إنشاء حساب، تسجيل دخول، إشعار فعلي.
