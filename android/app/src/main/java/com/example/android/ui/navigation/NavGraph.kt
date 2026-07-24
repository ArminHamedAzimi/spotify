package com.example.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.android.ui.components.AppTopBar
import com.example.android.ui.components.MiniPlayer
import com.example.android.ui.screens.downloads.DownloadsScreen
import com.example.android.ui.screens.downloads.DownloadsViewModel
import com.example.android.ui.screens.home.HomeScreen
import com.example.android.ui.screens.home.HomeEvent
import com.example.android.ui.screens.home.HomeViewModel
import com.example.android.ui.screens.notifications.NotificationsScreen
import com.example.android.ui.screens.playlists.PlaylistsScreen
import com.example.android.ui.screens.playlists.PlaylistDetailScreen
import com.example.android.ui.screens.playlists.PlaylistsViewModel
import com.example.android.ui.screens.player.PlayerScreen
import com.example.android.ui.screens.profile.ProfileScreen
import com.example.android.ui.screens.profile.ProfileViewModel
import com.example.android.ui.screens.search.SearchScreen
import com.example.android.ui.screens.settings.SettingsScreen
import com.example.android.ui.theme.ThemeMode
import com.example.android.ui.theme.AppDimens
import com.example.android.ui.localization.AppLanguage
import com.example.android.data.remote.UserDto
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.playback.PlaybackViewModel
import com.example.android.ui.theme.PlayerVisuals
import androidx.paging.compose.collectAsLazyPagingItems

@Composable
fun SpotifyNavGraph(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val navController = rememberNavController()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val playbackViewModel: PlaybackViewModel = koinViewModel()
    val downloadsViewModel: DownloadsViewModel = koinViewModel()
    val playlistsViewModel: PlaylistsViewModel = koinViewModel()
    val profileState by profileViewModel.uiState
    val playbackState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val downloadsState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val pagedDownloads = downloadsViewModel.pagedSongs.collectAsLazyPagingItems()
    val playlistsState by playlistsViewModel.state.collectAsStateWithLifecycle()
    val pagedPlaylists = playlistsViewModel.playlists.collectAsLazyPagingItems()
    val pagedPlaylistSongs = playlistsViewModel.songs.collectAsLazyPagingItems()
    LaunchedEffect(profileState.user?.id) {
        val userId = profileState.user?.id
        playbackViewModel.setActiveUser(userId)
        downloadsViewModel.setActiveUser(userId)
        if (profileState.user != null) {
            homeViewModel.onEvent(HomeEvent.Refresh)
            playlistsViewModel.refresh()
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    val isMainDestination = bottomNavItems.any { it.route == currentRoute }
    val isPlaylistDetail = currentRoute == Screen.PlaylistDetail.route
    val showAppBottomBar = isMainDestination || isPlaylistDetail

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentRoute != Screen.Player.route && !isPlaylistDetail) {
                AppTopBar(
                    isMainDestination = isMainDestination,
                    titleRes = when (currentRoute) {
                        Screen.Notifications.route -> Screen.Notifications.titleRes
                        Screen.Settings.route -> Screen.Settings.titleRes
                        else -> null
                    },
                    onBackClick = { navController.popBackStack() },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showAppBottomBar,
                enter = fadeIn(
                    animationSpec = tween(PlayerVisuals.navigationAnimationDurationMillis)
                ),
                exit = fadeOut(
                    animationSpec = tween(PlayerVisuals.navigationAnimationDurationMillis)
                )
            ) {
                Column {
                    if (playbackState.hasMedia) {
                        MiniPlayer(
                            state = playbackState,
                            onOpenPlayer = { navController.navigate(Screen.Player.route) },
                            onTogglePlayPause = playbackViewModel::togglePlayPause
                        )
                    }
                    MelodifyBottomBar(
                        currentRoute = if (isPlaylistDetail) Screen.Playlists.route else currentRoute,
                        user = profileState.user,
                        onNavigate = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onSongClick = playbackViewModel::play
                )
            }
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    songs = pagedDownloads,
                    isPremium = profileState.user?.hasActivePremium == true,
                    onSongClick = { song ->
                        playbackViewModel.playFromDownloads(
                            song,
                            (0 until pagedDownloads.itemCount)
                                .mapNotNull { pagedDownloads[it]?.toPlayableSong() }
                        )
                    },
                    onRemoveSong = downloadsViewModel::removeDownload
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(pagedPlaylists) {
                    navController.navigate(Screen.PlaylistDetail.route(it))
                }
            }
            composable(Screen.PlaylistDetail.route) { entry ->
                val id = entry.arguments?.getString("playlistId") ?: return@composable
                LaunchedEffect(id) { playlistsViewModel.load(id) }
                PlaylistDetailScreen(
                    playlistsState,
                    pagedPlaylistSongs,
                    playbackViewModel::playFromPlaylist,
                    playbackViewModel::startPlaylist,
                    playlistsViewModel::removeSong,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(profileViewModel = profileViewModel)
            }
            composable(Screen.Notifications.route) { NotificationsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    appLanguage = appLanguage,
                    onLanguageChange = onLanguageChange
                )
            }
            composable(
                route = Screen.Player.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(
                            PlayerVisuals.navigationAnimationDurationMillis
                        )
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(
                            PlayerVisuals.navigationAnimationDurationMillis
                        )
                    )
                }
            ) {
                PlayerScreen(
                    state = playbackState,
                    onTogglePlayPause = playbackViewModel::togglePlayPause,
                    onSeek = playbackViewModel::seekTo,
                    onPlaybackSpeedChange = playbackViewModel::setPlaybackSpeed,
                    onSleepTimerChange = playbackViewModel::setSleepTimer,
                    playlists = pagedPlaylists,
                    addedMemberships = playlistsState.addedMemberships,
                    onAddToPlaylist = playlistsViewModel::addSong,
                    onCreatePlaylist = playlistsViewModel::create,
                    onNext = playbackViewModel::next,
                    onPrevious = playbackViewModel::previous,
                    onShuffleChange = playbackViewModel::setShuffle,
                    isDownloaded = downloadsState.songs.any {
                        it.id == playbackState.mediaId
                    },
                    isDownloading = playbackState.mediaId in downloadsState.activeDownloadIds,
                    downloadMessageRes = downloadsState.messageRes,
                    onDownload = {
                        downloadsViewModel.download(
                            playbackState,
                            profileState.user?.id,
                            profileState.user?.hasActivePremium == true
                        )
                    },
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun MelodifyBottomBar(
    currentRoute: String,
    user: UserDto?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = com.example.android.ui.theme.AppDimens.bottomBarElevation
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        onClick = { onNavigate(screen) },
                        role = Role.Tab
                    )
                    .padding(vertical = AppDimens.bottomBarItemVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(AppDimens.bottomBarIndicatorWidth)
                        .height(AppDimens.bottomBarIndicatorHeight)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BottomBarIcon(screen = screen, user = user, selected = selected)
                }
                Text(
                    text = if (screen == Screen.Profile && user != null) {
                        user.name
                    } else {
                        stringResource(screen.titleRes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BottomBarIcon(screen: Screen, user: UserDto?, selected: Boolean) {
    Box(
        modifier = Modifier.size(AppDimens.bottomBarIconContainerSize),
        contentAlignment = Alignment.Center
    ) {
        if (screen == Screen.Profile && user?.avatarUrl != null) {
            BottomBarAvatar(user)
        } else {
            Icon(
                imageVector = screen.icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.bottomBarIconSize),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun BottomBarAvatar(user: UserDto) {
    Box(
        modifier = Modifier.size(AppDimens.bottomBarAvatarSize),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = user.avatarUrl,
            contentDescription = stringResource(com.example.android.R.string.profile_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(AppDimens.bottomBarAvatarSize)
                .clip(CircleShape)
        ) {
            if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                Icon(
                    imageVector = Screen.Profile.icon,
                    contentDescription = null
                )
            }
        }
        if (user.hasActivePremium) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(AppDimens.premiumBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(
                        width = AppDimens.borderWidth,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        }
    }
}
