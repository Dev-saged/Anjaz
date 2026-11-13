package com.anjaz.app.domain

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failure(val reason: String) : Outcome<Nothing>
}
