package com.example.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.android.ui.theme.AppDimens

@Composable
fun ModernPlaceholder(
    @StringRes titleRes: Int,
    icon: ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.spaceLarge),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppDimens.cardElevation
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.spaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                AppDimens.spaceMedium,
                Alignment.CenterVertically
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimens.emptyStateIconContainerSize)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .padding(AppDimens.spaceLarge),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
