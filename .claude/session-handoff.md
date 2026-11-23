# Session handoff — Anjaz

## الحالة
- الإصدار 0.13.0 (VERSION_CODE 16) مدموج في main؛ قبله 0.12.0؛ قبله 0.11.0؛ البناء في CI، اختبارات الـ Worker (12) خضراء محلياً.
- الجديد: شاشة الإحصائيات ui/stats (مربعات/مخيمات/أسر)، camps.families (Room v7)، أقسام المهام الأربعة `TaskCategory`، أولوية مستويان (HIGH القديم = عادية)، مربعات منسدلة، حذف صور المهام، حذف الرسالة لدى الجميع، الحسابات على @anjaz.ps فقط (BrandConfig.ACCOUNT_DOMAIN + Worker ACCOUNT_DOMAIN).
- إصلاح الخطوط: Type.kt (lineHeight واسع + Trim.None + includeFontPadding)، حد fontScale 0.85–1.3 في AnjazTheme، `FitText` للنصوص ذات السطر الواحد.

- 0.12.0: الدليل حسب المناطق، نقل المربعات/المخيمات، تحويل منطقة إلى مربع، حذف منطقة فارغة (Room v8)، اسم دخول بلاحقة @anjaz.ps ظاهرة، كلمة مرور بأي طول.

- 0.13.0: إصلاح رؤية المهام (نطاق المكلَّف + union استعلامات)، «من:» على المهام، أسماء المرسلين، دليل محادثة بالمخيمات، users.campId، التوزيع بالأوزان ui/distribute، إشعارات تذكر المرسِل (Room v9).

## قرارات
- الاستماع يُعاد عند تغيّر دور/مربع/مخيم المستخدم (SyncEngine.refreshMe + LaunchedEffect في AnjazNav).
- كلمة المرور الأقصر من 6 تُكمَّل بـ `#anjaz` في التطبيق والـ Worker معاً (شرط Firebase)؛ الدخول يجرّب المكمَّلة ثم الخام.
- السلة: الحذف النهائي يضع deletedAt=1؛ الـ Worker (trash.js، كرون 21:00) يحذف فعلياً ما deletedAt < الآن−30 يوماً مع events/photos. القواعد تسمح بالحذف الفعلي فقط لما deletedAt رقم.
- الدخول باسم بلا نطاق يجرّب @anjaz.ps ثم النطاق القديم users.anjaz.local.

## مفتوح
- غير مجرَّب على جهاز. قواعد Firestore تغيّرت وتُنشر يدوياً. الـ Worker يُنشر عبر deploy-worker.yml.

## الخطوة التالية
- نشر firestore.rules، والتأكد من نجاح deploy-worker، ثم تجربة الإحصائيات وإنشاء حساب مدير مربع على جهاز.
