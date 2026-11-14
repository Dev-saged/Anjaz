package com.anjaz.app.update

import android.content.Context
import java.io.File

/** مجلد التطبيق على الهاتف: التحديثات والنسخ الاحتياطية والتصدير في مكان واحد. */
object AppFolder {
    fun root(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Anjaz").apply { mkdirs() }
    fun updates(ctx: Context): File = File(root(ctx), "updates").apply { mkdirs() }
    fun backups(ctx: Context): File = File(root(ctx), "backups").apply { mkdirs() }
    fun exports(ctx: Context): File = File(root(ctx), "exports").apply { mkdirs() }

    /** نسخ احتياطية محفوظة محليًا: نحتفظ بآخر عدد محدد فقط. */
    fun saveBackup(ctx: Context, name: String, bytes: ByteArray, keep: Int = 10): File {
        val dir = backups(ctx)
        val f = File(dir, name).apply { writeBytes(bytes) }
        dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
        return f
    }
}
