package com.example.android.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.R
import com.example.android.data.remote.UserDto
import com.example.android.ui.theme.AppDimens
import com.example.android.ui.theme.AppMotion

private enum class AuthMode { Login, Register }

@Composable
fun ProfileScreen(profileViewModel: ProfileViewModel = viewModel()) {
    val state by profileViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.profile_saved)
    val subscriptionMessage = stringResource(R.string.subscription_updated)

    LaunchedEffect(state.saveSuccessCount) {
        if (state.saveSuccessCount > 0) {
            snackbarHostState.showSnackbar(savedMessage)
        }
    }
    LaunchedEffect(state.subscriptionSuccessCount) {
        if (state.subscriptionSuccessCount > 0) {
            snackbarHostState.showSnackbar(subscriptionMessage)
        }
    }

    if (state.isLoading && state.user == null && state.errorRes == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.user?.let { user ->
            SignedInProfile(
                user = user,
                isLoading = state.isLoading,
                isUploadingAvatar = state.isUploadingAvatar,
                isUpdatingSubscription = state.isUpdatingSubscription,
                saveSuccessCount = state.saveSuccessCount,
                errorRes = state.errorRes,
                onAvatarSelected = profileViewModel::uploadAvatar,
                onAddSubscription = profileViewModel::addSubscription,
                onSave = profileViewModel::saveProfile,
                onLogout = profileViewModel::logout
            )
        } ?: AuthenticationContent(
            isLoading = state.isLoading,
            errorRes = state.errorRes,
            onLogin = profileViewModel::login,
            onRegister = profileViewModel::register
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(AppDimens.spaceMedium)
                .widthIn(max = AppDimens.formContentMaxWidth)
        ) { snackbarData ->
            Snackbar(
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppDimens.actionIconSize)
                    )
                    Text(
                        text = snackbarData.visuals.message,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInProfile(
    user: UserDto,
    isLoading: Boolean,
    isUploadingAvatar: Boolean,
    isUpdatingSubscription: Boolean,
    saveSuccessCount: Int,
    errorRes: Int?,
    onAvatarSelected: (Uri) -> Unit,
    onAddSubscription: (Int) -> Unit,
    onSave: (String) -> Unit,
    onLogout: () -> Unit
) {
    var name by remember(user.id, user.name) { mutableStateOf(user.name) }
    var selectedAvatar by remember { mutableStateOf<Uri?>(null) }
    var showPremiumPlans by rememberSaveable { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedAvatar = it
            onAvatarSelected(it)
        }
    }
    LaunchedEffect(saveSuccessCount) {
        if (saveSuccessCount > 0) selectedAvatar = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(AppDimens.avatarUploadRingSize),
            contentAlignment = Alignment.Center
        ) {
            ProfileAvatar(model = selectedAvatar ?: user.avatarUrl)
            if (isUploadingAvatar) {
                AvatarUploadRing()
            }
            if (user.hasActivePremium) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(AppDimens.premiumHeroBadgeSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.premium_member),
                        modifier = Modifier.padding(AppDimens.spaceSmall),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.change_avatar),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(AppDimens.spaceMedium))
        Text(text = user.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.spaceSmall))
        PremiumBadge(user.hasActivePremium)
        Spacer(Modifier.height(AppDimens.spaceMedium))
        FilledTonalButton(
            onClick = { showPremiumPlans = true },
            enabled = !isUpdatingSubscription,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            if (isUpdatingSubscription) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.actionIconSize),
                    strokeWidth = AppDimens.borderWidth
                )
            } else {
                Icon(
                    Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.actionIconSize)
                )
                Text(
                    text = stringResource(
                        if (user.hasActivePremium) {
                            R.string.extend_subscription
                        } else {
                            R.string.try_premium
                        }
                    ),
                    modifier = Modifier.padding(start = AppDimens.spaceSmall)
                )
            }
        }
        Spacer(Modifier.height(AppDimens.spaceLarge))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = AppDimens.formContentMaxWidth),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(AppDimens.cardElevation)
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
            ) {
                Text(
                    text = stringResource(R.string.edit_profile),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user.email,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.email)) },
                    supportingText = { Text(stringResource(R.string.email_read_only)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorRes?.let { ErrorText(it) }
                Button(
                    onClick = { onSave(name) },
                    enabled = name.isNotBlank() && !isLoading && !isUploadingAvatar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppDimens.actionIconSize),
                            strokeWidth = AppDimens.borderWidth
                        )
                    } else {
                        Text(stringResource(R.string.save_changes))
                    }
                }
                TextButton(
                    onClick = onLogout,
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.log_out))
                }
            }
        }
    }

    if (showPremiumPlans) {
        PremiumPlanSheet(
            onDismiss = { showPremiumPlans = false },
            onPlanSelected = { months ->
                showPremiumPlans = false
                onAddSubscription(months)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumPlanSheet(
    onDismiss: () -> Unit,
    onPlanSelected: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.spaceLarge,
                    end = AppDimens.spaceLarge,
                    bottom = AppDimens.spaceExtraLarge
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
        ) {
            Icon(
                Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimens.emptyStateIconSize)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.choose_premium_plan),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = stringResource(R.string.premium_plan_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            premiumPlans.forEach { plan ->
                FilledTonalButton(
                    onClick = { onPlanSelected(plan.months) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Text(stringResource(plan.labelRes))
                }
            }
        }
    }
}

private data class PremiumPlan(val months: Int, val labelRes: Int)

private val premiumPlans = listOf(
    PremiumPlan(months = 1, labelRes = R.string.premium_one_month),
    PremiumPlan(months = 3, labelRes = R.string.premium_three_months),
    PremiumPlan(months = 6, labelRes = R.string.premium_six_months),
    PremiumPlan(months = 12, labelRes = R.string.premium_twelve_months)
)

@Composable
private fun AvatarUploadRing() {
    val transition = rememberInfiniteTransition(label = "avatar-upload")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = AppMotion.fullRotationDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AppMotion.avatarRingDurationMillis,
                easing = LinearEasing
            )
        ),
        label = "avatar-upload-rotation"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(AppDimens.avatarUploadRingSize)
            .rotate(rotation)
    ) {
        drawArc(
            color = ringColor,
            startAngle = AppMotion.avatarRingStartDegrees,
            sweepAngle = AppMotion.avatarRingSweepDegrees,
            useCenter = false,
            style = Stroke(
                width = AppDimens.avatarUploadRingStroke.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun AuthenticationContent(
    isLoading: Boolean,
    errorRes: Int?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showRequiredError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProfileAvatar(model = null)
        Spacer(Modifier.height(AppDimens.spaceLarge))
        Text(
            text = stringResource(
                if (mode == AuthMode.Login) R.string.welcome_back else R.string.join_spotify
            ),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.auth_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.spaceLarge))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = AppDimens.formContentMaxWidth),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(AppDimens.cardElevation)
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
                ) {
                    AuthModeButton(
                        selected = mode == AuthMode.Login,
                        textRes = R.string.login,
                        modifier = Modifier.weight(1f)
                    ) { mode = AuthMode.Login }
                    AuthModeButton(
                        selected = mode == AuthMode.Register,
                        textRes = R.string.register,
                        modifier = Modifier.weight(1f)
                    ) { mode = AuthMode.Register }
                }
                if (mode == AuthMode.Register) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showRequiredError) ErrorText(R.string.required_fields)
                errorRes?.let { ErrorText(it) }
                Button(
                    onClick = {
                        val valid = email.isNotBlank() && password.isNotBlank() &&
                            (mode == AuthMode.Login || name.isNotBlank())
                        showRequiredError = !valid
                        if (valid) {
                            if (mode == AuthMode.Login) onLogin(email, password)
                            else onRegister(name, email, password)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppDimens.actionIconSize),
                            strokeWidth = AppDimens.borderWidth
                        )
                    } else {
                        Text(stringResource(if (mode == AuthMode.Login) R.string.login else R.string.register))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModeButton(
    selected: Boolean,
    textRes: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) {
            Text(stringResource(textRes))
        }
    } else {
        TextButton(onClick = onClick, modifier = modifier) {
            Text(stringResource(textRes))
        }
    }
}

@Composable
private fun ProfileAvatar(model: Any?) {
    Surface(
        modifier = Modifier.size(AppDimens.profileHeroAvatarSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (model == null) {
            Icon(
                Icons.Filled.Person,
                contentDescription = stringResource(R.string.profile_avatar),
                modifier = Modifier.padding(AppDimens.spaceLarge),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = stringResource(R.string.profile_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(CircleShape)
            ) {
                if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(AppDimens.spaceLarge),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumBadge(isPremium: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isPremium) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.spaceMedium,
                vertical = AppDimens.spaceSmall
            ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceExtraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPremium) {
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.actionIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(
                    if (isPremium) R.string.premium_member else R.string.free_member
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ErrorText(errorRes: Int) {
    Text(
        text = stringResource(errorRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
