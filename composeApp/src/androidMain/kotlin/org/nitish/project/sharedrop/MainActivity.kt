package org.nitish.project.sharedrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        AndroidContext.deliverPickedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        AndroidContext.initialize(applicationContext)
        AndroidContext.setFilePickerLauncher {
            openDocument.launch(arrayOf("*/*"))
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidContext.clearFilePickerLauncher()
        super.onDestroy()
    }

}
