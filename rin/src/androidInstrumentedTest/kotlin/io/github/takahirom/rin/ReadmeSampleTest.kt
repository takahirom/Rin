// Copyright (C) 2024 takahirom
// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class ReadmeSampleTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule =
        RuleChain.emptyRuleChain().detectLeaksAfterTestSuccessWrapping(tag = "ActivitiesDestroyed") {
            around(composeTestRule)
        }

    private val scenario: ActivityScenario<ComponentActivity>
        get() = composeTestRule.activityRule.scenario

    class ArticleRepository {
        val articlesStateFlow = MutableStateFlow(
            listOf(
                "article1",
                "article2",
                "article3"
            )
        )

        fun articlesStream(): StateFlow<List<String>> {
            return articlesStateFlow
        }

        @Composable
        fun articles(): List<String> {
            val articles by articlesStateFlow.collectAsRetainedState()
            return articles
        }
    }

    enum class Event {
    }

    class UiState(val articles: List<String>)

    @Composable
    fun articleRepository(): ArticleRepository {
        // Please use DI library
        return rememberRetained { ArticleRepository() }
    }

    @Composable
    fun mainPresenter(events: Flow<Event>, repository: ArticleRepository): UiState {
        val articles by produceRetainedState<List<String>>(listOf()) {
            repository
                .articlesStream()
                .collect {
                    value = it
                }
        }
        LaunchedEffect(events) {
            // ...
        }

        return UiState(articles)
    }


    @Composable
    fun mainPresenter2(events: Flow<Event>, repository: ArticleRepository): UiState {
        val articles by rememberUpdatedState(repository.articles())

        LaunchedEffect(events) {
            // ...
        }

        return UiState(articles)
    }

    @Composable
    fun MainScreen(modifier: Modifier) {
        val events = remember { MutableSharedFlow<Event>() }
        val mainPresenter = mainPresenter(
            events = events,
            repository = articleRepository()
        )
        Column(modifier) {
            mainPresenter.articles.forEach {
                Text(it)
            }
        }
    }

    @Test
    fun readmeTest() {
        val content = @Composable {
            MainScreen(Modifier)
        }
        setActivityContent(content)

        composeTestRule.onNodeWithText("article1").assertIsDisplayed()
    }

    @Test
    fun readmeTest2() {
        val content = @Composable {
            val events = remember { MutableSharedFlow<Event>() }
            val mainPresenter = mainPresenter2(
                events = events,
                repository = articleRepository()
            )
            Column {
                mainPresenter.articles.forEach {
                    Text(it)
                }
            }
        }
        setActivityContent(content)

        composeTestRule.onNodeWithText("article1").assertIsDisplayed()
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