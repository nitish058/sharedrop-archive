package org.nitish.project.sharedrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        AndroidContext.context = this
        AndroidContext.appContext = applicationContext

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidContext.context = null
        super.onDestroy()
    }

}
