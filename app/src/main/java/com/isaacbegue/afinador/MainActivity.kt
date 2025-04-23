package com.isaacbegue.afinador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.isaacbegue.afinador.ui.screen.TunerScreen
import com.isaacbegue.afinador.ui.theme.AfinadorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AfinadorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Llama a TunerScreen desde su nuevo paquete
                    TunerScreen()
                }
            }
        }
    }
}

// El @Composable fun TunerScreen() y su @Preview asociado
// ya NO deben estar en este archivo (los movimos a TunerScreen.kt)