# repo-map — Anjaz (إنجاز)

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.
> حافظ على هذا الملف تحت 5KB: بلا كود، وحدّثه بسطرين بعد كل تغيير.

## Quick index (ميزة → ملف → ابحث بـ)
- هوية → brand/ (`build_brand.py`، أيقونات في brand/android-icons)
- ثيم → ui/theme (`AnjazTheme`، `Anjaz.colors`)؛ مكوّنات → ui/components (`GlassNavBar`، `SchemaForm`)
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ → `TaskRepository` (create/transition + outbox)، `DashboardRepository`، `DemoSeeder`؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)
- الدخول والجلسة → cloud/AuthRepository، ui/login؛ المزامنة → sync/ (`SyncEngine` دفع الصندوق الصادر + سحب، `FirestoreRemoteStore`، `SyncWorker`، `ApiClient` للـ Worker)

## Features (v0.2.0)
- SOS: data/SosRepository → ui/sos؛ محادثة: data/ChatRepository → ui/chat (مسار room/{roomId}، SyncEngine.listenRoom).
- حسابات: data/AccountsRepository → ui/accounts (Worker /v1/users)؛ دليل: data/DirectoryRepository → ui/directory.
- إشعارات: push/{Notifications,DeviceTokens,AnjazMessagingService,DeepLink}؛ قناتا default وsos.
- نسخ/تقارير: backup/BackupManager، report/{ReportData,PdfLayout,PdfExporter,XlsxExporter} → ui/reports؛ سجل: ui/audit.
- Room v3: Migrations 1→2→3 (camps.regionId لأن القواعد تفحصه)؛ المخططات app/schemas؛ MigrationTest.
- PdfDocument لا يعمل تحت Robolectric؛ لذلك PdfLayout منفصل ومختبر على JVM.

## Decisions
- جمعية واحدة: مناطق ← مربعات ← مخيمات (~4×10 مستخدمين)؛ بلا SaaS الآن.
- كل شيء مجاني وبلا بطاقة: Firebase Spark (Auth + Firestore + FCM) + Cloudflare Workers/D1/Cron. السبب: قرار ساجد.
- Storage/Functions تحتاج Blaze، فالبديل Worker (FCM، إعادة الضبط، التصعيد، النسخ) و D1 (الوسائط).
- العزل بين المناطق والمربعات في Firestore Security Rules + تحقق Worker بالتوكن، لا في الواجهة.
- الرسائل الخاصة بلا E2EE الآن؛ القواعد تمنع أي مدير من قراءتها. E2EE مؤجل.
- الدخول باسم مستخدم + كلمة مرور (بريد وهمي داخلي)، والمدير هو من ينشئ ويعيد الضبط. ربط بريد Google لاحقاً.
- minSdk 24 (بدل 26 في القالب) لدعم أجهزة قديمة؛ targetSdk 36. توزيع APK مباشر.
- اجتماعات: WebRTC + إشارة Firestore + STUN؛ صوت→نص محلي: قياس Whisper قبل الاختيار (غير منفّذين).
- مستند المتطلبات الكامل: رسالة ساجد الأصلية (22 قسماً)؛ لا يُحذف منها بند دون موافقته.

- الهوية: حلقة + مربع أخضر، خط Alexandria للعناوين وIBM Plex للنص، أساسي #102E52 وإنجاز #24C27D؛ المصدر `brand/` ويُعاد توليده بـ `python3 brand/tools/build_brand.py`.
- الرئيسية معتمدة: A + شريط أيام B + تنقل زجاجي C (الرئيسية، المهام، الرسائل، المزيد). المرجع brand/prototype/home-a.html. لا يُعاد بناء الهيكل.

## Solved bugs (never reintroduce)
- قاعدة readBy في رسائل المحادثة كانت تسمح بإضافة معرّف مستخدم آخر؛ السبب أن الشرط يفحص الحجم فقط. الإصلاح: المقارنة بالاتحاد الدقيق `readBy ∪ {uid}` + اختبار.
- الإنشاء يدفع الحالة الأولية (NEW/OPEN) وعمليات الحالة تدفع الحالة المسجلة في الحمولة؛ دفع الحالة الحالية محلياً كان يخالف القواعد ويُرفض (SyncEngineTest.offlineChain).

## Open issues
- لم يُختبر على Firebase/Worker حقيقي ولا على جهاز؛ الإعداد في backend/README.md (google-services.json، WORKER_URL في BrandConfig).
- سجل أحداث المهمة يُدفع ولا يُسحب؛ الرسائل خارج نسخ الـ Worker؛ PDF لم يُرسم على جهاز.
- غير منفّذ: وسائط المحادثة والمجموعات، صوت→نص، اجتماعات، لوحات حسب الدور، واجهة التصعيد.
