package org.nitish.project.sharedrop

actual class FileReceiver {
    actual fun startReceiving(
        port: Int,
        onProgress: (fileName: String, progress: Float) -> Unit,
        onFileReceived: (fileName: String, tempFilePath: String) -> Unit
    ) {
    }

    actual fun cancelCurrentTransfer() {
    }

    actual fun stopReceiving() {
    }
}