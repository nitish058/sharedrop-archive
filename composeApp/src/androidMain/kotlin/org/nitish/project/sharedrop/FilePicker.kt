package org.nitish.project.sharedrop

actual class FilePicker {
    actual fun pickFile(
        onFilePicked: (path: String) -> Unit,
        onFilePreparationStarted: () -> Unit,
        onFilePickFailed: () -> Unit
    ) {
        AndroidContext.openFilePicker(
            onFilePicked, onFilePreparationStarted, onFilePickFailed
        )
    }
}
