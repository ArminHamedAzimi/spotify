package com.example.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.android.R
import com.example.android.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    isMainDestination: Boolean,
    @StringRes titleRes: Int?,
    onBackClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    if (!isMainDestination && titleRes != null) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                TonalIconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                        modifier = Modifier.size(AppDimens.actionIconSize)
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        return
    }

    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = stringResource(R.string.app_logo),
                    modifier = Modifier
                        .size(AppDimens.topBarLogoSize)
                        .clip(MaterialTheme.shapes.small)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceLarge),
                modifier = Modifier.padding(end = AppDimens.spaceMedium)
            ) {
                TonalIconButton(onClick = onNotificationsClick) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.notifications),
                        modifier = Modifier.size(AppDimens.actionIconSize)
                    )
                }
                TonalIconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(AppDimens.actionIconSize)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun TonalIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(AppDimens.actionButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        content = content
    )
}
