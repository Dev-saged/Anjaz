# repo-map — Anjaz (إنجاز)

> ابدأ بهذا الملف ثم `.claude/session-handoff.md`؛ الرموز بـ `Grep` في `docs/code-index.md`؛ ≤5KB بلا كود.

## Quick index (ميزة → ملف → ابحث بـ)
- هوية → brand/ (`build_brand.py`، أيقونات في brand/android-icons)
- ثيم → ui/theme (`AnjazTheme`، `Anjaz.colors`)؛ مكوّنات → ui/components (`GlassNavBar`، `SchemaForm`)
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ (`TaskRepository`، `DashboardRepository`، `DemoSeeder`)؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)
- الدخول → cloud/AuthRepository، ui/login؛ المزامنة → sync/ (`SyncEngine`، `FirestoreRemoteStore`، `SyncWorker`، `ApiClient`)

## Features (v0.5.0)
- SOS: data/SosRepository → ui/sos؛ محادثة: data/ChatRepository → ui/chat (مسار room/{roomId}، SyncEngine.listenRoom).
- حسابات: data/AccountsRepository → ui/accounts؛ دليل: ui/directory؛ حول: ui/about/AboutCard (BrandConfig.Dev)؛ أصول README: brand/tools/make_readme_assets.py.
- Room v4: Migrations 1→4 (camps.regionId تفحصه القواعد؛ 3→4 حقول وسائط الرسائل)؛ PdfLayout منفصل لأن PdfDocument لا يعمل تحت Robolectric.
- وسائط: media/{MediaCompressor، MediaRepository (إرسال/تنزيل/تأكيد/تنظيف)، VoiceRecorder، MediaTransport}؛ الـ Worker جسر مؤقت backend/worker/src/media.js (يُحذف بعد ساعة من تأكيد الجميع أو 14 يوماً)؛ واجهة ui/chat/MediaUi.
- صوت→نص: stt/{WhisperModel (تنزيل 160MB مرة واحدة)، Transcriber (sherpa-onnx)، AudioPcm، SttViewModel}؛ الـ AAR يُنزَّل ببصمة في app/build.gradle.kts (fetchSherpa) وليس في Git. فيديو: media/VideoCompressor (Media3، تحت 9MB). تسجيل: RecorderState/HoldToRecord/RecordingBar في ui/chat/MediaUi.
- مجموعات: ChatListViewModel.createGroup → Worker /v1/rooms/group (للقادة)؛ لوحة أدوار: domain/DashboardBuilder → ui/dashboard (تذكير بالمتأخرة).
- تحديث ذاتي: update/ (UpdateChecker، UpdateManager، UpdateDownloadService، UpdateUi، AppFolder)؛ build.yml ينشر Release + version.json؛ versionCode=VERSION_CODE*10000+BUILD_NUMBER.

## Decisions
- كل شيء مجاني وبلا بطاقة: Firebase Spark (Auth + Firestore + FCM) + Cloudflare Workers/D1/Cron. السبب: قرار ساجد.
- هوية: حلقة+مربع أخضر، Alexandria/IBM Plex، #102E52/#24C27D (brand/، build_brand.py). Storage/Functions تحتاج Blaze فالبديل Worker+D1.
- العزل بين المناطق والمربعات في Firestore Security Rules + تحقق Worker بالتوكن، لا في الواجهة.
- الدخول باسم مستخدم + كلمة مرور (بريد وهمي داخلي)؛ المدير ينشئ ويعيد الضبط.
- minSdk 24 (بدل 26 في القالب) لدعم أجهزة قديمة؛ targetSdk 36. توزيع APK مباشر.
- اجتماعات WebRTC غير منفّذة.
- المستخدم: اسم بسيط أو بريد كامل (domain/Username + Worker policy.validUsername).

- الرئيسية معتمدة (brand/prototype/home-a.html): لا يُعاد بناء الهيكل.

## Solved bugs (never reintroduce)
- رسالة صوتية فاشلة (Worker غير منشور) كانت توقف الصندوق الصادر كله؛ الإصلاح: SyncEngine.pushPending يتجاوز الفاشل ويكمل (تتوقف الجولة فقط عند انقطاع الشبكة)، وFAILED + إعادة إرسال/حذف بالضغط المطول.
- قاعدة readBy في رسائل المحادثة كانت تسمح بإضافة معرّف مستخدم آخر؛ السبب أن الشرط يفحص الحجم فقط. الإصلاح: المقارنة بالاتحاد الدقيق `readBy ∪ {uid}` + اختبار.
- الإنشاء يدفع الحالة الأولية (NEW/OPEN) وعمليات الحالة تدفع الحالة المسجلة في الحمولة؛ دفع الحالة الحالية محلياً كان يخالف القواعد ويُرفض (SyncEngineTest.offlineChain).
- Worker: تمرير `fetch` العام كخاصية (`this.fetch(...)`) يرمي Illegal invocation على Workers فقط؛ الحل `(...a) => fetch(...a)` (لم تلتقطه الاختبارات).

## Open issues
- Firebase والـ Worker منشوران ومجرّبان؛ التطبيق وتدفق التحديث لم يُجرَّبا على جهاز؛ لا Release قبل إضافة أسرار التوقيع (4) في GitHub.
- الإملاء/الفيديو/التسجيل الجديد لم تُجرَّب على جهاز؛ تأكد من أسرار CLOUDFLARE_API_TOKEN/ACCOUNT_ID في GitHub؛ PDF لم يُرسم.
