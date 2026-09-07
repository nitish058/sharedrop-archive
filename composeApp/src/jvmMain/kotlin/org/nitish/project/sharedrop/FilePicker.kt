package org.nitish.project.sharedrop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class FilePicker {
    actual fun pickFile(
        onFilePicked: (path: String) -> Unit,
        onFilePreparationStarted: () -> Unit,
        onFilePickFailed: () -> Unit
    ) {
        Thread {
            val dialog = FileDialog(null as Frame?, "Select a file", FileDialog.LOAD)
            dialog.isVisible = true
            val file = dialog.file ?: return@Thread
            val dir = dialog.directory ?: return@Thread
            val selectedFile = File(dir, file)
            onFilePreparationStarted()
            onFilePicked(selectedFile.absolutePath)
        }.start()
    }
}
