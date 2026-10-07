package org.nitish.project.sharedrop

expect class FileReceiver() {
    fun startReceiving(
        port: Int,
        onProgress: (fileName : String, progress: Float, transferredBytes: Long, totalBytes: Long ) -> Unit,
        onFileReceived: (fileName: String, tempFilePath: String) -> Unit
    )

    /** Stops only the active download while keeping this device available for later transfers. */
    fun cancelCurrentTransfer()

    fun stopReceiving()
}
