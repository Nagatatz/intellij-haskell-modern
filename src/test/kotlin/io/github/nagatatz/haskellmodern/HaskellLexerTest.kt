package io.github.nagatatz.haskellmodern

import com.intellij.psi.TokenType
import io.github.nagatatz.haskellmodern.lang.HaskellLexer
import io.github.nagatatz.haskellmodern.lang.HaskellTokenTypes as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaskellLexerTest {
    private fun lex(text: String): List<Pair<String, String>> {
        val lexer = HaskellLexer()
        lexer.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<String, String>>()
        var last = 0
        while (lexer.tokenType != null) {
            assertEquals("tokens must be contiguous", last, lexer.tokenStart)
            assertTrue("token must not be empty", lexer.tokenEnd > lexer.tokenStart)
            if (lexer.tokenType != TokenType.WHITE_SPACE) {
                out += lexer.tokenType.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd)
            }
            last = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals(text.length, last)
        return out
    }

    private fun types(text: String) = lex(text).map { it.first }

    @Test
    fun moduleHeaderAndImports() {
        val toks = lex("module Main (main) where\nimport qualified Data.Map.Strict as M\n")
        assertEquals(T.KEYWORD.toString() to "module", toks[0])
        assertEquals(T.CONID.toString() to "Main", toks[1])
        assertTrue(toks.contains(T.KEYWORD.toString() to "qualified"))
        assertTrue(toks.contains(T.CONID.toString() to "Data.Map.Strict"))
        assertTrue(toks.contains(T.KEYWORD.toString() to "as"))
    }

    @Test
    fun qualifiedOnlyKeywordOnImportLine() {
        val toks = lex("f qualified as = qualified\n")
        assertTrue(toks.none { it.first == T.KEYWORD.toString() })
    }

    @Test
    fun topLevelDeclaration() {
        val toks = lex("area :: Shape -> Double\narea s = go s\n  where go = id\n")
        assertEquals(T.TOP_DECL.toString() to "area", toks[0])
        assertEquals(T.RESERVED_OP.toString() to "::", toks[1])
        assertEquals(T.RESERVED_OP.toString() to "->", toks[3])
        assertTrue(toks.contains(T.VARID.toString() to "go"))
    }

    @Test
    fun nestedBlockCommentsPragmasAndHaddock() {
        val text = "{-# LANGUAGE GADTs #-}\n{- a {- nested -} b -}\n-- | doc\n-- plain\n--> op\n"
        val toks = lex(text)
        assertEquals(T.PRAGMA.toString() to "{-# LANGUAGE GADTs #-}", toks[0])
        assertEquals(T.BLOCK_COMMENT.toString() to "{- a {- nested -} b -}", toks[1])
        assertEquals(T.DOC_COMMENT.toString(), toks[2].first)
        assertEquals(T.LINE_COMMENT.toString(), toks[3].first)
        assertEquals(T.VARSYM.toString() to "-->", toks[4])
    }

    @Test
    fun literals() {
        assertEquals(
            listOf(T.STRING, T.CHAR, T.CHAR, T.INTEGER, T.INTEGER, T.FLOAT, T.INTEGER).map { it.toString() },
            types("\"a\\\"b\" 'x' '\\n' 0x1F 1_000 1.5e-3 42"),
        )
    }

    @Test
    fun promotionTickAndPrimes() {
        val toks = lex("y = foldl' x '[] 'Just")
        assertEquals(T.VARID.toString() to "foldl'", toks[2])
        assertTrue(toks.any { it.first == T.TICK.toString() })
    }

    @Test
    fun operatorsAndBackquotes() {
        val toks = lex("a <$> b `div` c :| d")
        assertTrue(toks.contains(T.VARSYM.toString() to "<$>"))
        assertTrue(toks.contains(T.BACKQUOTE.toString() to "`"))
        assertTrue(toks.contains(T.CONSYM.toString() to ":|"))
    }

    @Test
    fun cppLines() {
        val toks = lex("#if MIN_VERSION_base(4,18,0)\nx = 1\n#endif\n")
        assertEquals(T.CPP.toString(), toks[0].first)
        assertEquals(T.CPP.toString() to "#endif", toks.last())
    }

    @Test
    fun neverLosesCharacters() {
        // ランダムに近い入力でもトークンが連続・非空であること (lex 内で検査)
        lex("\"unterminated\n{- unterminated comment\n")
        lex("'\n λ → ∀ 𝔸 ¬ x# 0b101 #{const X} \u0000")
    }
}
