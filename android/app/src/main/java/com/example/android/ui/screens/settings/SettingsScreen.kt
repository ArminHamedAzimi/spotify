package com.example.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.example.android.R
import com.example.android.ui.localization.AppLanguage
import com.example.android.ui.theme.AppDimens
import com.example.android.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = AppDimens.screenContentMaxWidth)
            .padding(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        Text(
            text = stringResource(R.string.appearance),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.theme_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = AppDimens.cardElevation
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.spaceSmall)
                    .selectableGroup()
            ) {
                ThemeOption(R.string.theme_system, ThemeMode.System, themeMode, onThemeModeChange)
                ThemeOption(R.string.theme_light, ThemeMode.Light, themeMode, onThemeModeChange)
                ThemeOption(R.string.theme_dark, ThemeMode.Dark, themeMode, onThemeModeChange)
            }
        }
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.language_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = AppDimens.cardElevation
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.spaceSmall)
                    .selectableGroup()
            ) {
                LanguageOption(
                    R.string.language_system,
                    AppLanguage.System,
                    appLanguage,
                    onLanguageChange
                )
                LanguageOption(
                    R.string.language_english,
                    AppLanguage.English,
                    appLanguage,
                    onLanguageChange
                )
                LanguageOption(
                    R.string.language_persian,
                    AppLanguage.Persian,
                    appLanguage,
                    onLanguageChange
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    @StringRes labelRes: Int,
    language: AppLanguage,
    selectedLanguage: AppLanguage,
    onSelected: (AppLanguage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.settingsItemMinHeight)
            .selectable(
                selected = selectedLanguage == language,
                onClick = { onSelected(language) },
                role = Role.RadioButton
            )
            .padding(horizontal = AppDimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedLanguage == language,
            onClick = { onSelected(language) }
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = AppDimens.spaceSmall)
        )
    }
}

@Composable
private fun ThemeOption(
    @StringRes labelRes: Int,
    mode: ThemeMode,
    selectedMode: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.settingsItemMinHeight)
            .selectable(
                selected = selectedMode == mode,
                onClick = { onSelected(mode) },
                role = Role.RadioButton
            )
            .padding(horizontal = AppDimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onSelected(mode) }
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = AppDimens.spaceSmall)
        )
    }
}
