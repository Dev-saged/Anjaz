# Session handoff — Anjaz

## الحالة
- الإصدار 0.11.0 (VERSION_CODE 14)، الفرع feature/v0110-stats-layout يُدمج في main؛ البناء في CI، اختبارات الـ Worker (12) خضراء محلياً.
- الجديد: شاشة الإحصائيات ui/stats (مربعات/مخيمات/أسر)، camps.families (Room v7)، أقسام المهام الأربعة `TaskCategory`، أولوية مستويان (HIGH القديم = عادية)، مربعات منسدلة، حذف صور المهام، حذف الرسالة لدى الجميع، الحسابات على @anjaz.ps فقط (BrandConfig.ACCOUNT_DOMAIN + Worker ACCOUNT_DOMAIN).
- إصلاح الخطوط: Type.kt (lineHeight واسع + Trim.None + includeFontPadding)، حد fontScale 0.85–1.3 في AnjazTheme، `FitText` للنصوص ذات السطر الواحد.

## قرارات
- السلة: الحذف النهائي يضع deletedAt=1؛ الـ Worker (trash.js، كرون 21:00) يحذف فعلياً ما deletedAt < الآن−30 يوماً مع events/photos. القواعد تسمح بالحذف الفعلي فقط لما deletedAt رقم.
- الدخول باسم بلا نطاق يجرّب @anjaz.ps ثم النطاق القديم users.anjaz.local.

## مفتوح
- غير مجرَّب على جهاز. قواعد Firestore تغيّرت وتُنشر يدوياً. الـ Worker يُنشر عبر deploy-worker.yml.

## الخطوة التالية
- نشر firestore.rules، والتأكد من نجاح deploy-worker، ثم تجربة الإحصائيات وإنشاء حساب مدير مربع على جهاز.
