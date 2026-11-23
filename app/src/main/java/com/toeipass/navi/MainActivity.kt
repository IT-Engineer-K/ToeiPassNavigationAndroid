package com.toeipass.navi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.toeipass.navi.ui.ToeiPassScreen
import com.toeipass.navi.ui.theme.ToeiPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToeiPassTheme {
                ToeiPassScreen()
            }
        }
    }
}
