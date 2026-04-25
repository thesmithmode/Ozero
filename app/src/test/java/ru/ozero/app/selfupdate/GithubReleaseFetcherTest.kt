package ru.ozero.app.selfupdate

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleaseFetcherTest {

    private val fetcher = GithubReleaseFetcher(owner = "thesmithmode", repo = "Ozero")

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
    fun parsesPrereleaseFlag() {
        val json = """
            {"tag_name":"v0.3.0-rc1","prerelease":true,
             "assets":[{"name":"o.apk","browser_download_url":"u"},{"name":"o.apk.sig","browser_download_url":"s"}]}
        """.trimIndent()
        val r = fetcher.parse(json)
        assertNotNull(r)
        assertTrue(r.isPrerelease)
    }

    @Test
    fun rejectsMissingApk() {
        val json = """
            {"tag_name":"v1.0.0","assets":[{"name":"o.apk.sig","browser_download_url":"s"}]}
        """.trimIndent()
        assertNull(fetcher.parse(json))
    }

    @Test
    fun rejectsMissingSignature() {
        val json = """
            {"tag_name":"v1.0.0","assets":[{"name":"o.apk","browser_download_url":"u"}]}
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
              {"name":"checksums.txt","browser_download_url":"x"},
              {"name":"o.apk","browser_download_url":"a"},
              {"name":"o.apk.sig","browser_download_url":"s"},
              {"name":"o.aab","browser_download_url":"y"}
            ]}
        """.trimIndent()
        val r = fetcher.parse(json)
        assertNotNull(r)
        assertEquals("a", r.apkUrl)
        assertEquals("s", r.sigUrl)
    }
}
