package com.anjaz.app.domain

/** اسم المستخدم: اسم بسيط يُلحق به نطاق النظام، أو بريد كامل بنطاقه (مثل sajed@anjaz.ps). */
object Username {
    private val simple = Regex("^[a-z0-9._-]{3,32}$")
    private val email = Regex("^[a-z0-9._+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    fun normalize(raw: String) = raw.trim().lowercase()

    fun isValid(u: String) = simple.matches(u) || (u.length <= 80 && email.matches(u))

    fun emailFor(u: String, domain: String) = if (u.contains('@')) u else "$u@$domain"

    private val local = Regex("^[a-z0-9][a-z0-9._-]{0,31}$")

    /** الجزء قبل @ فقط؛ النطاق دائماً نطاق الجهة مهما كُتب. */
    fun localPart(raw: String): String = normalize(raw).substringBefore('@').replace(" ", "")

    /** اسم الحساب الجديد كاملاً على نطاق الجهة، أو null إن لم يكن صالحاً. */
    fun forAccount(raw: String, domain: String): String? {
        val u = localPart(raw)
        return "$u@$domain".takeIf { local.matches(u) }
    }

    /** سبب رفض اسم الدخول بلغة واضحة، أو null إن كان صالحاً. */
    fun problem(raw: String): String? {
        val u = localPart(raw)
        return when {
            u.isEmpty() -> "اكتب اسم الدخول"
            u.any { it in '\u0600'..'\u06FF' } -> "اسم الدخول بحروف إنجليزية فقط، مثل ahmad أو ahmad.ali"
            !local.matches(u) -> "استخدم حروفاً إنجليزية وأرقاماً فقط، ويمكن النقطة أو الشرطة (حتى 32 حرفاً)"
            else -> null
        }
    }

    /** Firebase يشترط 6 أحرف على الأقل؛ كلمة المرور الأقصر تُكمَّل بلاحقة ثابتة عند الإنشاء والدخول معاً. */
    const val PAD = "#anjaz"
    fun authPassword(p: String): String = if (p.length >= 6) p else p + PAD

    /** الأرقام العربية والفارسية إلى لاتينية، وإزالة الفراغات الطرفية. */
    fun cleanSecret(raw: String): String = raw.trim().map { ch ->
        when (ch) {
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
            else -> ch
        }
    }.joinToString("")
}
