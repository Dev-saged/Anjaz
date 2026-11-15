# Session handoff — Anjaz

## الحالة
- الإصدار 0.10.0 (VERSION_CODE 13) على الفرع feature/v0100-tasks-trash ثم main؛ البناء أخضر في CI (الاختبارات لا تعمل في CI ولا محلياً).
- الجديد: تكليف (كل المربعات/المخيمات/مربع/مخيم/أشخاص) عبر `TaskRepository.createBatch` (مهمة لكل جهة)، منتقي تاريخ، حذف ناعم للمهام مع تراجع، سلة مهملات ui/trash، مسؤول المربع، تعديل/حذف المربعات والمخيمات، تنظيف شهري في CleanupWorker.
- Room v6 (MIGRATION_5_6)؛ حقول: tasks.campId/deletedAt، squares.managerName/deletedAt، camps.deletedAt.

## قرارات
- حذف نهائي = deletedAt=1 (`Trash.EXPIRED`) فيزيله CleanupWorker ولا تعيده المزامنة (Firestore يمنع delete).
- حذف المربع ينقل مخيماته بالطابع نفسه ويعيدها معه.
- قواعد Firestore تغيّرت (مهام: حذف ناعم للإداري؛ managerName) وتُنشر يدوياً.

## مفتوح
- غير مجرَّب على جهاز: التكليف الجماعي، التقويم، السلة، مزامنة الحذف/الاسترجاع.
- رسائل المحادثة غير مشمولة بالسلة.
- ملف schemas/6.json يولّده Room عند البناء؛ يلزم commit له.

## الخطوة التالية
- نشر firestore.rules من backend/firebase ثم تجربة السيناريوهات على جهاز.
