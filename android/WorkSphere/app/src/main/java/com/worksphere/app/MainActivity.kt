package com.worksphere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.worksphere.app.data.AuthService
import com.worksphere.app.ui.theme.WorkSphereTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialise auth service with application context so SharedPreferences
        // survive configuration changes.
        AuthService.init(applicationContext)

        setContent {
            WorkSphereTheme {
                WorkSphereApp()
            }
        }
    }
}
