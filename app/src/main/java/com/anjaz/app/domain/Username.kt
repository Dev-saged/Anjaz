package com.anjaz.app.domain

/** اسم المستخدم: اسم بسيط يُلحق به نطاق النظام، أو بريد كامل بنطاقه (مثل sajed@anjaz.ps). */
object Username {
    private val simple = Regex("^[a-z0-9._-]{3,32}$")
    private val email = Regex("^[a-z0-9._+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    fun normalize(raw: String) = raw.trim().lowercase()

    fun isValid(u: String) = simple.matches(u) || (u.length <= 80 && email.matches(u))

    fun emailFor(u: String, domain: String) = if (u.contains('@')) u else "$u@$domain"

    /** اسم الحساب الجديد كاملاً على نطاق الجهة، أو null إن كان بنطاق آخر. */
    fun forAccount(raw: String, domain: String): String? {
        val u = normalize(raw)
        val full = if (u.contains('@')) u else "$u@$domain"
        return full.takeIf { it.endsWith("@$domain") && isValid(it) }
    }

    /** الأرقام العربية والفارسية إلى لاتينية، وإزالة الفراغات الطرفية. */
    fun cleanSecret(raw: String): String = raw.trim().map { ch ->
        when (ch) {
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
            else -> ch
        }
    }.joinToString("")
}
