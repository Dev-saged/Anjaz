# session-handoff — Anjaz
(يُعاد كتابته في نهاية كل جلسة، 25 سطراً كحد أقصى)

- الحالة (v0.3.0): كل الميزات السابقة + نظام التحديث الذاتي (update/) + README جديد بوسائط docs/media. يجتاز `./gradlew testDebugUnitTest assembleDebug` (53 اختباراً). الفرع feature/updates.
- Firebase وWorker منشوران ومجرّبان (إنشاء حساب). لم يُجرَّب التطبيق ولا تدفق التحديث على جهاز حقيقي.
- التحديث: UpdateChecker يقرأ GitHub Releases (version.json ← redirect ← API)؛ UpdateManager ينزّل بـ Range داخل AppFolder.updates ثم يثبّت بـ PackageInstaller؛ الحذف عبر PackageReplacedReceiver وrestore().
- CI: build.yml يمرّر BUILD_NUMBER وينشر Release + version.json على main فقط عند وجود KEYSTORE_BASE64. الأسرار الأربعة غير مضافة بعد، فلا Release ولا تحديث ظاهر حتى يضيفها ساجد (Settings ← Secrets).
- التوقيع: أي APK بمفتاح مختلف عن المثبّت لا يُرقّى؛ أول تثبيت بالمفتاح الثابت يحتاج حذف النسخة القديمة.
- قرارات: versionCode=VERSION_CODE*10000+BUILD_NUMBER؛ مجلد التطبيق الخاص (Android/data) لا التخزين العام؛ ملاحظات الإصدار من docs/releases.
- غير منفّذ: وسائط المحادثة والمجموعات، صوت→نص، اجتماعات WebRTC، لوحات حسب الدور، واجهة التصعيد، نسخ Worker للرسائل.
- أمان: ساجد يدوّر رمز Cloudflare ومفتاح حساب الخدمة (نُشرا في المحادثة) ويغيّر كلمة مرور المدير.
- الخطوة التالية: إضافة أسرار التوقيع ← دمج feature/updates إلى main ← تجربة APK على جهاز ← الوسائط في المحادثة.
