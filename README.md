# Rin (輪)
"Rin" means "circle" in Japanese. This library enhances Compose Multiplatform by enabling the use of `rememberRetained{}`, which is stored within ViewModel. It broadens the versatility of Compose, allowing it to be utilized in a wider array of contexts and scenarios.

## Motivation

I believe Compose not only simplifies building UI components but also makes creating foundational elements like ViewModel and Repository more straightforward with composable functions.

Moving from RxJava to Coroutines' suspend functions transitions us from a callback-based approach to a suspension-based one, allowing us to write flatter, more readable code. Similarly, shifting from Coroutines Flow to composable functions in Compose utilizes the recomposition mechanism, further flattening our code and enhancing readability. 
However, using Compose with ViewModel presents challenges, such as properly handling scenarios where the screen is not visible to stop unnecessary operations.


Initially, I integrated ViewModel with [Molecule](https://github.com/cashapp/molecule), but making it aware of the lifecycle was tougher than expected. 
Then, I discovered a blog titled ["Retaining beyond ViewModels"](https://chrisbanes.me/posts/retaining-beyond-viewmodels/) which is an insightful article. It discusses how [Circuit](https://github.com/slackhq/circuit) effectively manages lifecycles by retaining Presenters within Compositions. I found Circuit particularly beneficial for its use of Compose's lifecycle for state management and its support for `rememberRetained{}`, mirroring ViewModel's lifecycle. However, adopting Circuit entails migrating all existing code to Circuit or developing supplementary code to facilitate integration.

Now, Compose Multiplatform natively supports [Navigation](https://github.com/JetBrains/compose-multiplatform-core/blob/fcdc2410f3429cf758345f2ea82d286ae849aa8b/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/NavHost.kt) and [ViewModel](https://github.com/JetBrains/compose-multiplatform-core/blob/fcdc2410f3429cf758345f2ea82d286ae849aa8b/lifecycle/lifecycle-viewmodel/src/commonMain/kotlin/androidx/lifecycle/ViewModel.kt), enabling their default use.

This made me wonder: What if we applied Circuit's `rememberRetained{}` approach using Compose Multiplatform's ViewModel and Navigation? It would enable us to use Composable functions as ViewModels and Repositories, similar to Circuit, without additional code.

## Understanding Rin

Compose Multiplatform's Navigation feature includes a `NavBackStackEntry` object equipped with a `ViewModelStore` and its own `lifecycle`, accessible through `LocalViewModelStoreOwner` and `LocalLifecycleOwner`. Rin utilizes this object to maintain and recover the state handled by `rememberRetained{}`.

### Difference with ViewModel

The difference between `rememberRetained{}` and ViewModel is that `rememberRetained{}` clears the stored state when its composition is removed from the screen. In contrast, ViewModel retains the saved state even after its composition disappears from the screen, until the screen itself is closed.

### Difference with remember{}

The difference between `rememberRetained{}` and `remember{}` is that `rememberRetained{}` retains the state across configuration changes and keeps the data even when the screen moves to the back stack.

### Behavior Example

```kotlin
@Composable
fun ScreenA() {
  var isB by rememberRetained{ mutableStateOf(true) }
  rememberRetained{ "A" }
  if(isB) {
    rememberRetained{ "B" }
  } else {
    rememberRetained{ "C" }
  }
}
```

Using `remember{}`:

```
Switch isB
-> B will be removed. C will be saved

Move to ScreenB
-> **A, C will be removed**
```

Using ViewModel:

```
Switch isB
-> B **will not** be removed. C will be saved

Move to ScreenB
-> A, B, C will not be removed
```

Using `rememberRetained{}`:

```
Switch isB
-> B will be removed. C will be saved

Move to ScreenB
-> A, C will not be removed
```

You can check the [full test code](rin/src/androidInstrumentedTest/kotlin/io/github/takahirom/rin/RinBehaviorTest.kt) for more details.

## Credits

- [Circuit](https://slackhq.github.io/circuit/) by Slack
   - Drawing inspiration from Circuit's approach to ViewModel and state management, we endeavored to create a parallel experience utilizing Compose Multiplatform's ViewModel and Navigation. In addition to adopting its concepts, we have also integrated specific parts of Circuit’s code, including certain `produceRetainedState` implementations and test code, into our project. We extend our gratitude to the Circuit team for their pioneering work.

## Example

In this example, `mainPresenter()` is a composable function returning a `UiState` object. It uses `produceRetainedState` to keep the articles' state. The `repository.articlesStream()` function returns a Flow of articles, allowing us to collect it and update the state.

```kotlin
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
fun MainScreen(modifier: Modifier) {
   val events = remember{MutableSharedFlow<Event>()}
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
```

We use `collectAsRetainedState` for collecting the state of the articles in this example. The `articles()` function returns a list of articles.

```kotlin
class ArticleRepository {
   ...
   @Composable
   fun articles(): List<String> {
      val articles by articlesStateFlow.collectAsRetainedState()
      return articles
   }
}

@Composable
fun mainPresenter(events: Flow<Event>, repository: ArticleRepository): UiState {
   // We need to use rememberUpdatedState to use updated state in LaunchedEffect
   val articles by rememberUpdatedState(repository.articles())

   LaunchedEffect(events) {
      // ...
   }

   return UiState(articles)
}
```

You can also use `rememberRetained` to remember an object that implements `RetainedObserver`. This is useful for observing the lifecycle of the object.

```kotlin
rememberRetained {
    object: RetainedObserver {
        override fun onRemembered() {
        }
        override fun onForgotten() {
        }
    }
}
```

# download

It is still in a very early stage, but your feedback will help make this library stable.

```
rin = { module = "io.github.takahirom.rin:rin", version = "[use-latest-release]" }
```

```
implementation("io.github.takahirom.rin:rin:[use-latest-release]")
```

# The Project Using the Rin Library

* [conference-app-2024](https://github.com/DroidKaigi/conference-app-2024)

If you are using Rin, please create a pull request to add your project to this list. Having more users would allow us to dedicate more time to enhancing this library.


# LICENSE

```
Copyright 2024 takahirom
Copyright 2022 Slack Technologies, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```