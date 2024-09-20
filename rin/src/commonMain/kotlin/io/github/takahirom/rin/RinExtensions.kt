// Copyright (C) 2024 takahirom
// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package io.github.takahirom.rin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ProduceRetainedStateScopeImpl<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext,
) : ProduceStateScope<T>, MutableState<T> by state {

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> {}
        } finally {
            onDispose()
        }
    }
}

@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(Unit) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}

@Composable
public fun <T : R, R> Flow<T>.collectAsRetainedState(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
): State<R> =
    produceRetainedState(initial, this, context) {
        if (context == EmptyCoroutineContext) {
            collect { value = it }
        } else withContext(context) { collect { value = it } }
    }

@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(key1, key2) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}

@Composable
public fun <T> StateFlow<T>.collectAsRetainedState(
    context: CoroutineContext = EmptyCoroutineContext
): State<T> = collectAsRetainedState(value, context)
