package com.example.android.di

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.example.android.BuildConfig
import com.example.android.data.ProfileRepository
import com.example.android.data.home.HomeRepositoryImpl
import com.example.android.data.remote.SpotifyApi
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.GetRecentSongsUseCase
import com.example.android.domain.home.HomeRepository
import com.example.android.ui.screens.home.HomeViewModel
import com.example.android.ui.screens.profile.ProfileViewModel
import com.example.android.playback.PlaybackViewModel
import com.example.android.data.downloads.DownloadRepository
import com.example.android.ui.screens.downloads.DownloadsViewModel
import com.example.android.data.playlists.PlaylistRepository
import com.example.android.ui.screens.playlists.PlaylistsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {
    single { TokenStore(androidContext()) }
    single {
        OkHttpClient.Builder()
            .addInterceptor(
                ChuckerInterceptor.Builder(androidContext())
                    .redactHeaders("Authorization")
                    .build()
            )
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApi::class.java)
    }
    single { ProfileRepository(androidContext(), get(), get()) }
    single { DownloadRepository(androidContext()) }
    single { PlaylistRepository(get(), get()) }
    single<HomeRepository> { HomeRepositoryImpl(get(), get()) }
    factory { GetRecentSongsUseCase(get()) }
    viewModel { ProfileViewModel(androidContext() as android.app.Application, get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { PlaybackViewModel(androidContext() as android.app.Application, get(), get()) }
    viewModel { DownloadsViewModel(get()) }
    viewModel { PlaylistsViewModel(get()) }
}
