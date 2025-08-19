// Copyright (C) 2024 takahirom
// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.lifecycle.*
import kotlin.test.Test
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import io.github.takahirom.rin.RetainedObserver
import io.github.takahirom.rin.RinViewModel
import io.github.takahirom.rin.rememberRetained
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals

private const val TAG_REMEMBER = "remember"
private const val TAG_RETAINED_1 = "retained1"

internal fun ViewModelStore.rinViewModel(): RinViewModel {
    return get("androidx.lifecycle.ViewModelProvider.DefaultKey:io.github.takahirom.rin.RinViewModel") as RinViewModel
}

class MacosNavigationTest {
    @OptIn(InternalComposeApi::class)
    @Test
    fun macOSMoleculeTest() {
        val coroutineScope = CoroutineScope(Job())
        assertEquals(
            expected = "macOS test",
            actual = coroutineScope.launchMolecule(RecompositionMode.Immediate) {
                val nestedRegistry = remember {
                    object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = ViewModelStore()
                    }
                }
                val lifecycleRegistry = remember {
                    object : LifecycleOwner {
                        override val lifecycle: Lifecycle = LifecycleRegistry(this)
                    }
                }
                CompositionLocalProviderWithReturnValue(LocalViewModelStoreOwner provides nestedRegistry) {
                    CompositionLocalProviderWithReturnValue(LocalLifecycleOwner provides lifecycleRegistry) {
                        var retainedText: String by rememberRetained { mutableStateOf("macOS test") }
                        retainedText
                    }
                }
            }.value
        )
        coroutineScope.cancel()
    }

    @Composable
    @OptIn(InternalComposeApi::class)
    fun <T> CompositionLocalProviderWithReturnValue(
        value: ProvidedValue<*>,
        content: @Composable () -> T,
    ): T {
        currentComposer.startProvider(value)
        val result = content()
        currentComposer.endProvider()
        return result
    }

    @Composable
    private fun MacOSKeyContent(key: String?) {
        var text1 by remember { mutableStateOf("") }
        var retainedText: String by rememberRetained(key = key) { mutableStateOf("") }
        Column {
            TextField(
                modifier = Modifier.testTag(TAG_REMEMBER),
                value = text1,
                onValueChange = {
                    text1 = it
                },
                label = { Text("Regular State") },
            )
            TextField(
                modifier = Modifier.testTag(TAG_RETAINED_1),
                value = retainedText,
                onValueChange = { retainedText = it },
                label = { Text("Retained State") },
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun macOSNavigationTest() {
        val content = @Composable {
            CompositionLocalProvider(LocalLifecycleOwner provides object : LifecycleOwner {
                override val lifecycle: Lifecycle = LifecycleRegistry(this)
            }) {
                MaterialTheme {
                    Scaffold { paddingValues ->
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "macOS-start") {
                            composable("macOS-start") {
                                Column {
                                    Button(onClick = {
                                        navController.navigate("macOS-next")
                                    }) {
                                        Text("Go to Next (macOS)")
                                    }
                                    MacOSKeyContent("retainedTextMacOS1")
                                }
                            }
                            composable("macOS-next") {
                                Column {
                                    MacOSKeyContent("retainedTextMacOS2")
                                    Button(onClick = {
                                        navController.popBackStack()
                                    }) {
                                        Text("Back to Start (macOS)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        runComposeUiTest {
            setContent(content)

            onNodeWithTag(TAG_REMEMBER).performTextInput("macOS_Remember")
            onNodeWithTag(TAG_RETAINED_1).performTextInput("macOS_Retained")

            onNodeWithTag(TAG_REMEMBER).assertTextContains("macOS_Remember")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("macOS_Retained")

            onNodeWithText("Go to Next (macOS)").performClick()

            onNodeWithTag(TAG_REMEMBER).assertTextContains("")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("")

            onNodeWithTag(TAG_REMEMBER).performTextInput("macOS_Remember2")
            onNodeWithTag(TAG_RETAINED_1).performTextInput("macOS_Retained2")

            onNodeWithTag(TAG_REMEMBER).assertTextContains("macOS_Remember2")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("macOS_Retained2")

            onNodeWithText("Back to Start (macOS)").performClick()

            onNodeWithTag(TAG_REMEMBER).assertTextContains("")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("macOS_Retained")
        }
    }

    class MacOSRepository {
        val stateFlow = MutableStateFlow(0)
        fun flow(): StateFlow<Int> {
            return stateFlow
        }

        fun increment() {
            stateFlow.value += 1
        }

        fun decrement() {
            stateFlow.value -= 1
        }
    }

    class MacOSRetainedObserverImpl(val repository: MacOSRepository) : RetainedObserver {
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
                        mutableState.value = "macOS: $it"
                    }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun macOSIncrementAcrossScreenTest() {
        val repository = MacOSRepository()
        var startScreenRinViewModel: RinViewModel? = null
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides object : LifecycleOwner {
                    override val lifecycle: Lifecycle = LifecycleRegistry(this)
                }) {
                    MaterialTheme {
                        Scaffold { paddingValues ->
                            val navController = rememberNavController()
                            NavHost(navController = navController, startDestination = "macOS-counter") {
                                composable("macOS-counter") {
                                    Column {
                                        val state = rememberRetained {
                                            MacOSRetainedObserverImpl(repository)
                                        }.mutableState.value

                                        startScreenRinViewModel =
                                            LocalViewModelStoreOwner.current!!.viewModelStore.rinViewModel()
                                        
                                        Text(state)
                                        Button(onClick = {
                                            repository.increment()
                                        }) {
                                            Text("Increment (macOS)")
                                        }
                                        Button(onClick = {
                                            repository.decrement()
                                        }) {
                                            Text("Decrement (macOS)")
                                        }
                                        Button(onClick = {
                                            navController.navigate("macOS-detail")
                                        }) {
                                            Text("Go to Detail")
                                        }
                                    }
                                }
                                composable("macOS-detail") {
                                    Column {
                                        Text("macOS Detail Screen")
                                        Button(onClick = {
                                            repository.increment()
                                        }) {
                                            Text("Increment from Detail")
                                        }
                                        Button(onClick = {
                                            navController.popBackStack()
                                        }) {
                                            Text("Back to Counter")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            onNodeWithText("macOS: 0").assertIsDisplayed()
            
            onNodeWithText("Increment (macOS)").performClick()
            onNodeWithText("Increment (macOS)").performClick()
            onNodeWithText("macOS: 2").assertIsDisplayed()

            onNodeWithText("Decrement (macOS)").performClick()
            onNodeWithText("macOS: 1").assertIsDisplayed()

            onNodeWithText("Go to Detail").performClick()
            onNodeWithText("macOS Detail Screen").assertIsDisplayed()
            
            onNodeWithText("Increment from Detail").performClick()

            onNodeWithText("Back to Counter").performClick()

            onNodeWithText("macOS: 2").assertIsDisplayed()
            
            startScreenRinViewModel?.savedData?.forEach {
                println("macOS saved data: $it")
            }

            onNodeWithText("Increment (macOS)").performClick()
            onNodeWithText("macOS: 3").assertIsDisplayed()
        }
    }
}