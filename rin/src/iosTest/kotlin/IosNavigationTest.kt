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


class IosNavigationTest {
    @OptIn(InternalComposeApi::class)
    @Test
    fun test() {
        assertEquals(
            expected = "test",
            actual = CoroutineScope(Job()).launchMolecule(RecompositionMode.Immediate) {
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
                        var retainedText: String by rememberRetained { mutableStateOf("test") }
                        retainedText
                    }
                }
            }.value
        )
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun navigationTest() {
        val content = @Composable {
            CompositionLocalProvider(LocalLifecycleOwner provides object : LifecycleOwner {
                override val lifecycle: Lifecycle = LifecycleRegistry(this)
            }) {
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
                                Button(onClick = {
                                    navController.popBackStack()
                                }) {
                                    Text("Back")
                                }
                            }
                        }
                    }
                }
            }
        }
        runComposeUiTest {
            setContent(content)

            onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember")
            onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained")

            onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")

            onNodeWithText("Next").performClick()

            onNodeWithTag(TAG_REMEMBER).assertTextContains("")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("")

            onNodeWithTag(TAG_REMEMBER).performTextInput("Text_Remember2")
            onNodeWithTag(TAG_RETAINED_1).performTextInput("Text_Retained2")

            onNodeWithTag(TAG_REMEMBER).assertTextContains("Text_Remember2")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained2")

            onNodeWithText("Back").performClick()

            onNodeWithTag(TAG_REMEMBER).assertTextContains("")
            onNodeWithTag(TAG_RETAINED_1).assertTextContains("Text_Retained")
        }
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun incrementAcrossScreenTest() {
        val repository = Repository()
        var startScreenRinViewModel: RinViewModel? = null
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides object : LifecycleOwner {
                    override val lifecycle: Lifecycle = LifecycleRegistry(this)
                }) {
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
                                            LocalViewModelStoreOwner.current!!.viewModelStore.rinViewModel()
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
                                        Button(onClick = {
                                            navController.popBackStack()
                                        }) {
                                            Text("GoBack")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            onNodeWithText("Increment").performClick()
            onNodeWithText("Increment").performClick()
            onNodeWithText("Increment").performClick()

            onNodeWithText("GoNext").performClick()

            onNodeWithText("Next").assertIsDisplayed()
            onNodeWithText("Increment").performClick()

            onNodeWithText("GoBack").performClick()

            onNode(isRoot()).printToLog("Root")
            onNodeWithText("4").assertIsDisplayed()
            startScreenRinViewModel?.savedData?.forEach {
                println(it)
            }

            onNodeWithText("Increment").performClick()
            onNodeWithText("5").assertIsDisplayed()
        }
    }
}