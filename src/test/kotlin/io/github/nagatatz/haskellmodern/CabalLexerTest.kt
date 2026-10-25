package io.github.nagatatz.haskellmodern

import com.intellij.psi.TokenType
import io.github.nagatatz.haskellmodern.cabal.CabalLexer
import io.github.nagatatz.haskellmodern.cabal.CabalTokenTypes as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CabalLexerTest {
    private fun lex(text: String): List<Pair<String, String>> {
        val l = CabalLexer()
        l.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<String, String>>()
        var last = 0
        while (l.tokenType != null) {
            assertEquals(last, l.tokenStart)
            assertTrue(l.tokenEnd > l.tokenStart)
            if (l.tokenType != TokenType.WHITE_SPACE) out += l.tokenType.toString() to text.substring(l.tokenStart, l.tokenEnd)
            last = l.tokenEnd
            l.advance()
        }
        assertEquals(text.length, last)
        return out
    }

    @Test
    fun cabalFile() {
        val toks = lex(
            """
            cabal-version: 3.0
            -- comment
            name:          demo
            library
              build-depends: base >=4.18 && <5, containers ^>=0.6
              if flag(dev)
                ghc-options: -Wall
            test-suite spec
            """.trimIndent(),
        )
        assertEquals(C.FIELD_NAME.toString() to "cabal-version", toks[0])
        assertEquals(C.COLON.toString(), toks[1].first)
        assertEquals(C.VERSION.toString() to "3.0", toks[2])
        assertTrue(toks.contains(C.COMMENT.toString() to "-- comment"))
        assertTrue(toks.contains(C.SECTION.toString() to "library"))
        assertTrue(toks.contains(C.FIELD_NAME.toString() to "build-depends"))
        assertTrue(toks.contains(C.OPERATOR.toString() to ">="))
        assertTrue(toks.contains(C.OPERATOR.toString() to "^>="))
        assertTrue(toks.contains(C.CONDITIONAL.toString() to "if"))
        assertTrue(toks.contains(C.WORD.toString() to "-Wall"))
        assertTrue(toks.contains(C.SECTION.toString() to "test-suite"))
    }
}
