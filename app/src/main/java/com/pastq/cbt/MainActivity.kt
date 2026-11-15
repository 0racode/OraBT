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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.pastq.cbt.data.LiveManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pastq.cbt.ui.AccountScreen
import com.pastq.cbt.ui.AdminCreateScreen
import com.pastq.cbt.ui.AdminDisputedScreen
import com.pastq.cbt.ui.AdminHomeScreen
import com.pastq.cbt.ui.AdminLoginScreen
import com.pastq.cbt.ui.AdminResultsScreen
import com.pastq.cbt.ui.LiveJoinScreen
import com.pastq.cbt.ui.LivePlayScreen
import com.pastq.cbt.ui.DownloadScreen
import com.pastq.cbt.ui.LeaderboardScreen
import com.pastq.cbt.update.Updater
import com.pastq.cbt.ui.ExamScreen
import com.pastq.cbt.ui.ExamSetupScreen
import com.pastq.cbt.ui.HistoryScreen
import com.pastq.cbt.ui.HomeScreen
import com.pastq.cbt.ui.PracticeScreen
import com.pastq.cbt.ui.PracticeSetupScreen
import com.pastq.cbt.ui.ResultScreen

class MainActivity : ComponentActivity() {
    private val noticePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** A tapped live-test notice carries the session code; the join screen fills it in. */
    private fun handleNotice(i: Intent?) {
        if (i?.getStringExtra("kind") == "live") {
            LiveManager.pendingCode.value = i.getStringExtra("code")?.takeIf { it.isNotBlank() } ?: "-"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotice(intent)
    }

    // Android 13+ needs a one-time permission to show notices.
    private fun askForNotices() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("push", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean("asked", true).apply()
        noticePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as CbtApplication).repository
        Updater.check()
        handleNotice(intent)
        askForNotices()
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val pending by LiveManager.pendingCode.collectAsStateWithLifecycle()
                    LaunchedEffect(pending) {
                        if (pending != null && nav.currentDestination?.route != "liveJoin") nav.navigate("liveJoin")
                    }
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                repo,
                                onPractice = { nav.navigate("practiceSetup") },
                                onExam = { nav.navigate("examSetup") },
                                onHistory = { nav.navigate("history") },
                                onDownload = { nav.navigate("download") },
                                onLeaderboard = { nav.navigate("leaderboard") },
                                onAccount = { nav.navigate("account") },
                                onLive = { nav.navigate("liveJoin") },
                            )
                        }
                        composable("liveJoin") {
                            LiveJoinScreen(
                                onBack = { nav.popBackStack() },
                                onJoined = { nav.navigate("livePlay") },
                                onAdmin = { nav.navigate("adminLogin") },
                            )
                        }
                        composable("livePlay") { LivePlayScreen(onBack = { nav.popBackStack() }) }
                        composable("adminLogin") {
                            AdminLoginScreen(
                                onBack = { nav.popBackStack() },
                                onLoggedIn = { nav.navigate("adminHome") { popUpTo("adminLogin") { inclusive = true } } },
                            )
                        }
                        composable("adminHome") {
                            AdminHomeScreen(
                                onBack = { nav.popBackStack("liveJoin", inclusive = false) },
                                onCreate = { nav.navigate("adminCreate") },
                                onResults = { nav.navigate("adminResults/$it") },
                                onDisputed = { nav.navigate("adminDisputed") },
                            )
                        }
                        composable("adminCreate") {
                            AdminCreateScreen(repo, onBack = { nav.popBackStack() }, onCreated = { nav.popBackStack() })
                        }
                        composable("adminResults/{id}") { entry ->
                            AdminResultsScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("adminDisputed") { AdminDisputedScreen(onBack = { nav.popBackStack() }) }
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
                            ResultScreen(
                                onHome = { nav.popBackStack("home", inclusive = false) },
                                onAccount = { nav.navigate("account") },
                            )
                        }
                        composable("leaderboard") { LeaderboardScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("account") { AccountScreen(onBack = { nav.popBackStack() }) }
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
