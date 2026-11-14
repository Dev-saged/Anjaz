# دليل النشر — إنجاز

## التطبيق (تلقائي)
1. ادفع إلى `main` فيبني `build.yml` ويوقّع وينشر Release باسم `vX.Y.Z-bN` مع `version.json`.
2. الأجهزة المثبَّتة تكتشف النسخة الجديدة من «التحديثات» وتنزّلها.
3. الأسرار المطلوبة: `KEYSTORE_BASE64`، `SIGNING_STORE_PASSWORD`، `SIGNING_KEY_ALIAS`، `SIGNING_KEY_PASSWORD`.
4. لا تغيّر مفتاح التوقيع بعد أول إصدار وإلا لن يعمل التحديث فوق النسخة القديمة.

## الـ Worker
- تلقائي: `deploy-worker.yml` يتطلب السرّين `CLOUDFLARE_API_TOKEN` و`CLOUDFLARE_ACCOUNT_ID`.
- يدوي: `cd backend/worker && npx wrangler deploy` مع المتغيّرين في البيئة فقط، دون حفظهما في ملف.
- Firebase: انشر القواعد من `backend/firebase` ثم شغّل اختباراتها.

## قبل كل إصدار
- ارفع `VERSION_NAME` و`VERSION_CODE` في `gradle.properties` وانسخ الرقم إلى `version.json`.
- اكتب `docs/releases/vX.Y.Z.md` وحدّث `ChangelogSheet` في `ui/about/Changelog.kt`.
- جرّب على جهاز: تسجيل الدخول، مهمة، رسالة صوتية، تحديث.
