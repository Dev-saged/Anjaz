# repo-map — Anjaz (إنجاز)

> ابدأ بهذا الملف ثم `.claude/session-handoff.md`؛ الرموز بـ `Grep` في `docs/code-index.md`؛ ≤5KB بلا كود.

## Quick index (ميزة → ملف → ابحث بـ)
- هوية → brand/ (`build_brand.py`، أيقونات في brand/android-icons)
- ثيم → ui/theme (`AnjazTheme`، `Anjaz.colors`)؛ مكوّنات → ui/components (`GlassNavBar`، `SchemaForm`)
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ → `TaskRepository` (create/transition + outbox)، `DashboardRepository`، `DemoSeeder`؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)
- الدخول والجلسة → cloud/AuthRepository، ui/login؛ المزامنة → sync/ (`SyncEngine` دفع الصندوق الصادر + سحب، `FirestoreRemoteStore`، `SyncWorker`، `ApiClient` للـ Worker)

## Features (v0.3.0)
- SOS: data/SosRepository → ui/sos؛ محادثة: data/ChatRepository → ui/chat (مسار room/{roomId}، SyncEngine.listenRoom).
- حسابات: data/AccountsRepository → ui/accounts (Worker /v1/users)؛ دليل: data/DirectoryRepository → ui/directory.
- إشعارات: push/{Notifications,DeviceTokens,AnjazMessagingService,DeepLink}؛ قناتا default وsos.
- نسخ/تقارير: backup/BackupManager، report/{ReportData,PdfLayout,PdfExporter,XlsxExporter} → ui/reports؛ سجل: ui/audit.
- Room v3: Migrations 1→2→3 (camps.regionId تفحصه القواعد)؛ PdfLayout منفصل لأن PdfDocument لا يعمل تحت Robolectric.
- تحديث ذاتي: update/{UpdateChecker (3 طبقات من GitHub Releases)، UpdateManager (تنزيل Range + تثبيت PackageInstaller)، UpdateDownloadService، UpdateUi (بانر علوي + ورقة)، AppFolder (updates/backups/exports)}؛ build.yml ينشر Release + version.json؛ versionCode=VERSION_CODE*10000+BUILD_NUMBER.

## Decisions
- كل شيء مجاني وبلا بطاقة: Firebase Spark (Auth + Firestore + FCM) + Cloudflare Workers/D1/Cron. السبب: قرار ساجد.
- Storage/Functions تحتاج Blaze، فالبديل Worker (FCM، التصعيد، النسخ) و D1.
- العزل بين المناطق والمربعات في Firestore Security Rules + تحقق Worker بالتوكن، لا في الواجهة.
- الرسائل بلا E2EE؛ القواعد تمنع المديرين من قراءتها.
- الدخول باسم مستخدم + كلمة مرور (بريد وهمي داخلي)؛ المدير ينشئ ويعيد الضبط.
- minSdk 24 (بدل 26 في القالب) لدعم أجهزة قديمة؛ targetSdk 36. توزيع APK مباشر.
- اجتماعات WebRTC وصوت→نص محلي: غير منفّذين.
- مستند المتطلبات الكامل: رسالة ساجد الأصلية (22 قسماً)؛ لا يُحذف منها بند دون موافقته.

- الهوية: حلقة + مربع أخضر، خط Alexandria للعناوين وIBM Plex للنص، أساسي #102E52 وإنجاز #24C27D؛ المصدر `brand/` ويُعاد توليده بـ `python3 brand/tools/build_brand.py`.
- الرئيسية معتمدة: A + شريط أيام B + تنقل زجاجي C (الرئيسية، المهام، الرسائل، المزيد). المرجع brand/prototype/home-a.html. لا يُعاد بناء الهيكل.

## Solved bugs (never reintroduce)
- قاعدة readBy في رسائل المحادثة كانت تسمح بإضافة معرّف مستخدم آخر؛ السبب أن الشرط يفحص الحجم فقط. الإصلاح: المقارنة بالاتحاد الدقيق `readBy ∪ {uid}` + اختبار.
- الإنشاء يدفع الحالة الأولية (NEW/OPEN) وعمليات الحالة تدفع الحالة المسجلة في الحمولة؛ دفع الحالة الحالية محلياً كان يخالف القواعد ويُرفض (SyncEngineTest.offlineChain).
- Worker: تمرير `fetch` العام كخاصية (`this.fetch(...)`) يرمي Illegal invocation على Workers فقط؛ الحل `(...a) => fetch(...a)` (لم تلتقطه الاختبارات).

## Open issues
- Firebase والـ Worker منشوران ومجرّبان؛ التطبيق وتدفق التحديث لم يُجرَّبا على جهاز؛ لا Release قبل إضافة أسرار التوقيع (4) في GitHub.
- سجل أحداث المهمة يُدفع ولا يُسحب؛ الرسائل خارج نسخ الـ Worker؛ PDF لم يُرسم على جهاز.
- غير منفّذ: وسائط المحادثة والمجموعات، لوحات حسب الدور، واجهة التصعيد.
