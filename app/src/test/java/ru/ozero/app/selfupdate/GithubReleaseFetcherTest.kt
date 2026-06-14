package ru.ozero.app.selfupdate

import org.junit.jupiter.api.Test
import ru.ozero.app.BuildConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleaseFetcherTest {

    private val fetcher = GithubReleaseFetcher(
        owner = BuildConfig.UPDATE_GITHUB_OWNER,
        repo = BuildConfig.UPDATE_GITHUB_REPO,
    )

    @Test
    fun parsesValidReleaseJson() {
        val json = """
            {
              "tag_name": "v0.2.0",
              "prerelease": false,
              "published_at": "2026-05-01T10:00:00Z",
              "assets": [
                {"name": "ozero-0.2.0.apk", "browser_download_url": "https://example.com/a.apk"},
                {"name": "ozero-0.2.0.apk.sig", "browser_download_url": "https://example.com/a.apk.sig"}
              ]
            }
        """.trimIndent()
        val r = fetcher.parse(json)
        assertNotNull(r)
        assertEquals("v0.2.0", r.tag)
        assertEquals("https://example.com/a.apk", r.apkUrl)
        assertEquals("https://example.com/a.apk.sig", r.sigUrl)
        assertEquals(false, r.isPrerelease)
    }

    @Test
    fun parsesPositiveVersionCodeField() {
        val json = """
            {"tag_name":"v1.0.0","version_code":42,
             "assets":[
               {"name":"o.apk","browser_download_url":"https://example.com/o.apk"},
               {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
             ]}
        """.trimIndent()

        val r = fetcher.parse(json)

        assertNotNull(r)
        assertEquals(42L, r.versionCode)
    }

    @Test
    fun parsesVersionCodeFromBodyWhenFieldIsMissingOrZero() {
        val json = """
            {"tag_name":"v1.0.0","version_code":0,"body":"notes\nversion_code: 77\nend",
             "assets":[
               {"name":"o.apk","browser_download_url":"https://example.com/o.apk"},
               {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
             ]}
        """.trimIndent()

        val r = fetcher.parse(json)

        assertNotNull(r)
        assertEquals(77L, r.versionCode)
    }

    @Test
    fun parsesPrereleaseFlag() {
        val json = """
            {"tag_name":"v0.3.0-rc1","prerelease":true,
             "assets":[
               {"name":"o.apk","browser_download_url":"https://example.com/o.apk"},
               {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
             ]}
        """.trimIndent()
        val r = fetcher.parse(json)
        assertNotNull(r)
        assertTrue(r.isPrerelease)
    }

    @Test
    fun rejectsMissingApk() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
            ]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMissingSignature() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o.apk","browser_download_url":"https://example.com/o.apk"}
            ]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMultipleApks() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o-arm64.apk","browser_download_url":"https://e.com/a1.apk"},
              {"name":"o-x86.apk","browser_download_url":"https://e.com/a2.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s.sig"}
            ]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMultipleSignatures() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o.apk","browser_download_url":"https://e.com/a.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s1.sig"},
              {"name":"o2.apk.sig","browser_download_url":"https://e.com/s2.sig"}
            ]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsNonHttpsDownloadUrls() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o.apk","browser_download_url":"http://e.com/a.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s.sig"}
            ]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMissingAssetsArray() {
        val json = """{"tag_name":"v1.0.0"}"""
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMissingTag() {
        val json = """{"assets":[]}"""
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(fetcher.parse("not-json"))
    }

    @Test
    fun ignoresUnrelatedAssets() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"checksums.txt","browser_download_url":"https://e.com/x.txt"},
              {"name":"o.apk","browser_download_url":"https://e.com/a.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s.sig"},
              {"name":"o.aab","browser_download_url":"https://e.com/y.aab"}
            ]}
        """.trimIndent()
        val r = fetcher.parse(json)
        assertNotNull(r)
        assertEquals("https://e.com/a.apk", r.apkUrl)
        assertEquals("https://e.com/s.sig", r.sigUrl)
    }

    @Test
    fun rejectsBlankTagName() {
        val json = """
            {"tag_name":"   ","assets":[
              {"name":"o.apk","browser_download_url":"https://e.com/a.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s.sig"}
            ]}
        """.trimIndent()

        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsWhenOnlySignatureLooksLikeApkSuffix() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o.apk.sig","browser_download_url":"https://e.com/o.apk.sig"},
              {"name":"o.txt","browser_download_url":"https://e.com/o.txt"}
            ]}
        """.trimIndent()

        assertNull(fetcher.parse(json))
    }

    @Test
    fun ignoresAssetsWithoutHttpsBeforeCountingDuplicates() {
        val json = """
            {"tag_name":"v1.0.0","assets":[
              {"name":"o-arm64.apk","browser_download_url":"http://e.com/a1.apk"},
              {"name":"o.apk","browser_download_url":"https://e.com/a.apk"},
              {"name":"o.apk.sig","browser_download_url":"https://e.com/s.sig"},
              {"name":"o2.apk.sig","browser_download_url":"ftp://e.com/s2.sig"}
            ]}
        """.trimIndent()

        val r = fetcher.parse(json)

        assertNotNull(r)
        assertEquals("https://e.com/a.apk", r.apkUrl)
        assertEquals("https://e.com/s.sig", r.sigUrl)
    }

    @Test
    fun versionCodeFallsBackToZeroForMissingOrInvalidBodyMarker() {
        val json = """
            {"tag_name":"v1.0.0","body":"version_code: not-a-number",
             "assets":[
               {"name":"o.apk","browser_download_url":"https://example.com/o.apk"},
               {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
             ]}
        """.trimIndent()

        val r = fetcher.parse(json)

        assertNotNull(r)
        assertEquals(0L, r.versionCode)
    }

    @Test
    fun positiveVersionCodeFieldWinsOverBodyMarker() {
        val json = """
            {"tag_name":"v1.0.0","version_code":42,"body":"version_code: 77",
             "assets":[
               {"name":"o.apk","browser_download_url":"https://example.com/o.apk"},
               {"name":"o.apk.sig","browser_download_url":"https://example.com/o.apk.sig"}
             ]}
        """.trimIndent()

        val r = fetcher.parse(json)

        assertNotNull(r)
        assertEquals(42L, r.versionCode)
    }
}
