package ru.ozero.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.ozero.app.R
import ru.ozero.app.ui.settings.LocaleApplier
import ru.ozero.enginescore.settings.AppMode

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentLocale by viewModel.currentLocale.collectAsStateWithLifecycle()
    val currentAppMode by viewModel.currentAppMode.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }
    OnboardingContent(
        pageIndex = state.pageIndex,
        currentLocaleTag = currentLocale,
        currentAppMode = currentAppMode,
        onLocaleSelect = viewModel::onLocaleSelect,
        onAppModeSelect = viewModel::onAppModeSelect,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        onFinish = viewModel::onFinish,
    )
}

@Composable
fun OnboardingContent(
    pageIndex: Int,
    currentLocaleTag: String?,
    currentAppMode: AppMode,
    onLocaleSelect: (String?) -> Unit,
    onAppModeSelect: (AppMode) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val totalPages = OnboardingViewModel.TOTAL_PAGES
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pageIndex) {
        if (pagerState.currentPage != pageIndex) {
            pagerState.animateScrollToPage(pageIndex)
        }
    }
    Scaffold(modifier = Modifier.testTag("onboarding")) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> LanguageStep(
                        currentTag = currentLocaleTag,
                        onSelect = onLocaleSelect,
                    )
                    1 -> StaticPage(R.string.onboarding_title_1, R.string.onboarding_body_1)
                    2 -> StaticPage(R.string.onboarding_title_2, R.string.onboarding_body_2)
                    3 -> StaticPage(R.string.onboarding_title_3, R.string.onboarding_body_3)
                    4 -> ModePickStep(
                        currentMode = currentAppMode,
                        onSelect = onAppModeSelect,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("onboarding_skip"),
                ) { Text(stringResource(R.string.onboarding_skip)) }

                if (pageIndex < totalPages - 1) {
                    Button(
                        onClick = {
                            onNext()
                            scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                        },
                        modifier = Modifier.testTag("onboarding_next"),
                    ) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.testTag("onboarding_finish"),
                    ) { Text(stringResource(R.string.onboarding_finish)) }
                }
            }
        }
    }
}

@Composable
private fun ModePickStep(
    currentMode: AppMode,
    onSelect: (AppMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.onboarding_mode_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_mode_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = currentMode == AppMode.SIMPLE,
                    onClick = { onSelect(AppMode.SIMPLE) },
                    role = Role.RadioButton,
                )
                .padding(vertical = 8.dp)
                .testTag("onboarding_mode_simple"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = currentMode == AppMode.SIMPLE,
                onClick = { onSelect(AppMode.SIMPLE) },
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_app_mode_simple),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_app_mode_simple_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = currentMode == AppMode.EXPERT,
                    onClick = { onSelect(AppMode.EXPERT) },
                    role = Role.RadioButton,
                )
                .padding(vertical = 8.dp)
                .testTag("onboarding_mode_expert"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = currentMode == AppMode.EXPERT,
                onClick = { onSelect(AppMode.EXPERT) },
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_app_mode_expert),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_app_mode_expert_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LanguageStep(
    currentTag: String?,
    onSelect: (String?) -> Unit,
) {
    val allLocales = remember {
        listOf(
            null to R.string.settings_language_system,
            "ru" to R.string.settings_language_ru,
            "en" to R.string.settings_language_en,
            "zh-CN" to R.string.settings_language_zh_cn,
            "es" to R.string.settings_language_es,
            "ar" to R.string.settings_language_ar,
            "fr" to R.string.settings_language_fr,
            "hi" to R.string.settings_language_hi,
            "pt" to R.string.settings_language_pt,
            "de" to R.string.settings_language_de,
            "ja" to R.string.settings_language_ja,
        )
    }
    val supported = LocaleApplier.SUPPORTED_TAGS.toSet()
    val locales = allLocales.filter { (tag, _) -> (tag ?: "") in supported }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_language_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        locales.forEach { (tag, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = currentTag == tag,
                        onClick = { onSelect(tag) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp)
                    .testTag("onboarding_locale_${tag ?: "system"}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = currentTag == tag,
                    onClick = { onSelect(tag) },
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun StaticPage(titleRes: Int, bodyRes: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
