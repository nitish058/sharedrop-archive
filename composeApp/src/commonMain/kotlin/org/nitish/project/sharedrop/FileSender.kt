package org.nitish.project.sharedrop

expect class FileSender() {
    fun sendFile(
        host: String,
        port: Int,
        absolutePath: String,
        onProgress: (Float, Long, Long) -> Unit,
        onResult: (Boolean) -> Unit
    )

    fun cancel()
}
