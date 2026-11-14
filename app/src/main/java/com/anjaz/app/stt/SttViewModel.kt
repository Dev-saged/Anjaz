package com.anjaz.app.stt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SttViewModel @Inject constructor(private val model: WhisperModel, private val transcriber: Transcriber) : ViewModel() {
    val state: StateFlow<ModelState> = model.state
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val needModel = MutableStateFlow(false)
    private var pending: (() -> Unit)? = null

    fun downloadModel() { viewModelScope.launch { model.download(); if (model.state.value is ModelState.Ready) { needModel.value = false; pending?.invoke(); pending = null } } }
    fun dismissModel() { needModel.value = false; pending = null }

    /** يشغّل العمل إن كان النموذج جاهزاً، وإلا يطلب تنزيله ثم يكمل تلقائياً. */
    private fun whenReady(action: () -> Unit) { if (model.state.value is ModelState.Ready) action() else { pending = action; needModel.value = true } }

    fun transcribeFile(key: String, path: String, onText: (String) -> Unit) = whenReady {
        busy.value = key
        viewModelScope.launch {
            runCatching { transcriber.fromFile(path) }
                .onSuccess { if (it.isBlank()) error.value = "لم يُتعرَّف على كلام في التسجيل" else onText(it) }
                .onFailure { error.value = "تعذّر تحويل الصوت إلى نص" }
            busy.value = null
        }
    }

    fun transcribePcm(pcm: FloatArray, onText: (String) -> Unit) = whenReady {
        busy.value = "dictation"
        viewModelScope.launch {
            runCatching { transcriber.fromPcm(pcm) }
                .onSuccess { if (it.isBlank()) error.value = "لم أسمع كلاماً، حاول مرة أخرى" else onText(it) }
                .onFailure { error.value = "تعذّر الإملاء الصوتي" }
            busy.value = null
        }
    }

    fun clearError() { error.value = null }
}
