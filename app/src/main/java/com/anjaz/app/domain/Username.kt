package com.anjaz.app.domain

/** اسم المستخدم: اسم بسيط يُلحق به نطاق النظام، أو بريد كامل بنطاقه (مثل sajed@anjaz.ps). */
object Username {
    private val simple = Regex("^[a-z0-9._-]{3,32}$")
    private val email = Regex("^[a-z0-9._+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    fun normalize(raw: String) = raw.trim().lowercase()

    fun isValid(u: String) = simple.matches(u) || (u.length <= 80 && email.matches(u))

    fun emailFor(u: String, domain: String) = if (u.contains('@')) u else "$u@$domain"
}
