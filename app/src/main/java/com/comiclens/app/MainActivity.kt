package com.comiclens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comiclens.app.ui.nav.AppNav
import com.comiclens.app.ui.theme.ComicLensTheme
import com.comiclens.app.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val s by vm.settings.collectAsStateWithLifecycle()
            ComicLensTheme(mode = s.themeMode, accentIndex = s.accentIndex) {
                AppNav(onboarded = s.onboarded, onFinishOnboarding = vm::finishOnboarding)
            }
        }
    }
}
