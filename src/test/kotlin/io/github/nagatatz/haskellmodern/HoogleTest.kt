package io.github.nagatatz.haskellmodern

import io.github.nagatatz.haskellmodern.hoogle.HoogleClient
import io.github.nagatatz.haskellmodern.hoogle.HoogleJsonParser
import io.github.nagatatz.haskellmodern.hoogle.HoogleQuery
import io.github.nagatatz.haskellmodern.hoogle.HtmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoogleTest {
    // hoogle.haskell.org の実レスポンス (format=text) から抜粋
    private val sample = """
    [{"docs":"Insert a new key and value in the map. If the key is already present in the map, the associated value is\nreplaced with the supplied value.\n","item":"insert :: Ord k => k -> a -> Map k a -> Map k a","module":{"name":"Data.Map.Strict","url":"https://hackage.haskell.org/package/containers/docs/Data-Map-Strict.html"},"package":{"name":"containers","url":"https://hackage.haskell.org/package/containers"},"type":"","url":"https://hackage.haskell.org/package/containers/docs/Data-Map-Strict.html#v:insert"},
     {"docs":"","item":"module Data.Map","module":{},"package":{"name":"containers","url":"https://hackage.haskell.org/package/containers"},"type":"module","url":"https://hackage.haskell.org/package/containers/docs/Data-Map.html"}]
    """.trimIndent()

    @Test
    fun parsesJsonArray() {
        val rs = HoogleJsonParser.parse(sample)
        assertEquals(2, rs.size)
        val r = rs[0]
        assertEquals("insert", r.name)
        assertEquals("Data.Map.Strict", r.moduleName)
        assertEquals("containers", r.packageName)
        assertTrue(r.docs.startsWith("Insert a new key"))
        assertTrue(r.url.endsWith("#v:insert"))
        assertEquals("module", rs[1].type)
        assertNull(rs[1].moduleName)
    }

    @Test
    fun parsesJsonLinesAndHtml() {
        val lines = """
            {"docs":"&lt;b&gt;x","item":"<span class=name><s0>map</s0></span> :: (a -&gt; b) -&gt; [a] -&gt; [b]","module":{"name":"Prelude","url":"u"},"package":{"name":"base","url":"p"},"type":"","url":"https://x#v:map"}
            {"docs":"","item":"fmap :: Functor f =&gt; (a -&gt; b) -&gt; f a -&gt; f b","module":{"name":"Prelude","url":"u"},"package":{"name":"base","url":"p"},"type":"","url":"y"}
        """.trimIndent()
        val rs = HoogleJsonParser.parse(lines)
        assertEquals(2, rs.size)
        assertEquals("map :: (a -> b) -> [a] -> [b]", rs[0].item)
        assertEquals("<b>x", rs[0].docs)
        assertEquals("fmap", rs[1].name)
    }

    @Test
    fun emptyAndGarbage() {
        assertEquals(0, HoogleJsonParser.parse("").size)
        assertEquals(0, HoogleJsonParser.parse("[]").size)
        assertEquals(0, HoogleJsonParser.parse("No results found").size)
    }

    @Test
    fun buildsUrl() {
        assertEquals(
            "https://hoogle.haskell.org/?mode=json&format=text&hoogle=Map.insert+%2Bcontainers&start=1&count=5",
            HoogleClient.buildUrl("https://hoogle.haskell.org", "Map.insert +containers", 5),
        )
        assertTrue(HoogleClient.buildUrl("http://localhost:8080/", "a -> a", 1).startsWith("http://localhost:8080/?mode=json"))
    }

    @Test
    fun hackageAnchors() {
        assertEquals("-60--36--62-", HoogleClient.haddockAnchor("(<$>)"))
        assertEquals(
            "https://hackage.haskell.org/package/base-4.19.1.0/docs/GHC-Base.html#v:map",
            HoogleClient.hackageDocUrl("base-4.19.1.0", "GHC.Base", "map"),
        )
        assertTrue(HoogleClient.hackageDocUrl("containers", "Data.Map", "Map").endsWith("#t:Map"))
    }

    @Test
    fun queryAtCaret() {
        val text = "main = print (M.insert 1 'a' m <> foldl' f z xs)"
        assertEquals("M.insert", HoogleQuery.atOffset(text, text.indexOf("insert") + 2))
        assertEquals("M.insert", HoogleQuery.atOffset(text, text.indexOf("M.") ))
        assertEquals("(<>)", HoogleQuery.atOffset(text, text.indexOf("<>") + 1))
        assertEquals("foldl'", HoogleQuery.atOffset(text, text.indexOf("foldl'") + 6))
        assertEquals("print", HoogleQuery.atOffset(text, text.indexOf("print")))
        assertNull(HoogleQuery.atOffset(text, text.indexOf("=")))
        assertEquals("insert", HoogleQuery.unqualified("M.insert"))
        assertNull(HoogleQuery.unqualified("a -> a"))
        // 関数合成 f.g は修飾名にしない
        assertEquals("g", HoogleQuery.atOffset("x = f.g", 6))
    }

    @Test
    fun htmlEntities() {
        assertEquals("a < b & \"c\" λ", HtmlText.toPlain("a &lt; b &amp; &quot;c&quot; &#955;"))
    }
}
