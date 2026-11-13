# repo-map — Anjaz (إنجاز)

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.
> حافظ على هذا الملف تحت 5KB: بلا كود، وحدّثه بسطرين بعد كل تغيير.

## Quick index (ميزة → ملف → ابحث بـ)
- هوية الشعار → brand/ → `build_brand.py`، أيقونة أندرويد → brand/android-icons (تُنسخ إلى app/src/main/res)

- ثيم/ألوان/خطوط → app/.../ui/theme → `AnjazTheme`، `Anjaz.colors`؛ المكوّنات → ui/components → `GlassNavBar`، `ProgressRing`، `SchemaForm`
- الرئيسية → ui/home → `HomeViewModel`؛ المهام → ui/tasks → `TasksViewModel`، `NewTaskSheet`؛ التنقل → ui/nav → `AnjazRoot`
- المنطق المحلي → data/ → `TaskRepository` (create/transition + outbox)، `DashboardRepository`، `DemoSeeder`؛ اختبار ذاتي → core/SelfTest
- الخلفية → backend/firebase (القواعد + اختباراتها) و backend/worker (المسارات في `route` داخل src/index.js، الصلاحيات في policy.js، التصعيد cron.js، النسخ backup.js)

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

## Open issues
- الإجراءات تُسجَّل بمعرّف محلي `Session.LOCAL_ACTOR` حتى يُربط تسجيل الدخول؛ الأدوار معرّفة في domain/Role لكن غير مفروضة على الواجهة بعد.
- الخلفية مختبَرة بمحاكيات (Firestore emulator للقواعد، ومحاكاة REST للـ Worker) ولم تُنشر على مشروع حقيقي؛ الإعداد في backend/README.md.
- الصندوق الصادر يُكتب ولا يُرسَل بعد (لا Worker متزامن حتى الآن).
- التحقق من حدود Cloudflare المجانية الحالية (Workers/D1/Cron) قبل التنفيذ.
- اختبار الصوت والاجتماعات والإنذار يحتاج هواتف حقيقية من ساجد.
