package com.chinmai.yogasense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chinmai.yogasense.ui.navigation.YogaSenseApp
import com.chinmai.yogasense.ui.theme.YogaSenseAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Bar icon colour is set per destination (SystemBarIcons): dark on light screens, light in practice.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.chinmai.yogasense.perf.PerfLog.init(this)
        com.chinmai.yogasense.perf.PerfLog.mark("activity onCreate")
        setContent {
            YogaSenseAITheme {
                YogaSenseApp()
            }
        }
    }
}
