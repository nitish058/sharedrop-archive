package org.nitish.project.sharedrop

actual class FileSender {
    actual fun sendFile(
        host: String,
        port: Int,
        absolutePath: String,
        onProgress: (Float) -> Unit,
        onResult: (Boolean) -> Unit
    ) {
    }

    actual fun cancel(){}
}