// Copyright (C) 2024 takahirom
// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package io.github.takahirom.rin

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth
import kotlinx.coroutines.flow.MutableStateFlow
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class RetainedTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule =
        RuleChain.emptyRuleChain().detectLeaksAfterTestSuccessWrapping(tag = "ActivitiesDestroyed") {
            around(composeTestRule)
        }

    private val scenario: ActivityScenario<ComponentActivity>
        get() = composeTestRule.activityRule.scenario

    @Test
    fun singleWithKey() {
        val content = @Composable { KeyContent("retainedText") }
        setActivityContent(content)
        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Was the text saved
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
    }

    // Suppressions temporary until we can move this back to the circuit-retained module
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    @Test
    fun clearingAfterDone() {
        setActivityContent {
            Column {
                var text2Enabled by rememberRetained { mutableStateOf(true) }
                val text1 by rememberRetained { mutableStateOf("Text") }
                Text(modifier = Modifier.testTag(TAG_RETAINED_1), text = text1)
                Button(
                    modifier = Modifier.testTag("TAG_BUTTON"),
                    onClick = { text2Enabled = !text2Enabled },
                ) {
                    Text("Toggle")
                }
                if (text2Enabled) {
                    val text2 by rememberRetained { mutableStateOf("Text") }
                    Text(modifier = Modifier.testTag(TAG_RETAINED_2), text = text2)
                }
            }
        }

        // Hold on to our Continuity instance
        val continuity = composeTestRule.activity.viewModelStore.rinViewModel()

        // We now have one list with three retained values
        // - text2Enabled
        // - text1
        // - text2
        Truth.assertThat(continuity.savedData).hasSize(3)
        Truth.assertThat(continuity.savedData.values.sumOf { it.size }).isEqualTo(3)

        // Now disable the second text
        composeTestRule.onNodeWithTag("TAG_BUTTON").performClick()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // text2 is now gone, so we only have two providers left now
        // - text2Enabled
        // - text1
        Truth.assertThat(continuity.savedData).hasSize(2)
        Truth.assertThat(continuity.savedData.values.flatten()).hasSize(2)

        // Recreate the activity
        scenario.recreate()

        // After recreation, our VM now has committed our pending values.
        // - text2Enabled
        // - text1
        Truth.assertThat(continuity.savedData).hasSize(2)
        Truth.assertThat(continuity.savedData.values.flatten()).hasSize(2)

        println("start setting setActivityContent")
        // Set different compose content what wouldn't reuse the previous ones
        setActivityContent {
            Row {
                val text1 by rememberRetained { mutableStateOf("Text") }
                Text(modifier = Modifier.testTag(TAG_RETAINED_1), text = text1)
            }
        }

        // Assert the GC step ran to remove unclaimed values. Need to wait for idle first
        composeTestRule.waitForIdle()

        // We now just have one list with one provider
        // - text1
        Truth.assertThat(continuity.savedData).hasSize(1)
        Truth.assertThat(continuity.savedData.values.single()).hasSize(1)

        // Destroy the activity, which should clear the retained values entirely
        scenario.moveToState(Lifecycle.State.DESTROYED)
        Truth.assertThat(continuity.savedData).hasSize(0)
        Truth.assertThat(continuity.savedData).hasSize(0)
    }


    // Suppressions temporary until we can move this back to the circuit-retained module
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    @Test
    fun clearingAfterDoneWhenNoOtherStates() {
        setActivityContent {
            Column {
                var text2Enabled by remember { mutableStateOf(true) }
//                val text1 by rememberRetained { mutableStateOf("Text") }
//                Text(modifier = Modifier.testTag(TAG_RETAINED_1), text = text1)
                Button(
                    modifier = Modifier.testTag("TAG_BUTTON"),
                    onClick = { text2Enabled = !text2Enabled },
                ) {
                    Text("Toggle")
                }
                if (text2Enabled) {
                    val text2 by rememberRetained { mutableStateOf("Text") }
                    Text(modifier = Modifier.testTag(TAG_RETAINED_2), text = text2)
                }
            }
        }

        // Hold on to our Continuity instance
        val continuity = composeTestRule.activity.viewModelStore.rinViewModel()

        // We now have one list with three retained values
        // - text2Enabled
        // - text1
        // - text2
        Truth.assertThat(continuity.savedData).hasSize(1)
        Truth.assertThat(continuity.savedData.values.sumOf { it.size }).isEqualTo(1)

        // Now disable the second text
        composeTestRule.onNodeWithTag("TAG_BUTTON").performClick()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // text2 is now gone, so we only have two providers left now
        // - text2Enabled
        // - text1
        Truth.assertThat(continuity.savedData).hasSize(0)
    }

    @Test
    fun singleWithNoKey() {
        val content = @Composable { KeyContent(null) }
        setActivityContent(content)
        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Was the text saved
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
    }

    @Test
    fun multipleKeys() = testMultipleRetainedContent { MultipleRetains(useKeys = true) }

    @Test
    fun multipleNoKeys() = testMultipleRetainedContent { MultipleRetains(useKeys = false) }

    @Test
    fun nestedRegistries() = testMultipleRetainedContent { NestedRetains(useKeys = true) }

    @Test
    fun nestedRegistriesNoKeys() = testMultipleRetainedContent { NestedRetains(useKeys = false) }

    @Test
    fun nestedRegistriesWithPopAndPushWithKeys() = nestedRegistriesWithPopAndPush(true)

    @Test
    fun nestedRegistriesWithPopAndPushNoKeys() = nestedRegistriesWithPopAndPush(false)

    @Test
    fun nestedRegistriesWithPopAndPushAndCannotRetainWithKeys() =
        nestedRegistriesWithPopAndPushAndCannotRetain(true)

    @Test
    fun nestedRegistriesWithPopAndPushAndCannotRetainNoKeys() =
        nestedRegistriesWithPopAndPushAndCannotRetain(false)

    @Test
    fun singleInput() {
        val inputState = MutableStateFlow("first input")
        val content =
            @Composable {
                val input by inputState.collectAsState()
                InputsContent(input)
            }
        setActivityContent(content)
        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Input didn't change, was the text saved
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
    }

    @Test
    fun changingInput() {
        val inputState = MutableStateFlow("first input")
        val content =
            @Composable {
                val input by inputState.collectAsState()
                InputsContent(input)
            }
        setActivityContent(content)
        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        // New input
        inputState.value = "second input"
        // Was the text reset with the input change
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("")
    }


    @Test
    fun oneRootRecompose() {
        val mutableState = mutableStateOf(1)
        val content =
            @Composable {
                Column {
                    RecomposingRow(1, mutableState.value)
                    RecomposingRow(2, mutableState.value)
                }
            }
        setActivityContent(content)

        composeTestRule.onNodeWithTag("button1").performClick()
        composeTestRule.onNodeWithText("Retained1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retained2").assertDoesNotExist()
        composeTestRule.onNodeWithTag("button2").performClick()
        composeTestRule.onNodeWithText("Retained1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retained2").assertIsDisplayed()

        scenario.recreate()
        setActivityContent(content)

        composeTestRule.onNodeWithText("Retained2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retained1").assertIsDisplayed()
    }

    @Composable
    private fun RecomposingRow(i: Int, recomposeInt: Int) {
        Row {
            println("recompose $i $recomposeInt")
            var retained by rememberRetained("key $i") { mutableStateOf<Boolean>(false) }
            Button(
                modifier = Modifier.testTag("button$i"),
                onClick = { retained = !retained }) {
                Text("Toggle$i")
            }
            if (retained) {
                val retained = rememberRetained("key $i inner") { mutableStateOf("Retained inner $i") }
                Text("Retained$i")
            }
        }
    }

    @Test
    fun recreateWithChangingInput() {
        val inputState = MutableStateFlow("first input")
        val content =
            @Composable {
                val input by inputState.collectAsState()
                InputsContent(input)
            }
        setActivityContent(content)
        composeTestRule.onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        // Restart the activity
        scenario.recreate()
        inputState.value = "second input"
        // Compose our content
        setActivityContent(content)
        // Was the text reset with the input change
        composeTestRule.onNodeWithTag(TAG_REMEMBER).assertTextContains("")
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("")
    }


    @Test
    fun rememberObserver() {
        val subject =
            object : RetainedObserver {
                var onRememberCalled: Int = 0
                    private set

                var onForgottenCalled: Int = 0
                    private set

                override fun onForgotten() {
                    onForgottenCalled++
                }

                override fun onRemembered() {
                    onRememberCalled++
                }
            }

        val content =
            @Composable {
                rememberRetained { subject }
                Unit
            }
        setActivityContent(content)

        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(0)

        // Restart the activity
        scenario.recreate()
        // Compose our content again
        setActivityContent(content)

        // Assert that onRemembered was not called again
        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(0)

        // Now finish the Activity
        scenario.close()

        // Assert that the observer was forgotten
        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(1)
    }

    @Test
    fun rememberObserver_nestedRegistries() {
        val subject =
            object : RetainedObserver {
                var onRememberCalled: Int = 0
                    private set

                var onForgottenCalled: Int = 0
                    private set

                override fun onForgotten() {
                    onForgottenCalled++
                }

                override fun onRemembered() {
                    onRememberCalled++
                }
            }

        val content =
            @Composable {
                val parentViewModelStoreOwner = LocalViewModelStoreOwner.current
                val nestedRegistryLevel1 = rememberRetained {
                    ViewModelStore().apply {
                        parentViewModelStoreOwner!!.viewModelStore!!.let {
                            it.put("nestedRegistryLevel1", object : ViewModel() {
                                override fun onCleared() {
                                    super.onCleared()
                                    this@apply.clear()
                                }
                            })
                        }
                    }
                }
                CompositionLocalProvider(LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore = nestedRegistryLevel1
                }) {
                    val nestedRegistryLevel2 = rememberRetained {
                        ViewModelStore().apply {
                            parentViewModelStoreOwner!!.viewModelStore!!.let {
                                it.put("nestedRegistryLevel2", object : ViewModel() {
                                    override fun onCleared() {
                                        super.onCleared()
                                        this@apply.clear()
                                    }
                                })
                            }
                        }
                    }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = nestedRegistryLevel2
                    }) {
                        @Suppress("UNUSED_VARIABLE") val retainedSubject = rememberRetained { subject }
                    }
                }
            }
        setActivityContent(content)

        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(0)

        // Restart the activity
        scenario.recreate()
        // Compose our content again
        setActivityContent(content)

        // Assert that onRemembered was not called again
        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(0)

        // Now finish the Activity
        scenario.close()

        // Assert that the observer was forgotten
        Truth.assertThat(subject.onRememberCalled).isEqualTo(1)
        Truth.assertThat(subject.onForgottenCalled).isEqualTo(1)
    }

    private fun nestedRegistriesWithPopAndPush(useKeys: Boolean) {
        val content = @Composable { NestedRetainWithPushAndPop(useKeys = useKeys) }
        setActivityContent(content)

        // Assert that Retained 1 is visible & Retained 2 does not exist
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // Now click the button to show the child content
        composeTestRule.onNodeWithTag(TAG_BUTTON_SHOW).performClick()

        // Perform our initial text input
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained1")
        composeTestRule
            .onNodeWithTag(TAG_RETAINED_2)
            .assertIsDisplayed()
            .performTextInput("Text_Retained2")

        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")

        // Now click the button to hide the nested content (aka a pop)
        composeTestRule.onNodeWithTag(TAG_BUTTON_HIDE).performClick()

        // Assert that Retained 1 is visible & Retained 2 does not exist
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // Now click the button to show the nested content again (aka a push)
        composeTestRule.onNodeWithTag(TAG_BUTTON_SHOW).performClick()

        // Assert that the child content is using the retained content
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")

        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Was the text saved
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")
    }


    private fun nestedRegistriesWithPopAndPushAndCannotRetain(useKeys: Boolean) {
        val content = @Composable { NestedRetainWithPushAndPopAndCannotRetain(useKeys = useKeys) }
        setActivityContent(content)

        // Assert that Retained 1 is visible & Retained 2 does not exist
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // Now click the button to show the child content
        composeTestRule.onNodeWithTag(TAG_BUTTON_SHOW).performClick()

        // Perform our initial text input
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained1")
        composeTestRule
            .onNodeWithTag(TAG_RETAINED_2)
            .assertIsDisplayed()
            .performTextInput("Text_Retained2")

        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")

        // Now click the button to hide the nested content (aka a pop)
        composeTestRule.onNodeWithTag(TAG_BUTTON_HIDE).performClick()

        // Assert that Retained 1 is visible & Retained 2 does not exist
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertDoesNotExist()

        // Now click the button to show the nested content again (aka a push)
        composeTestRule.onNodeWithTag(TAG_BUTTON_SHOW).performClick()

        // Assert that the child content is _not_ using the retained content since can retain checker is
        // false
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assert(!hasText("Text_Retained2"))

        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Was the text not saved
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assert(!hasText("Text_Retained2"))
    }

    //
//  private val rootViewModelStoreOwner = object : ViewModelStoreOwner {
//    override val viewModelStore: ViewModelStore = ViewModelStore()
//  }
    private fun setActivityContent(content: @Composable () -> Unit) {
        scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides activity) {
                    content()
                }
            }
        }
    }

    private fun testMultipleRetainedContent(content: @Composable () -> Unit) {
        setActivityContent(content)

        composeTestRule.onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).performTextInput("Text_Retained2")
        composeTestRule.onNodeWithTag(TAG_RETAINED_3).performTextInput("2")
        // Check that our input worked
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")
        composeTestRule.onNodeWithTag(TAG_RETAINED_3).assertTextContains("2", substring = true)
        // Restart the activity
        scenario.recreate()
        // Compose our content
        setActivityContent(content)
        // Was the text saved
        composeTestRule.onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained1")
        composeTestRule.onNodeWithTag(TAG_RETAINED_2).assertTextContains("Text_Retained2")
        composeTestRule.onNodeWithTag(TAG_RETAINED_3).assertTextContains("2", substring = true)
    }
}


private const val TAG_REMEMBER = "remember"
private const val TAG_RETAINED_1 = "retained1"
private const val TAG_RETAINED_2 = "retained2"
private const val TAG_RETAINED_3 = "retained3"
private const val TAG_BUTTON_SHOW = "btn_show"
private const val TAG_BUTTON_HIDE = "btn_hide"

@Composable
private fun KeyContent(key: String?, tag1: String = TAG_REMEMBER, tag2: String = TAG_RETAINED_1) {
    var text1 by remember { mutableStateOf("") }
    // By default rememberSavable uses it's line number as its key, this doesn't seem
    // to work when testing, instead pass a key
    var retainedText: String by rememberRetained(key = key) { mutableStateOf("") }
    Column {
        TextField(
            modifier = Modifier.testTag(tag1),
            value = text1,
            onValueChange = { text1 = it },
            label = {},
        )
        TextField(
            modifier = Modifier.testTag(tag2),
            value = retainedText,
            onValueChange = { retainedText = it },
            label = {},
        )
    }
}

@Composable
private fun MultipleRetains(useKeys: Boolean) {
    var retainedInt: Int by
    rememberRetained(key = "retainedInt".takeIf { useKeys }) { mutableStateOf(0) }
    var retainedText1: String by
    rememberRetained(key = "retained1".takeIf { useKeys }) { mutableStateOf("") }
    var retainedText2: String by
    rememberRetained(key = "retained2".takeIf { useKeys }) { mutableStateOf("") }
    Column {
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_1),
            value = retainedText1,
            onValueChange = { retainedText1 = it },
            label = {},
        )
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_2),
            value = retainedText2,
            onValueChange = { retainedText2 = it },
            label = {},
        )
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_3),
            value = retainedInt.toString(),
            onValueChange = { retainedInt = it.toInt() },
            label = {},
        )
    }
}

@Composable
private fun NestedRetains(useKeys: Boolean) {
    var retainedText1: String by
    rememberRetained(key = "retained1".takeIf { useKeys }) { mutableStateOf("") }

    Column {
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_1),
            value = retainedText1,
            onValueChange = { retainedText1 = it },
            label = {},
        )

        val nestedRegistryLevel1 = rememberRetained { ViewModelStore() }
        CompositionLocalProvider(LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = nestedRegistryLevel1
        }) {
            NestedRetainLevel1(useKeys)
        }
    }
}

@Composable
private fun NestedRetainLevel1(useKeys: Boolean) {
    var retainedText2: String by
    rememberRetained(key = "retained2".takeIf { useKeys }) { mutableStateOf("") }

    TextField(
        modifier = Modifier.testTag(TAG_RETAINED_2),
        value = retainedText2,
        onValueChange = { retainedText2 = it },
        label = {},
    )

    val nestedRegistry = rememberRetained {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    val lifecycleRegistry = rememberRetained {
        object:LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = LifecycleRegistry(this)

        }
    }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides nestedRegistry,
        LocalLifecycleOwner provides lifecycleRegistry,
    ) {
        NestedRetainLevel2(useKeys)
    }
}

@Composable
private fun NestedRetainLevel2(useKeys: Boolean) {
    var retainedInt: Int by
    rememberRetained(key = "retainedInt".takeIf { useKeys }) { mutableStateOf(0) }

    TextField(
        modifier = Modifier.testTag(TAG_RETAINED_3),
        value = retainedInt.toString(),
        onValueChange = { retainedInt = it.toInt() },
        label = {},
    )
}

@Composable
private fun NestedRetainWithPushAndPop(useKeys: Boolean) {
    var retainedText1: String by
    rememberRetained(key = "retained1".takeIf { useKeys }) { mutableStateOf("") }

    Column {
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_1),
            value = retainedText1,
            onValueChange = { retainedText1 = it },
            label = {},
        )

        val showNestedContent = rememberRetained { mutableStateOf(false) }

        Button(
            onClick = { showNestedContent.value = false },
            modifier = Modifier.testTag(TAG_BUTTON_HIDE),
        ) {
            Text(text = "Hide child")
        }

        Button(
            onClick = { showNestedContent.value = true },
            modifier = Modifier.testTag(TAG_BUTTON_SHOW),
        ) {
            Text(text = "Show child")
        }

        // Keep the retained state registry around even if showNestedContent becomes false
        CompositionLocalProvider(
            LocalShouldRemoveRetainedWhenRemovingComposition provides { false },
        ) {
            if (showNestedContent.value) {
                val nestedRegistry = rememberRetained {
                    object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = ViewModelStore()
                    }
                }
                val lifecycleRegistry = rememberRetained {
                    object:LifecycleOwner {
                        override val lifecycle: Lifecycle = LifecycleRegistry(this)

                    }
                }
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides nestedRegistry,
                    LocalLifecycleOwner provides lifecycleRegistry,
                ) {
                    NestedRetainLevel1(useKeys)
                }
            }
        }
    }
}

@Composable
private fun NestedRetainWithPushAndPopAndCannotRetain(useKeys: Boolean) {
    var retainedText1: String by
    rememberRetained(key = "retained1".takeIf { useKeys }) { mutableStateOf("") }

    Column {
        TextField(
            modifier = Modifier.testTag(TAG_RETAINED_1),
            value = retainedText1,
            onValueChange = { retainedText1 = it },
            label = {},
        )

        val showNestedContent = rememberRetained { mutableStateOf(false) }

        Button(
            onClick = { showNestedContent.value = false },
            modifier = Modifier.testTag(TAG_BUTTON_HIDE),
        ) {
            Text(text = "Hide child")
        }

        Button(
            onClick = { showNestedContent.value = true },
            modifier = Modifier.testTag(TAG_BUTTON_SHOW),
        ) {
            Text(text = "Show child")
        }

        // Keep the retained state registry around even if showNestedContent becomes false
        CompositionLocalProvider(
            LocalShouldRemoveRetainedWhenRemovingComposition provides { false },
        ) {
            if (showNestedContent.value) {
                val nestedRegistry = rememberRetained { ViewModelStore() }
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = nestedRegistry
                    },
                    LocalShouldRemoveRetainedWhenRemovingComposition provides { true },
                ) {
                    NestedRetainLevel1(useKeys)
                }
            }
        }
    }
}

@Composable
private fun InputsContent(input: String) {
    var text1 by remember(input) { mutableStateOf("") }
    var retainedText: String by rememberRetained(input) { mutableStateOf("") }
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
