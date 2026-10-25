package io.github.nagatatz.haskellmodern.lang

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class HaskellTokenType(debugName: String) : IElementType(debugName, HaskellLanguage) {
    override fun toString(): String = "Haskell:" + super.toString()
}

object HaskellTokenTypes {
    @JvmField val LINE_COMMENT = HaskellTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = HaskellTokenType("BLOCK_COMMENT")
    /** Haddock: `-- |`, `-- ^`, `-- $`, `-- *`, `{- |`, `{- ^` */
    @JvmField val DOC_COMMENT = HaskellTokenType("DOC_COMMENT")
    /** `{-# LANGUAGE ... #-}` 等 */
    @JvmField val PRAGMA = HaskellTokenType("PRAGMA")
    /** 行頭の `#if` / `#include` 等 (CPP, hsc2hs) と `#{...}` */
    @JvmField val CPP = HaskellTokenType("CPP")

    @JvmField val KEYWORD = HaskellTokenType("KEYWORD")
    @JvmField val VARID = HaskellTokenType("VARID")
    /** 行頭 (カラム0) の変数名 = トップレベル定義/型シグネチャの名前 */
    @JvmField val TOP_DECL = HaskellTokenType("TOP_DECL")
    @JvmField val CONID = HaskellTokenType("CONID")
    /** `Data.Map.insert` のような修飾付き変数名 */
    @JvmField val QVARID = HaskellTokenType("QVARID")
    @JvmField val VARSYM = HaskellTokenType("VARSYM")
    @JvmField val CONSYM = HaskellTokenType("CONSYM")
    /** `::` `->` `<-` `=>` `=` `|` `\` `@` `~` `..` `∷` `→` 等 */
    @JvmField val RESERVED_OP = HaskellTokenType("RESERVED_OP")

    @JvmField val STRING = HaskellTokenType("STRING")
    @JvmField val CHAR = HaskellTokenType("CHAR")
    @JvmField val INTEGER = HaskellTokenType("INTEGER")
    @JvmField val FLOAT = HaskellTokenType("FLOAT")

    @JvmField val LPAREN = HaskellTokenType("(")
    @JvmField val RPAREN = HaskellTokenType(")")
    @JvmField val LBRACKET = HaskellTokenType("[")
    @JvmField val RBRACKET = HaskellTokenType("]")
    @JvmField val LBRACE = HaskellTokenType("{")
    @JvmField val RBRACE = HaskellTokenType("}")
    @JvmField val COMMA = HaskellTokenType(",")
    @JvmField val SEMICOLON = HaskellTokenType(";")
    @JvmField val BACKQUOTE = HaskellTokenType("`")
    /** TemplateHaskell の `'name` / `''Type`、DataKinds の昇格 `'[]` など */
    @JvmField val TICK = HaskellTokenType("TICK")

    @JvmField val WHITE_SPACE: IElementType = TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING, CHAR)
    @JvmField val WHITESPACES = TokenSet.create(TokenType.WHITE_SPACE)
}
