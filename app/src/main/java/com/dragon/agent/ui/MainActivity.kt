package com.dragon.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dragon.agent.ui.screens.ChatScreen
import com.dragon.agent.ui.screens.MarketScreen
import com.dragon.agent.ui.screens.SettingsScreen
import com.dragon.agent.ui.screens.SkillsScreen
import com.dragon.agent.ui.theme.DragonAgentTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 导航目标
 */
sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object Settings : Screen("settings")
    data object Skills : Screen("skills")
    data object Market : Screen("market")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DragonAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Chat.route
                    ) {
                        composable(Screen.Chat.route) {
                            ChatScreen(
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onNavigateToSkills = {
                                    navController.navigate(Screen.Skills.route)
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(Screen.Skills.route) {
                            SkillsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToMarket = {
                                    navController.navigate(Screen.Market.route)
                                }
                            )
                        }
                        composable(Screen.Market.route) {
                            MarketScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
