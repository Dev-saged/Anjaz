package com.anjaz.app.schema

enum class FieldType { TEXT, MULTILINE, PHONE, NUMBER, CHOICE }

data class Choice(val key: String, val label: String)

data class FieldSchema(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val maxLength: Int = 200,
    val choices: List<Choice> = emptyList(),
)

data class ModuleSchema(
    val id: String,
    val title: String,
    val fields: List<FieldSchema>,
    val listColumns: List<String>,
)

object SchemaValidator {
    private val phone = Regex("^\\+?[0-9 ]{7,16}$")
    private val digits = Regex("^[0-9]+$")

    fun randomPassword(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return (1..10).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    fun sanitize(raw: String): String =
        raw.filter { !it.isISOControl() || it == '\n' }.replace(Regex("[ \\t]{2,}"), " ").trim()

    fun clean(schema: ModuleSchema, values: Map<String, String>): Map<String, String> =
        schema.fields.associate { it.key to sanitize(values[it.key].orEmpty()) }

    fun validate(schema: ModuleSchema, values: Map<String, String>): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        schema.fields.forEach { f ->
            val v = sanitize(values[f.key].orEmpty())
            when {
                v.isEmpty() -> if (f.required) errors[f.key] = "هذا الحقل مطلوب"
                v.length > f.maxLength -> errors[f.key] = "الحد الأقصى ${f.maxLength} حرفاً"
                f.type == FieldType.PHONE && !phone.matches(v) -> errors[f.key] = "رقم غير صالح"
                f.type == FieldType.NUMBER && !digits.matches(v) -> errors[f.key] = "أرقام فقط"
                f.type == FieldType.CHOICE && f.choices.none { it.key == v } -> errors[f.key] = "اختيار غير صالح"
            }
        }
        return errors
    }
}
