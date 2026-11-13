package com.anjaz.app.domain

enum class Role(val rank: Int, val label: String) {
    CENTRAL(5, "الإدارة المركزية"),
    REGION_MANAGER(4, "مدير المنطقة"),
    REGION_ADMIN(3, "الإداري العام للمنطقة"),
    SQUARE_MANAGER(2, "مدير المربع"),
    FIELD_STAFF(1, "موظف ميداني");

    fun canCreate(target: Role): Boolean = target.rank < rank || (this == CENTRAL && target == CENTRAL)
}
