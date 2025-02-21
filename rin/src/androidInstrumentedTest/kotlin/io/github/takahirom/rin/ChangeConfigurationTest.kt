package io.github.takahirom.rin

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class ChangeConfigurationTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule =
        RuleChain.emptyRuleChain()
            .detectLeaksAfterTestSuccessWrapping(tag = "ActivitiesDestroyed") {
                around(composeTestRule)
            }

    private val scenario: ActivityScenario<ComponentActivity>
        get() = composeTestRule.activityRule.scenario

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun changeConfigurationTest() {
        val content = @Composable {
            MaterialTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        var aRemember by remember { mutableStateOf(0) }
                        Text(
                            text = "aRemember: $aRemember",
                            modifier = Modifier
                                .clickable {
                                    aRemember++
                                }
                                .testTag("aRemember"),
                        )

                        var aRememberRetained by rememberRetained { mutableStateOf(0) }
                        Text(
                            text = "aRememberRetained: $aRememberRetained",
                            modifier = Modifier
                                .clickable { aRememberRetained++ }
                                .testTag("aRememberRetained"),
                        )
                    }
                }
            }
        }
        setActivityContent(content)

        composeTestRule.onNodeWithTag("aRemember").performClick()
        composeTestRule.onNodeWithTag("aRememberRetained").performClick()

        composeTestRule.onNodeWithText("aRemember: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("aRememberRetained: 1").assertIsDisplayed()

        device.setOrientationLandscape()
        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithText("aRemember: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("aRememberRetained: 1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("aRemember").performClick()
        composeTestRule.onNodeWithTag("aRememberRetained").performClick()

        composeTestRule.onNodeWithText("aRemember: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("aRememberRetained: 2").assertIsDisplayed()

        device.setOrientationPortrait()
        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithText("aRemember: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("aRememberRetained: 2").assertIsDisplayed()
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