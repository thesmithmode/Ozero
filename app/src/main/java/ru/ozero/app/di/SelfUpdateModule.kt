package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
        ApkUpdateVerifier(publicKey = decodeHex(BuildConfig.UPDATE_PUBLIC_KEY_HEX))

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
            ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }
}
