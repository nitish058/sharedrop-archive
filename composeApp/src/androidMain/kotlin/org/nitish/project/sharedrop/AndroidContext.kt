package org.nitish.project.sharedrop

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

object AndroidContext {
    private var appContextReference: WeakReference<Context>? = null
    var onFilePicked: ((path: String) -> Unit)? = null
    var onFilePreparationStarted: (() -> Unit)? = null
    var onFilePickFailed: (() -> Unit)? = null
    private var filePickerLauncherReference: WeakReference<(() -> Unit)>? = null

    fun initialize(context: Context) {
        appContextReference = WeakReference(context.applicationContext)
    }

    fun requireAppContext(): Context =
        appContextReference?.get() ?: error("Application context is not available")

    fun setFilePickerLauncher(launcher: () -> Unit) {
        filePickerLauncherReference = WeakReference(launcher)
    }

    fun clearFilePickerLauncher() {
        filePickerLauncherReference = null
    }

    fun openFilePicker(
        onFilePicked: (path: String) -> Unit,
        onFilePreparationStarted: () -> Unit,
        onFilePickFailed: () -> Unit
    ): Boolean {
        val launcher = filePickerLauncherReference?.get() ?: return false
        this.onFilePicked = onFilePicked
        this.onFilePreparationStarted = onFilePreparationStarted
        this.onFilePickFailed = onFilePickFailed
        launcher()
        return true
    }

    /**
     * Converts a document-provider URI into a local file before starting the
     * transfer. FileSender works with java.io.File, while Android's picker
     * normally returns a content:// URI rather than a filesystem path.
     */
    fun deliverPickedFile(uri: Uri?) {
        val callback = onFilePicked ?: return
        val preparationStarted = onFilePreparationStarted
        val pickFailed = onFilePickFailed
        onFilePicked = null
        onFilePreparationStarted = null
        onFilePickFailed = null
        val context = appContextReference?.get() ?: return
        if (uri == null) return

        preparationStarted?.invoke()

        Thread {
            runCatching {
                val displayName = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        )
                    } else {
                        null
                    }
                } ?: "sharedrop-file"

                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val transferDirectory = File(
                    context.cacheDir, "sharedrop-${UUID.randomUUID()}"
                )
                check(transferDirectory.mkdirs()) { "Unable to prepare transfer cache" }
                val cachedFile = File(transferDirectory, safeName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    cachedFile.outputStream().use(input::copyTo)
                } ?: error("Unable to open selected file")

                cachedFile.absolutePath
            }.onSuccess { path ->
                Handler(Looper.getMainLooper()).post { callback(path) }
            }.onFailure {
                Handler(Looper.getMainLooper()).post { pickFailed?.invoke() }
            }
        }.start()
    }
}
