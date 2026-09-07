package org.nitish.project.sharedrop

expect class FilePicker() {
    fun pickFile(
        onFilePicked: (path: String) -> Unit,
        onFilePreparationStarted: () -> Unit,
        onFilePickFailed: () -> Unit
    )
}
