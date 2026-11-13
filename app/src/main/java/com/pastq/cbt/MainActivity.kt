package com.pastq.cbt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pastq.cbt.ui.DownloadScreen
import com.pastq.cbt.update.Updater
import com.pastq.cbt.ui.ExamScreen
import com.pastq.cbt.ui.ExamSetupScreen
import com.pastq.cbt.ui.HistoryScreen
import com.pastq.cbt.ui.HomeScreen
import com.pastq.cbt.ui.PracticeScreen
import com.pastq.cbt.ui.PracticeSetupScreen
import com.pastq.cbt.ui.ResultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as CbtApplication).repository
        Updater.check()
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                repo,
                                onPractice = { nav.navigate("practiceSetup") },
                                onExam = { nav.navigate("examSetup") },
                                onHistory = { nav.navigate("history") },
                                onDownload = { nav.navigate("download") },
                            )
                        }
                        composable("practiceSetup") {
                            PracticeSetupScreen(repo, onStart = { nav.navigate("practice") }, onBack = { nav.popBackStack() })
                        }
                        composable("practice") { PracticeScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("examSetup") {
                            ExamSetupScreen(repo, onStart = { nav.navigate("exam") }, onBack = { nav.popBackStack() })
                        }
                        composable("exam") {
                            ExamScreen(repo, onFinished = {
                                nav.navigate("result") { popUpTo("home") }
                            })
                        }
                        composable("result") {
                            ResultScreen(onHome = { nav.popBackStack("home", inclusive = false) })
                        }
                        composable("download") { DownloadScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("history") { HistoryScreen(repo, onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.setForeground(true)
        Updater.onResume()
    }

    override fun onPause() {
        Updater.setForeground(false)
        super.onPause()
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(primary = Color(0xFF7FD6A0))
    } else {
        lightColorScheme(primary = Color(0xFF0B7A3E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
