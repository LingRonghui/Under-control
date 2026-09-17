package com.jingcai.predict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jingcai.predict.ui.AppRoot
import com.jingcai.predict.ui.theme.JingCaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            JingCaiTheme(darkTheme = dark) {
                AppRoot(
                    darkTheme = dark,
                    onThemeChange = { dark = it }
                )
            }
        }
    }
}
