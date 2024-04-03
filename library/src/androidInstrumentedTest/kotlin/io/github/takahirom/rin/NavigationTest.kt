package io.github.takahirom.rin

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NavigationTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule =
        RuleChain.emptyRuleChain().detectLeaksAfterTestSuccessWrapping(tag = "ActivitiesDestroyed") {
            around(composeTestRule)
        }

    private val scenario: ActivityScenario<ComponentActivity>
        get() = composeTestRule.activityRule.scenario

    @Test
    fun navigationTest() {
        val content = @Composable {
            MaterialTheme {
                Scaffold {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "start") {
                        composable("start") {
                            Column {
                                Button(onClick = {
                                    navController.navigate("next")
                                }) {
                                    Text("Next")
                                }
                                KeyContent("retainedText1")
                            }
                        }
                        composable("next") {
                            Column {
                                KeyContent("retainedText2")
                            }
                        }
                    }
                }
            }
        }
        setActivityContent(content)

        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")

        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")

        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("")

        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember2")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained2")

        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember2")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained2")

        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained2")

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressed()
        }

        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
    }


    class Repository() {
        val stateFlow = MutableStateFlow(0)
        fun flow(): StateFlow<Int> {
            return stateFlow
        }

        fun increment() {
            stateFlow.value += 1
        }
    }

    @Test
    fun navigationTestWithProducer() {
        val repository = Repository()
        var startScreenRinViewModel: RinViewModel? = null
        val content = @Composable {
            MaterialTheme {
                Scaffold {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "start") {
                        composable("start") {
                            Column {
                                val state by produceRetainedState("-1") {
                                    repository
                                        .flow()
                                        .collect {
                                            value = it.toString()
                                        }
                                }
                                startScreenRinViewModel =
                                    LocalViewModelStoreOwner.current!!.viewModelStore.get("RinViewModel") as RinViewModel
                                Text(state)
                                Button(onClick = {
                                    repository.increment()
                                }) {
                                    Text("Increment")
                                }
                                Button(onClick = {
                                    navController.navigate("next")
                                }) {
                                    Text("GoNext")
                                }
                            }
                        }
                        composable("next") {
                            Column {
                                Text("Next")
                                Button(onClick = {
                                    repository.increment()
                                }) {
                                    Text("Increment")
                                }
                            }
                        }
                    }
                }
            }
        }
        setActivityContent(content)

        composeTestRule.onNodeWithText("Increment").performClick()
        composeTestRule.onNodeWithText("Increment").performClick()
        composeTestRule.onNodeWithText("Increment").performClick()

        composeTestRule.onNodeWithText("GoNext").performClick()

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Increment").performClick()

        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Increment").performClick()

        // Check first screen isn't working
        Truth.assertThat(
            (startScreenRinViewModel!!.savedData.values.single().first().value as MutableState<String>).value
        ).isEqualTo("3")

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressed()
        }

        composeTestRule.onNodeWithText("Increment").performClick()

        composeTestRule.onNodeWithText("6")
    }


    class RetainedObserverImpl(val repository: Repository) : RetainedObserver {
        var mutableState: MutableState<String> = mutableStateOf("-1")
        val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        override fun onForgotten() {
            coroutineScope.cancel()
        }

        override fun onRemembered() {
            coroutineScope.launch {
                repository
                    .flow()
                    .collect {
                        mutableState.value = it.toString()
                    }
            }
        }
    }

    @Test
    fun navigationTestWithRetainedObserver() {
        val repository = Repository()
        var startScreenRinViewModel: RinViewModel? = null
        val content = @Composable {
            MaterialTheme {
                Scaffold {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "start") {
                        composable("start") {
                            Column {
                                val state = rememberRetained {
                                    RetainedObserverImpl(repository)
                                }.mutableState.value

                                startScreenRinViewModel =
                                    LocalViewModelStoreOwner.current!!.viewModelStore.get("RinViewModel") as RinViewModel
                                Text(state)
                                Button(onClick = {
                                    repository.increment()
                                }) {
                                    Text("Increment")
                                }
                                Button(onClick = {
                                    navController.navigate("next")
                                }) {
                                    Text("GoNext")
                                }
                            }
                        }
                        composable("next") {
                            Column {
                                Text("Next")
                                Button(onClick = {
                                    repository.increment()
                                }) {
                                    Text("Increment")
                                }
                            }
                        }
                    }
                }
            }
        }
        setActivityContent(content)

        composeTestRule.onNodeWithText("Increment").performClick()
        composeTestRule.onNodeWithText("Increment").performClick()
        composeTestRule.onNodeWithText("Increment").performClick()

        composeTestRule.onNodeWithText("GoNext").performClick()

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Increment").performClick()

        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Increment").performClick()

        // Check first screens' RetainedObserverImpl working
        println(
            "startScreenRinViewModel!!.savedData.values.single().first().value: ${
                (startScreenRinViewModel!!.savedData.values.single()
                    .first().value as RetainedObserverImpl).mutableState.value
            }"
        )
        composeTestRule.waitUntil {
            (startScreenRinViewModel!!.savedData.values.single()
                .first().value as RetainedObserverImpl).mutableState.value == "5"
        }
        Truth.assertThat(
            (startScreenRinViewModel!!.savedData.values.single()
                .first().value as RetainedObserverImpl).mutableState.value
        ).isEqualTo("5")

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressed()
        }

        composeTestRule.onNodeWithText("Increment").performClick()

        composeTestRule.onNodeWithText("6")
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


private const val TAG_REMEMBER = "remember"
private const val TAG_RETAINED_1 = "retained1"
private const val TAG_RETAINED_2 = "retained2"
private const val TAG_RETAINED_3 = "retained3"
private const val TAG_BUTTON_SHOW = "btn_show"
private const val TAG_BUTTON_HIDE = "btn_hide"


@Composable
private fun KeyContent(key: String?) {
    var text1 by remember { mutableStateOf("") }
    // By default rememberSavable uses it's line number as its key, this doesn't seem
    // to work when testing, instead pass a key
    var retainedText: String by rememberRetained(key = key) { mutableStateOf("") }
    Column {
        TextField(
            modifier = Modifier.testTag(TAG_REMEMBER),
            value = text1,
            onValueChange = { text1 = it },
            label = {},
        )
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_1),
            value = retainedText,
            onValueChange = { retainedText = it },
            label = {},
        )
    }
}