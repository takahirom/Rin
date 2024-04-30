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

### Example

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

It will be available on Maven Central soon.

---

<!--

Work in progress

## how do i build it?

1. - [x] clone this repository ot just [use it as template](https://github.com/kotlin/multiplatform-library-template/generate)
1. - [x] edit library module name and include it in [`settings.gradle.kts`](settings.gradle.kts#l18)
1. - [x] Edit [`groupId` and `version`](convention-plugins/src/main/kotlin/module.publication.gradle.kts#L10-L11)
    1. If you need the Android support update namespace [there](library/build.gradle.kts#L38) too
    1. If you don't need an Android support delete the [`android` section](library/build.gradle.kts#L37-L43)
1. - [x] Edit [build targets you need](library/build.gradle.kts#L9-L21)

At this stage, you have everything set to work with Kotlin Multiplatform. The project should be buildable (but you might need to provide actual starting values for the platforms you need).

## How do I make it build on GitHub Actions?

To make it work on GitHub actions, you need to update the [`matrix` section in `gradle.yml`](.github/workflows/gradle.yml#L25-L34). If you didn't change platforms in `build.gradle.kts` you don't need to touch anything. But still read it to understand how it works.

Also, currently, it only runs tests, but you can change this behaviour as you wish by modifying `matrix` and the Gradle [build command](.github/workflows/gradle.yml#L52)

## How do I deploy it to Maven Central?

The most part of the job is already automated for you. However, deployment to Maven Central requires some manual work from your side. 

1. - [x] Create an account at [Sonatype issue tracker](https://issues.sonatype.org/secure/Signup!default.jspa)
1. - [x] [Create an issue](https://issues.sonatype.org/secure/CreateIssue.jspa?issuetype=21&pid=10134) to create new project for you
1. - [x] You will have to prove that you own your desired namespace
1. - [x] Create a GPG key with `gpg --gen-key`, use the same email address you used to sign up to the Sonatype Jira
1. - [x] Find your key id in the output of the previous command looking like `D89FAAEB4CECAFD199A2F5E612C6F735F7A9A519`
1. - [x] Upload your key to a keyserver, for example 
    ```bash
    gpg --send-keys --keyserver keyserver.ubuntu.com "<your key id>"
    ```
1. - [x] Now you should create secrets available to your GitHub Actions
    1. via `gh` command
    ```bash
    gh secret set OSSRH_GPG_SECRET_KEY -a actions --body "$(gpg --export-secret-key --armor "<your key id>")"
    gh secret set OSSRH_GPG_SECRET_KEY_ID -a actions --body "<your key id>"
    gh secret set OSSRH_GPG_SECRET_KEY_PASSWORD -a actions --body "<your key password>"
    gh secret set OSSRH_PASSWORD -a actions --body "<your sonatype account password>"
    gh secret set OSSRH_USERNAME -a actions --body "<your sonatype account username>"
    ```
    1. Or via the interface in `Settings` → `Secrets and Variables` → `Actions`, same variables as in 1.
1. - [x] Edit deployment pom parameters in [`module.publication.gradle.kts`](convention-plugins/src/main/kotlin/module.publication.gradle.kts#L25-L44)
1. - [x] Edit deploy targets in [`deploy.yml`](.github/workflows/deploy.yml#L23-L36)
1. - [ ] Call deployment manually when ready [in Actions](../../actions/workflows/deploy.yml) → `Run Workflow`
1. - [ ] When you see in your account on https://oss.sonatype.org that everything is fine, you can release your staging repositories and add target `releaseSonatypeStagingRepository` to `deploy.yml` [after this line](.github/workflows/deploy.yml#L60). This way artifacts will be published to central automatically when tests pass.

-->