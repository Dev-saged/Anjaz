package com.anjaz.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val uid: String,
    val username: String,
    val displayName: String,
    val role: String,
    val regionId: String? = null,
    val squareId: String? = null,
) {
    val roleEnum: Role get() = Role.entries.firstOrNull { it.name == role } ?: Role.FIELD_STAFF
    val canAssign: Boolean get() = roleEnum != Role.FIELD_STAFF
}
