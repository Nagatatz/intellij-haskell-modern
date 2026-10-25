package io.github.nagatatz.haskellmodern.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import io.github.nagatatz.haskellmodern.lang.HaskellTokenTypes as T

object HaskellColors {
    @JvmField val LINE_COMMENT = createTextAttributesKey("HASKELL_LINE_COMMENT", D.LINE_COMMENT)
    @JvmField val BLOCK_COMMENT = createTextAttributesKey("HASKELL_BLOCK_COMMENT", D.BLOCK_COMMENT)
    @JvmField val DOC_COMMENT = createTextAttributesKey("HASKELL_DOC_COMMENT", D.DOC_COMMENT)
    @JvmField val PRAGMA = createTextAttributesKey("HASKELL_PRAGMA", D.METADATA)
    @JvmField val CPP = createTextAttributesKey("HASKELL_CPP", D.PREDEFINED_SYMBOL)
    @JvmField val KEYWORD = createTextAttributesKey("HASKELL_KEYWORD", D.KEYWORD)
    @JvmField val VARID = createTextAttributesKey("HASKELL_VARID", D.IDENTIFIER)
    @JvmField val TOP_DECL = createTextAttributesKey("HASKELL_TOP_DECL", D.FUNCTION_DECLARATION)
    @JvmField val CONID = createTextAttributesKey("HASKELL_CONID", D.CLASS_NAME)
    @JvmField val QVARID = createTextAttributesKey("HASKELL_QVARID", D.STATIC_METHOD)
    @JvmField val VARSYM = createTextAttributesKey("HASKELL_VARSYM", D.OPERATION_SIGN)
    @JvmField val CONSYM = createTextAttributesKey("HASKELL_CONSYM", D.OPERATION_SIGN)
    @JvmField val RESERVED_OP = createTextAttributesKey("HASKELL_RESERVED_OP", D.KEYWORD)
    @JvmField val STRING = createTextAttributesKey("HASKELL_STRING", D.STRING)
    @JvmField val CHAR = createTextAttributesKey("HASKELL_CHAR", D.STRING)
    @JvmField val NUMBER = createTextAttributesKey("HASKELL_NUMBER", D.NUMBER)
    @JvmField val PARENS = createTextAttributesKey("HASKELL_PARENS", D.PARENTHESES)
    @JvmField val BRACKETS = createTextAttributesKey("HASKELL_BRACKETS", D.BRACKETS)
    @JvmField val BRACES = createTextAttributesKey("HASKELL_BRACES", D.BRACES)
    @JvmField val COMMA = createTextAttributesKey("HASKELL_COMMA", D.COMMA)
    @JvmField val SEMICOLON = createTextAttributesKey("HASKELL_SEMICOLON", D.SEMICOLON)
    @JvmField val BACKQUOTE = createTextAttributesKey("HASKELL_BACKQUOTE", D.OPERATION_SIGN)
    @JvmField val TICK = createTextAttributesKey("HASKELL_TICK", D.METADATA)
    @JvmField val BAD_CHARACTER = createTextAttributesKey("HASKELL_BAD_CHARACTER", com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER)
}

class HaskellSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = HaskellLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(MAP[tokenType])

    companion object {
        private val MAP: Map<IElementType, TextAttributesKey> = mapOf(
            T.LINE_COMMENT to HaskellColors.LINE_COMMENT,
            T.BLOCK_COMMENT to HaskellColors.BLOCK_COMMENT,
            T.DOC_COMMENT to HaskellColors.DOC_COMMENT,
            T.PRAGMA to HaskellColors.PRAGMA,
            T.CPP to HaskellColors.CPP,
            T.KEYWORD to HaskellColors.KEYWORD,
            T.VARID to HaskellColors.VARID,
            T.TOP_DECL to HaskellColors.TOP_DECL,
            T.CONID to HaskellColors.CONID,
            T.QVARID to HaskellColors.QVARID,
            T.VARSYM to HaskellColors.VARSYM,
            T.CONSYM to HaskellColors.CONSYM,
            T.RESERVED_OP to HaskellColors.RESERVED_OP,
            T.STRING to HaskellColors.STRING,
            T.CHAR to HaskellColors.CHAR,
            T.INTEGER to HaskellColors.NUMBER,
            T.FLOAT to HaskellColors.NUMBER,
            T.LPAREN to HaskellColors.PARENS,
            T.RPAREN to HaskellColors.PARENS,
            T.LBRACKET to HaskellColors.BRACKETS,
            T.RBRACKET to HaskellColors.BRACKETS,
            T.LBRACE to HaskellColors.BRACES,
            T.RBRACE to HaskellColors.BRACES,
            T.COMMA to HaskellColors.COMMA,
            T.SEMICOLON to HaskellColors.SEMICOLON,
            T.BACKQUOTE to HaskellColors.BACKQUOTE,
            T.TICK to HaskellColors.TICK,
            TokenType.BAD_CHARACTER to HaskellColors.BAD_CHARACTER,
        )
    }
}

class HaskellSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        HaskellSyntaxHighlighter()
}
