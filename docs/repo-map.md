# repo-map — Anjaz (إنجاز)

## Quick index (ميزة → ملف → ابحث بـ)
- ثيم → ui/theme؛ مكوّنات → ui/components (`GlassNavBar`، `SchemaForm`)
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ (`TaskRepository`، `DashboardRepository`، `DemoSeeder`)؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)
- الدخول → cloud/AuthRepository؛ المزامنة → sync/ (`SyncEngine`، `FirestoreRemoteStore`، `SyncWorker`، `ApiClient`)

## Features (v0.8.0)
- SOS: ui/sos؛ محادثة: ui/chat (SyncEngine.listenRoom).
- حسابات: data/AccountsRepository → ui/accounts؛ دليل: ui/directory؛ حول: ui/about/AboutCard؛ README: brand/tools/make_readme_assets.py.
- Room v9: Migrations 1→9 (8→9 tasks.createdByName، users.campId، messages.senderName، rooms.memberNames)؛ PdfLayout منفصل لأن PdfDocument لا يعمل تحت Robolectric.
- وسائط: media/{MediaRepository، VoiceRecorder، MediaTransport}؛ Worker جسر مؤقت media.js؛ واجهة ui/chat/MediaUi.
- صوت→نص: stt/{WhisperModel، Transcriber، AudioPcm، SttViewModel}؛ AAR يُنزَّل ببصمة (fetchSherpa). فيديو: media/VideoCompressor.
- تنظيف: sync/CleanupWorker (يومي، MediaRepository.cleanOrphans)؛ ما الجديد: ui/about/Changelog؛ حالة فارغة: components/EmptyState؛ النشر: docs/DEPLOY.md.
- v0.8.0: خريطة ui/map؛ متكررة ui/templates؛ مكالمات `CallManager`؛ قفل `AppLock`؛ بحث ui/search؛ صور TaskPhotos.
- حجم: splits.abi؛ version.json فيه apks/sizes.
- v0.8.3: مكتبات onnx/sherpa/webrtc خارج الـ APK (excludes في gradle) → pack/`NativePacks` (يُنزَّل من release packs-v1، SHA-256، `PackPrefetchWorker` على Wi-Fi)؛ إن غيّرت هذه المكتبات ارفع PACK_ID/PACK_TAG وbuild.yml؛ الرئيسية: ui/home/HomeShortcuts (`MapCard`،`PlacesRow`).
- v0.13.0: رؤية المهام `FirestoreRemoteStore.tasks` (union) + `TaskRepository.canAssignTo`؛ قارئ/منفّذ في القواعد `taskReader`/`executor`؛ جهات الاتصال `users` (union) + `ChatListViewModel.directoryGroups`؛ أسماء الغرف `encodeNames`؛ التوزيع ui/distribute + `Weights.allocate` (أكبر الباقي، WeightsTest)؛ إشعارات notify.js تذكر المرسِل.
- v0.12.0: الدليل `RegionSection`؛ نقل/تحويل `convertRegionToSquare`؛ `FormAlert`؛ كلمة مرور حرة `Username.authPassword` (لاحقة #anjaz للأقصر من 6).
- v0.11.0: إحصائيات ui/stats؛ أقسام المهام `TaskCategory`؛ `FitText`+حد الخط 1.3 في AnjazTheme؛ `Plural`؛ حذف سلة فعلي Worker trash.js؛ حذف الرسالة `unsend`.
- v0.10.0: تكليف `TaskRepository.createBatch`+`TaskTarget`؛ ui/tasks/`TaskTargetPicker`؛ سلة ui/trash+`Trash`؛ حذف ناعم deletedAt (حذف نهائي=EXPIRED=1)؛ تنظيف CleanupWorker.
- v0.9.0: صوت `VoiceBubble`/`HoldToRecord`؛ تنزيلات `DownloadCenter`؛ مزامنة `SyncSheet`.
- تحديث ذاتي: update/؛ build.yml ينشر Release+version.json؛ versionCode=VERSION_CODE*10000+BUILD_NUMBER.

## Decisions
- كل شيء مجاني وبلا بطاقة: Firebase Spark (Auth + Firestore + FCM) + Cloudflare Workers/D1/Cron. السبب: قرار ساجد.
- هوية: حلقة+مربع أخضر، #102E52/#24C27D (brand/، build_brand.py). Storage/Functions تحتاج Blaze فالبديل Worker+D1.
- العزل بين المناطق في Firestore Rules + تحقق Worker بالتوكن، لا في الواجهة.
- minSdk 24؛ targetSdk 36.

## Solved bugs (never reintroduce)
- رسالة صوتية فاشلة (Worker غير منشور) كانت توقف الصندوق الصادر كله؛ الإصلاح: SyncEngine.pushPending يتجاوز الفاشل ويكمل (تتوقف الجولة فقط عند انقطاع الشبكة)، وFAILED + إعادة إرسال/حذف بالضغط المطول.
- قاعدة readBy في رسائل المحادثة كانت تسمح بإضافة معرّف مستخدم آخر؛ السبب أن الشرط يفحص الحجم فقط. الإصلاح: المقارنة بالاتحاد الدقيق `readBy ∪ {uid}` + اختبار.
- الإنشاء يدفع الحالة الأولية (NEW/OPEN) وعمليات الحالة تدفع الحالة المسجلة في الحمولة؛ دفع الحالة الحالية محلياً كان يخالف القواعد ويُرفض (SyncEngineTest.offlineChain).
- Worker: تمرير `fetch` العام كخاصية (`this.fetch(...)`) يرمي Illegal invocation على Workers فقط؛ الحل `(...a) => fetch(...a)` (لم تلتقطه الاختبارات).

## Open issues
- Firebase والـ Worker منشوران ومجرّبان؛ التطبيق وتدفق التحديث لم يُجرَّبا على جهاز؛ لا Release قبل إضافة أسرار التوقيع (4) في GitHub.
- v0.8–0.10 لم تُجرَّب على جهاز؛ قواعد Firestore تُنشر يدوياً (v0.10.0 تغيّرت).

## Solved bugs (v0.13.0)
- المهمة لشخص لا تظهر لديه: كانت regionId/squareId للمرسِل → الآن نطاق المكلَّف.
- الميداني لا يرى مهام مربعه: الاستعلام كان assigneeId فقط → union مع مربع/مخيم غير مسندة.
- عنوان المحادثة باسم المستلم: room.name للطرف الآخر من جهة المنشئ → memberNames.
