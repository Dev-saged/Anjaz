package com.anjaz.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** قفل التطبيق بالبصمة أو برمز قفل الجهاز عند الفتح بعد غياب قصير. */
object AppLock {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    const val GRACE_MS = 30_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("applock", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("on", on).apply()
    fun available(ctx: Context) = BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onOk: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح إنجاز").setSubtitle("أكّد هويتك للمتابعة").setAllowedAuthenticators(AUTH).build()
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        p.authenticate(info)
    }
}
