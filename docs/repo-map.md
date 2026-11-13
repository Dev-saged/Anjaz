# repo-map — Anjaz (إنجاز)

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.
> حافظ على هذا الملف تحت 5KB: بلا كود، وحدّثه بسطرين بعد كل تغيير.

## Quick index (ميزة → ملف → ابحث بـ)
- هوية الشعار → brand/ → `build_brand.py`، أيقونة أندرويد → brand/android-icons (تُنسخ إلى app/src/main/res)

- ثيم/ألوان/خطوط → app/.../ui/theme → `AnjazTheme`، `Anjaz.colors`؛ المكوّنات → ui/components → `GlassNavBar`، `ProgressRing`، `SchemaForm`
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ → `TaskRepository` (create/transition + outbox)، `DashboardRepository`، `DemoSeeder`؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)
- الدخول والجلسة → cloud/AuthRepository، ui/login؛ المزامنة → sync/ (`SyncEngine` دفع الصندوق الصادر + سحب، `FirestoreRemoteStore`، `SyncWorker`، `ApiClient` للـ Worker)

## Chat / SOS / Room v2
- استغاثة: data/SosRepository (send/advance) → ui/sos/SosScreen (HoldButton) ؛ مسار `sos` وزر FAB أحمر لغير القادة.
- محادثة: data/ChatRepository (directRoomId, send, markRead) → ui/chat/{ChatViewModels,ChatScreens}؛ مسار `room/{roomId}`؛ SyncEngine.listenRoom.
- Room v2: Migrations.MIGRATION_1_2 + MigrationTest؛ المخططات في app/schemas.
- حسابات: data/AccountsRepository + ui/accounts (إنشاء/إعادة ضبط/تعطيل عبر Worker `/v1/users`)؛ الاختيار من الدليل لا معرّفات يدوية.
- إشعارات: push/{Notifications, DeviceTokens, AnjazMessagingService, DeepLink}؛ قناتا default وsos؛ الرمز يُكتب في devices/{uid}.
- دليل: data/DirectoryRepository + ui/directory (منطقة/مربع/مخيم، دفع بالترتيب)؛ Room v3 أضاف camps.regionId لأن القواعد تفحصه.

## Decisions
- جمعية واحدة تدير مناطق ← مربعات ← مخيمات؛ بلا SaaS الآن، والتصميم يسمح بالتوسع لاحقاً. السبب: طلب ساجد.
- الحجم الحالي: ~4 مربعات × ~10 مستخدمين، أغلبهم غير متصلين. السبب: يبقى كل شيء ضمن الحصص المجانية.
- كل شيء مجاني وبلا بطاقة: Firebase Spark (Auth + Firestore + FCM) + Cloudflare Workers/D1/Cron. السبب: قرار ساجد.
- Cloud Storage و Cloud Functions يحتاجان Blaze، فنستبدلهما بـ Worker (إشعارات FCM، إعادة ضبط كلمة المرور بصلاحية المدير، التصعيد، النسخ اليومي) و D1 (الصور والصوت المضغوطة).
- العزل بين المناطق والمربعات في Firestore Security Rules + تحقق Worker بالتوكن، لا في الواجهة.
- الرسائل الخاصة بلا E2EE الآن؛ القواعد تمنع أي مدير من قراءتها. E2EE مؤجل.
- الدخول باسم مستخدم + كلمة مرور (بريد وهمي داخلي)، والمدير هو من ينشئ ويعيد الضبط. ربط بريد Google لاحقاً.
- minSdk 24 (بدل 26 في القالب) لدعم أجهزة قديمة؛ targetSdk 36. توزيع APK مباشر.
- الاجتماعات: WebRTC مباشر + إشارة عبر Firestore + STUN مجاني، لمجموعات صغيرة؛ قد يلزم TURN على بعض الشبكات.
- الصوت إلى نص محلي: يُقاس Whisper (Tiny/Base/Small) عبر whisper.cpp قبل الاختيار.
- مستند المتطلبات الكامل: رسالة ساجد الأصلية (22 قسماً)؛ لا يُحذف منها بند دون موافقته.

- الهوية: حلقة + مربع أخضر، خط Reem Kufi Bold، أساسي #102E52 وإنجاز #24C27D؛ المصدر `brand/` ويُعاد توليده بـ `python3 brand/tools/build_brand.py`.
- الشاشة الرئيسية معتمدة: اتجاه A (رأس كحلي بحلقة الإنجاز) + شريط أيام الأسبوع من B + تنقل زجاجي عائم بزر + مركزي من C (4 عناصر: الرئيسية، المهام، الرسائل، المزيد). الاجتماعات تحت المزيد. المرجع: brand/prototype/home-a.html. لا يُعاد بناء الهيكل بعد الاعتماد.

## Solved bugs (never reintroduce)
- قاعدة readBy في رسائل المحادثة كانت تسمح بإضافة معرّف مستخدم آخر؛ السبب أن الشرط يفحص الحجم فقط. الإصلاح: المقارنة بالاتحاد الدقيق `readBy ∪ {uid}` + اختبار.
- الإنشاء يدفع الحالة الأولية (NEW/OPEN) وعمليات الحالة تدفع الحالة المسجلة في الحمولة؛ دفع الحالة الحالية محلياً كان يخالف القواعد ويُرفض (SyncEngineTest.offlineChain).

## Open issues
- الإجراءات تُسجَّل بمعرّف محلي `Session.LOCAL_ACTOR` حتى يُربط تسجيل الدخول؛ الأدوار معرّفة في domain/Role لكن غير مفروضة على الواجهة بعد.
- الخلفية مختبَرة بمحاكيات (Firestore emulator للقواعد، ومحاكاة REST للـ Worker) ولم تُنشر على مشروع حقيقي؛ الإعداد في backend/README.md.
- المزامنة مكتوبة ومختبرة بخادم وهمي فقط؛ تحتاج google-services.json وWORKER_URL في BrandConfig لتعمل فعلياً. سجل أحداث المهمة يُدفع ولا يُسحب بعد.
- التحقق من حدود Cloudflare المجانية الحالية (Workers/D1/Cron) قبل التنفيذ.
- اختبار الصوت والاجتماعات والإنذار يحتاج هواتف حقيقية من ساجد.
