package io.github.nagatatz.haskellmodern

import io.github.nagatatz.haskellmodern.hoogle.HoogleResult
import io.github.nagatatz.haskellmodern.lsp.HaskellDocumentationBuilder
import io.github.nagatatz.haskellmodern.lsp.HlsConfig
import io.github.nagatatz.haskellmodern.lsp.HoverInfo
import io.github.nagatatz.haskellmodern.lsp.MarkdownHtml
import io.github.nagatatz.haskellmodern.settings.HaskellFormatter
import io.github.nagatatz.haskellmodern.settings.HaskellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsSupportTest {

    @Test
    fun configFromSettings() {
        val s = HaskellSettings.State().apply {
            formatter = HaskellFormatter.FOURMOLU
            hlintEnabled = false
        }
        val h = HlsConfig.haskellSection(s)
        assertEquals("fourmolu", h.get("formattingProvider").asString)
        assertFalse(h.getAsJsonObject("plugin").getAsJsonObject("hlint").get("globalOn").asBoolean)
        assertTrue(h.getAsJsonObject("plugin").getAsJsonObject("semanticTokens").get("globalOn").asBoolean)
        assertTrue(HlsConfig.fullSettings(s).has("haskell"))
    }

    @Test
    fun extraJsonDeepMerge() {
        val s = HaskellSettings.State().apply {
            extraHlsConfigJson = """{"haskell": {"plugin": {"hlint": {"globalOn": false}, "eval": {"globalOn": true}}, "checkProject": false}}"""
        }
        val h = HlsConfig.haskellSection(s)
        val plugin = h.getAsJsonObject("plugin")
        assertFalse(plugin.getAsJsonObject("hlint").get("globalOn").asBoolean)
        // 既存キーは保持される
        assertTrue(plugin.getAsJsonObject("hlint").has("diagnosticsOn"))
        assertTrue(plugin.getAsJsonObject("eval").get("globalOn").asBoolean)
        assertFalse(h.get("checkProject").asBoolean)
        // haskell キー無しでも受け付ける
        val s2 = HaskellSettings.State().apply { extraHlsConfigJson = """{"formattingProvider": "none"}""" }
        assertEquals("none", HlsConfig.haskellSection(s2).get("formattingProvider").asString)
        // 不正 JSON は無視
        assertNull(HlsConfig.parseExtra("{oops"))
        assertNull(HlsConfig.parseExtra("[1,2]"))
    }

    private val hoverWithDocs = """
        ```haskell
        map :: forall a b. (a -> b) -> [a] -> [b]
        ```

        *Defined in ‘GHC.Base’* *(base-4.19.1.0)*

        * * *

        `map` `f xs` is the list obtained by applying *f* to each element.

        [Documentation](file:///home/u/.ghcup/doc/GHC-Base.html#v:map) [Source](file:///x)
    """.trimIndent()

    private val hoverNoDocs = """
        ```haskell
        insert :: forall k a. Ord k => k -> a -> Map k a -> Map k a
        ```

        *Defined in ‘Data.Map.Strict.Internal’* *(containers-0.6.8)*

        [Documentation](file:///x) [Source](file:///y)
    """.trimIndent()

    @Test
    fun parsesHover() {
        val a = HoverInfo.parse(hoverWithDocs)
        assertEquals(listOf("map :: forall a b. (a -> b) -> [a] -> [b]"), a.signatures)
        assertEquals("GHC.Base", a.definedInModule)
        assertEquals("base-4.19.1.0", a.definedInPackageId)
        assertEquals("base", a.packageName)
        assertTrue(a.hasDocs)
        assertTrue(a.docMarkdown.startsWith("`map`"))

        val b = HoverInfo.parse(hoverNoDocs)
        assertFalse(b.hasDocs)
        assertEquals("containers", b.packageName)
        assertEquals("Data.Map.Strict.Internal", b.definedInModule)

        val local = HoverInfo.parse("```haskell\nfoo :: Int\n```\n\n*Defined at /work/src/Foo.hs:3:1*")
        assertNull(local.definedInModule)
        assertFalse(local.hasDocs)
    }

    @Test
    fun markdownToHtml() {
        val html = MarkdownHtml.toHtml("Use `x < y` and *em* **b** [link](https://e.org) [doc](file:///a)\n\n```haskell\na -> b\n```\n- item")
        assertTrue(html, html.contains("<code>x &lt; y</code>"))
        assertTrue(html, html.contains("<i>em</i>"))
        assertTrue(html, html.contains("<b>b</b>"))
        assertTrue(html, html.contains("<a href=\"https://e.org\">link</a>"))
        assertFalse(html, html.contains("file:///a"))
        assertTrue(html, html.contains("<pre><code>a -&gt; b</code></pre>"))
        assertTrue(html, html.contains("<li>item</li>"))
    }

    @Test
    fun hoogleFallbackPicksModuleMatch() {
        fun r(mod: String, docs: String) = HoogleResult(
            "insert :: Ord k => k -> a -> Map k a -> Map k a", "", docs, mod, null, "containers", null, "https://h/$mod#v:insert",
        )
        val results = listOf(r("Data.Map.Lazy", "lazy"), r("Data.Map.Strict.Internal", "strict"), r("Data.Set", "set"))
        assertEquals("strict", HaskellDocumentationBuilder.pickBest(results, "insert", "Data.Map.Strict.Internal")!!.docs)
        assertEquals("lazy", HaskellDocumentationBuilder.pickBest(results, "insert", null)!!.docs)
        assertNull(HaskellDocumentationBuilder.pickBest(results, "lookup", null))

        val html = HaskellDocumentationBuilder.render(HoverInfo.parse(hoverNoDocs), results[1], "insert")
        assertTrue(html, html.contains("strict"))
        assertTrue(html, html.contains("Documentation from Hoogle"))
        assertTrue(html, html.contains("https://h/Data.Map.Strict.Internal#v:insert"))
    }
}
