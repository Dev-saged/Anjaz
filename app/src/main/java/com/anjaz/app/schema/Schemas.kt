package com.anjaz.app.schema

object Schemas {
    val priorities = listOf(Choice("NORMAL", "عادية"), Choice("HIGH", "مهمة"), Choice("URGENT", "عاجلة"))
    val deadlines = listOf(
        Choice("none", "بلا موعد"), Choice("today", "اليوم"),
        Choice("tomorrow", "غداً"), Choice("3d", "بعد 3 أيام"),
    )

    val task = ModuleSchema(
        id = "tasks", title = "مهمة جديدة",
        fields = listOf(
            FieldSchema("title", "عنوان المهمة", FieldType.TEXT, required = true, maxLength = 120),
            FieldSchema("description", "الوصف", FieldType.MULTILINE, maxLength = 1000),
            FieldSchema("priority", "الأولوية", FieldType.CHOICE, required = true, choices = priorities),
            FieldSchema("deadline", "آخر موعد", FieldType.CHOICE, required = true, choices = deadlines),
        ),
        listColumns = listOf("title", "priority"),
    )

    fun taskWithAssignee(team: List<Choice>): ModuleSchema =
        if (team.isEmpty()) task
        else task.copy(fields = task.fields + FieldSchema("assigneeId", "المكلّف بالمهمة", FieldType.CHOICE, choices = team))

    val region = ModuleSchema(
        id = "regions", title = "منطقة",
        fields = listOf(
            FieldSchema("name", "اسم المنطقة", FieldType.TEXT, required = true, maxLength = 80),
            FieldSchema("phone", "رقم التواصل", FieldType.PHONE),
        ),
        listColumns = listOf("name", "phone"),
    )

    val square = ModuleSchema(
        id = "squares", title = "مربع",
        fields = listOf(
            FieldSchema("name", "اسم المربع", FieldType.TEXT, required = true, maxLength = 80),
            FieldSchema("idNumber", "رقم الهوية", FieldType.NUMBER, maxLength = 12),
            FieldSchema("phone", "رقم التواصل", FieldType.PHONE),
        ),
        listColumns = listOf("name", "phone"),
    )

    val camp = ModuleSchema(
        id = "camps", title = "مخيم",
        fields = listOf(
            FieldSchema("name", "اسم المخيم", FieldType.TEXT, required = true, maxLength = 80),
            FieldSchema("managerName", "مدير المخيم", FieldType.TEXT, maxLength = 80),
            FieldSchema("idNumber", "رقم الهوية", FieldType.NUMBER, maxLength = 12),
            FieldSchema("phone", "رقم التواصل", FieldType.PHONE),
        ),
        listColumns = listOf("name", "managerName"),
    )
}
