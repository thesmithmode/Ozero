package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.util.Log
import okhttp3.OkHttpClient
import ru.ozero.app.BuildConfig
import ru.ozero.app.selfupdate.ApkDownloader
import ru.ozero.app.selfupdate.ApkUpdateVerifier
import ru.ozero.app.selfupdate.GithubPinnedClient
import ru.ozero.app.selfupdate.GithubReleaseFetcher
import ru.ozero.app.selfupdate.SilentPackageInstaller
import ru.ozero.app.selfupdate.UpdateCoordinator
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SelfUpdateModule {

    @Provides
    @Singleton
    @Named("github-api")
    fun provideGithubApiClient(): OkHttpClient = GithubPinnedClient.create()

    @Provides
    @Singleton
    @Named("github-cdn")
    fun provideGithubCdnClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideReleaseFetcher(@Named("github-api") client: OkHttpClient): GithubReleaseFetcher =
        GithubReleaseFetcher(
            owner = BuildConfig.UPDATE_GITHUB_OWNER,
            repo = BuildConfig.UPDATE_GITHUB_REPO,
            client = client,
        )

    @Provides
    @Singleton
    fun provideApkDownloader(@Named("github-cdn") client: OkHttpClient): ApkDownloader =
        ApkDownloader(client = client)

    @Provides
    @Singleton
    fun provideApkVerifier(): ApkUpdateVerifier =
        runCatching {
            ApkUpdateVerifier(publicKey = decodeHex(BuildConfig.UPDATE_PUBLIC_KEY_HEX))
        }.getOrElse { throwable ->
            Log.e(TAG, "Invalid UPDATE_PUBLIC_KEY_HEX, self-update verify disabled", throwable)
            ApkUpdateVerifier(publicKey = ByteArray(32))
        }

    @Provides
    @Singleton
    fun provideSilentInstaller(@ApplicationContext ctx: Context): SilentPackageInstaller =
        SilentPackageInstaller(ctx)

    @Provides
    @Singleton
    fun provideUpdateCoordinator(
        @ApplicationContext ctx: Context,
        fetcher: GithubReleaseFetcher,
        downloader: ApkDownloader,
        verifier: ApkUpdateVerifier,
        installer: SilentPackageInstaller,
    ): UpdateCoordinator = UpdateCoordinator(
        fetcher = fetcher,
        downloader = downloader,
        verifier = verifier,
        installer = installer,
        currentVersion = currentVersionName(ctx),
        currentVersionCode = currentVersionCode(ctx),
        cacheDir = ctx.cacheDir,
    )

    private fun currentVersionName(ctx: Context): String = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        info.versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")

    private fun currentVersionCode(ctx: Context): Long = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    private fun decodeHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex длина должна быть чётной" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) {
                "hex содержит не-hex символы на позиции ${i * 2}"
            }
            ((hi shl 4) or lo).toByte()
        }
    }

    private const val TAG = "SelfUpdateModule"
}
