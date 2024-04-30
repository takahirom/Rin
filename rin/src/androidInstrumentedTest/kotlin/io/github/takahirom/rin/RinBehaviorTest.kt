package io.github.takahirom.rin

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class RinBehaviorTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule =
        RuleChain.emptyRuleChain()
            .detectLeaksAfterTestSuccessWrapping(tag = "ActivitiesDestroyed") {
                around(composeTestRule)
            }

    private val scenario: ActivityScenario<ComponentActivity>
        get() = composeTestRule.activityRule.scenario


    class StoreViewModel() : androidx.lifecycle.ViewModel() {
        val store = mutableStateMapOf<Any, Any>()
    }

    @Test
    fun navigationTest() {
        val content = @Composable {
            MaterialTheme {
                Scaffold {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "start") {
                        composable("start") {
                            Column {
                                var aRemember by remember { mutableStateOf(0) }
                                Text(
                                    text = "aRemember: $aRemember",
                                    modifier = Modifier.clickable {
                                        aRemember++
                                    }
                                        .testTag("aRemember")
                                )
                                var aRememberRetained by rememberRetained { mutableStateOf(0) }
                                Text(
                                    text = "aRememberRetained: $aRememberRetained",
                                    modifier = Modifier.clickable { aRememberRetained++ }
                                        .testTag("aRememberRetained"),
                                )
                                val viewModel =
                                    androidx.lifecycle.viewmodel.compose.viewModel<StoreViewModel>()
                                Text(
                                    text = "aViewModel: ${viewModel.store["a"] ?: 0}",
                                    modifier = Modifier.clickable {
                                        viewModel.store["a"] =
                                            (viewModel.store["a"] as? Int ?: 0) + 1
                                    }
                                        .testTag("aViewModel")
                                )
                                if (aRememberRetained % 2 == 0) {
                                    var bRememberRetained by rememberRetained { mutableStateOf(0) }
                                    Text(
                                        text = "bRememberRetained: $bRememberRetained",
                                        modifier = Modifier.clickable { bRememberRetained++ }
                                            .testTag("bRememberRetained")
                                    )
                                    var bRemember by remember { mutableStateOf(0) }
                                    Text(
                                        text = "bRemember: $bRemember",
                                        modifier = Modifier.clickable {
                                            bRemember++
                                            println("bRemember: $bRemember")
                                        }
                                            .testTag("bRemember")
                                    )
                                    Text(
                                        text = "bViewModel: ${viewModel.store["b"] ?: 0}",
                                        modifier = Modifier.clickable {
                                            viewModel.store["b"] =
                                                (viewModel.store["b"] as? Int ?: 0) + 1
                                        }
                                            .testTag("bViewModel")
                                    )
                                } else {
                                    var cRememberRetained by rememberRetained { mutableStateOf(0) }
                                    Text(
                                        text = "cRememberRetained: $cRememberRetained",
                                        modifier = Modifier.clickable { cRememberRetained++ }
                                            .testTag("cRememberRetained")
                                    )
                                    var cRemember by remember { mutableStateOf(0) }
                                    Text(
                                        text = "cRemember: $cRemember",
                                        modifier = Modifier.clickable { cRemember++ }
                                            .testTag("cRemember")
                                    )
                                    Text(
                                        text = "cViewModel: ${viewModel.store["c"] ?: 0}",
                                        modifier = Modifier.clickable {
                                            viewModel.store["c"] =
                                                (viewModel.store["c"] as? Int ?: 0) + 1
                                        }
                                            .testTag("cViewModel")
                                    )
                                }
                                Text(
                                    text = "Next",
                                    modifier = Modifier.clickable {
                                        navController.navigate("next")
                                    }
                                )
                            }
                        }
                        composable("next") {
                            Text(
                                text = "Back",
                                modifier = Modifier.clickable {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
        setActivityContent(content)

        val all = listOf("RememberRetained", "ViewModel", "Remember")

        all.forEach {
            composeTestRule.onNodeWithTag("b$it").performClick()
        }
        all.forEach {
            composeTestRule.onNodeWithTag("a$it").performClick()
        }
        all.forEach {
            composeTestRule.onNodeWithTag("c$it").performClick()
        }

        composeTestRule.onNodeWithTag("aRememberRetained").performClick()

        // If bRememberRetained is removed from the composition, the state should be reset.
        composeTestRule.onNodeWithText("bRememberRetained: 0").assertIsDisplayed()
        // Even if bViewModel is removed from the composition, the state is retained.
        composeTestRule.onNodeWithText("bViewModel: 1").assertIsDisplayed()
        // If bRemember is removed from the composition, the state is reset.
        composeTestRule.onNodeWithText("bRemember: 0").assertIsDisplayed()

        composeTestRule.onNodeWithTag("aRememberRetained").performClick()
        composeTestRule.onNodeWithTag("cRememberRetained").performClick()

        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithText("Back").performClick()

        composeTestRule.onNodeWithText("aRememberRetained: 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("aViewModel: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("aRemember: 0").assertIsDisplayed()

        // The state should be retained.
        composeTestRule.onNodeWithText("cRememberRetained: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("cViewModel: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("cRemember: 0").assertIsDisplayed()

        composeTestRule.onNodeWithTag("aRememberRetained").performClick()

        // The state should be reset.
        composeTestRule.onNodeWithText("bRememberRetained: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("bViewModel: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("bRemember: 0").assertIsDisplayed()
    }

    private fun setActivityContent(content: @Composable () -> Unit) {
        scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides activity) {
                    content()
                }
            }
        }
    }
}