package com.anjaz.app.media

import java.io.File

sealed interface Download {
    data object Done : Download
    data object Gone : Download
}

/** نقل الوسائط عبر الـ Worker كجسر مؤقت: رفع، تنزيل، وتأكيد استلام. */
interface MediaTransport {
    suspend fun upload(roomId: String, mediaId: String, file: File, mime: String)
    suspend fun download(mediaId: String, dest: File): Download
    suspend fun ack(mediaId: String)
}

object NoMedia : MediaTransport {
    override suspend fun upload(roomId: String, mediaId: String, file: File, mime: String) = Unit
    override suspend fun download(mediaId: String, dest: File): Download = Download.Gone
    override suspend fun ack(mediaId: String) = Unit
}
