---
title: "Sing-box Subscription Fetch: Missing Timeout and Cancel"
aliases: [singbox-subscription-timeout, subscription-hanging-ui, dead-subscription-cancel]
tags: [singbox, android, kotlin, ux, subscriptions, coroutines]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Sing-box Subscription Fetch: Missing Timeout and Cancel

Subscription fetch operations in the sing-box engine can hang the UI indefinitely when the remote server is unreachable ("dead subscription"). Without a timeout on the HTTP request and a cancel mechanism in the ViewModel, the user has no way to abort the operation — the UI remains blocked until the OS-level TCP timeout triggers (often 75+ seconds). Additionally, the subscription URL input was found to not accept URLs correctly in v0.2.8/v0.2.9, compounding the problem.

## Key Points

- Subscription refresh without explicit timeout blocks UI until OS TCP timeout (~75s on Android)
- No cancel mechanism means user cannot abort a stuck refresh — must kill and reopen the app
- OkHttp calls must use `.callTimeout(Duration)` or `.connectTimeout()` + `.readTimeout()` at OkHttpClient level
- ViewModel must expose a cancel function and store the fetch `Job?` analogous to `pingJob` in ping operations
- Confirmed issue in v0.2.8/v0.2.9: subscription URL input not accepted (separate from timeout)

## Details

### The Hanging UI Pattern

When a subscription URL points to a server that accepts TCP connections but never responds (e.g., a VPS that is up but the subscription service is down), the HTTP client will wait for the read timeout. Android's default OkHttp client configuration has a 10-second connect timeout but can have very long (or infinite) read timeouts depending on construction.

If the subscription fetch is launched in `viewModelScope.launch { ... }` without storing the `Job`, the user sees an indefinite loading spinner. The "Update" button cannot be pressed again (it is disabled while loading), and there is no "Cancel" button. The only escape is to leave the screen — but if the ViewModel is not cleared (e.g., on config change), the job continues running in the background.

### Fix Pattern

```kotlin
class SingboxSubscriptionViewModel(
    private val fetcher: SingboxSubscriptionFetcher
) : ViewModel() {
    private var fetchJob: Job? = null
    val isFetching = MutableStateFlow(false)
    val fetchError = MutableStateFlow<String?>(null)

    fun refresh(url: String) {
        fetchJob?.cancel()
        isFetching.value = true
        fetchError.value = null
        fetchJob = viewModelScope.launch {
            try {
                fetcher.fetch(url)  // OkHttp with callTimeout(30, SECONDS)
            } catch (e: CancellationException) {
                throw e  // must propagate
            } catch (e: Exception) {
                fetchError.value = e.message ?: "Unknown error"
            } finally {
                isFetching.value = false
            }
        }
    }

    fun cancelRefresh() {
        fetchJob?.cancel()
        isFetching.value = false
    }
}
```

OkHttpClient construction in the subscription module should specify explicit timeouts:

```kotlin
OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
```

### Relationship to URL Input Bug

In v0.2.8/v0.2.9, subscription URLs were not being accepted by the input field. This is a separate UI bug (likely input validation rejecting valid URL schemes or characters), but it compounds the timeout issue: if a URL cannot even be submitted, there is nothing to cancel. Both bugs should be fixed in the same T5/T6 pass.

### Karing Reference

The Karing reference implementation (`<local-reference>/karing`) provides a subscription input UI that accepts URLs, files, and clipboard content. It may be used as a reference for the T8 subscription input redesign.

## Related Concepts

- [[concepts/pingJob-viewmodel-cancellation]] - Identical pattern: store Job? in ViewModel, expose cancel(); confirmed correct by code review
- [[concepts/singbox-subscription-architecture]] - Subscription fetch pipeline: app/ process only, OkHttp/Gson constructed inline
- [[concepts/subagent-code-review-false-positives]] - Exception.message can be null — always use `?: "Unknown error"` fallback in error handlers

## Sources

- [[daily/2026-05-26.md]] - Session 19:44: "мёртвые подписки зависают UI без cancel" identified as T5-T6 bugs in v0.2.8/v0.2.9; "singbox не принимает URL подписок" identified as separate bug; fix priority: T5-T6 after T1-T4 (dns outbound, splithttp, Connected race, UI buttons); Karing reference suggested for subscription input T8
