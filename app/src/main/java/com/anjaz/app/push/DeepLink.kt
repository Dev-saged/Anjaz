package com.anjaz.app.push

import kotlinx.coroutines.flow.MutableStateFlow

object DeepLink {
    val pending = MutableStateFlow<String?>(null)
}
