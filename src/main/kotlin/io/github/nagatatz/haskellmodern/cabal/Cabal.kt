package io.github.nagatatz.haskellmodern.cabal

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import io.github.nagatatz.haskellmodern.HaskellIcons
import io.github.nagatatz.haskellmodern.lang.FlatParser
import javax.swing.Icon

object CabalLanguage : Language("Cabal") {
    private fun readResolve(): Any = CabalLanguage
    override fun getDisplayName(): String = "Cabal"
}

/** `*.cabal` と `cabal.project`, `cabal.project.local`, `cabal.project.freeze` を扱う。 */
object CabalFileType : LanguageFileType(CabalLanguage) {
    override fun getName(): String = "Cabal"
    override fun getDescription(): String = "Cabal package description / project file"
    override fun getDefaultExtension(): String = "cabal"
    override fun getIcon(): Icon = HaskellIcons.CABAL
}

class CabalTokenType(debugName: String) : IElementType(debugName, CabalLanguage)

object CabalTokenTypes {
    @JvmField val COMMENT = CabalTokenType("COMMENT")
    @JvmField val FIELD_NAME = CabalTokenType("FIELD_NAME")
    @JvmField val COLON = CabalTokenType("COLON")
    @JvmField val SECTION = CabalTokenType("SECTION")
    @JvmField val CONDITIONAL = CabalTokenType("CONDITIONAL")
    @JvmField val WORD = CabalTokenType("WORD")
    @JvmField val VERSION = CabalTokenType("VERSION")
    @JvmField val OPERATOR = CabalTokenType("OPERATOR")
    @JvmField val STRING = CabalTokenType("STRING")
    @JvmField val COMMA = CabalTokenType("COMMA")
    @JvmField val LPAREN = CabalTokenType("(")
    @JvmField val RPAREN = CabalTokenType(")")
    @JvmField val LBRACE = CabalTokenType("{")
    @JvmField val RBRACE = CabalTokenType("}")

    val COMMENTS: TokenSet = TokenSet.create(COMMENT)
    val STRINGS: TokenSet = TokenSet.create(STRING)
    val WHITESPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
}

/** 行指向の簡易 Lexer。行頭 (インデント後) の `name:` をフィールド名、セクション見出しをキーワードとして扱う。 */
class CabalLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        locate(startOffset)
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset
    override fun advance() = locate(tokenEnd)

    private fun ch(i: Int): Char = if (i < endOffset) buffer[i] else '\u0000'

    private fun firstOnLine(pos: Int): Boolean {
        var i = pos - 1
        while (i >= 0 && (buffer[i] == ' ' || buffer[i] == '\t')) i--
        return i < 0 || buffer[i] == '\n'
    }

    private fun eol(pos: Int): Int {
        var i = pos
        while (i < endOffset && buffer[i] != '\n') i++
        return i
    }

    private fun locate(start: Int) {
        tokenStart = start
        if (start >= endOffset) {
            tokenType = null; tokenEnd = start; return
        }
        val c = buffer[start]
        var end = start + 1
        val T = CabalTokenTypes
        tokenType = when {
            c.isWhitespace() -> {
                while (end < endOffset && buffer[end].isWhitespace()) end++
                TokenType.WHITE_SPACE
            }
            c == '-' && ch(start + 1) == '-' && firstOnLine(start) -> {
                end = eol(start); T.COMMENT
            }
            c == '"' -> {
                while (end < endOffset && buffer[end] != '"' && buffer[end] != '\n') {
                    if (buffer[end] == '\\') end++
                    end++
                }
                if (ch(end) == '"') end++
                T.STRING
            }
            isWordChar(c) -> {
                while (end < endOffset && isWordChar(buffer[end])) end++
                val word = buffer.subSequence(start, end).toString()
                var j = end
                while (j < endOffset && (buffer[j] == ' ' || buffer[j] == '\t')) j++
                val lower = word.lowercase()
                when {
                    firstOnLine(start) && ch(j) == ':' -> T.FIELD_NAME
                    firstOnLine(start) && lower in SECTIONS -> T.SECTION
                    firstOnLine(start) && lower in CONDITIONALS -> T.CONDITIONAL
                    word.first().isDigit() && word.all { it.isDigit() || it == '.' || it == '*' } -> T.VERSION
                    else -> T.WORD
                }
            }
            c == ':' -> T.COLON
            c == ',' -> T.COMMA
            c == '(' -> T.LPAREN
            c == ')' -> T.RPAREN
            c == '{' -> T.LBRACE
            c == '}' -> T.RBRACE
            else -> {
                while (end < endOffset && buffer[end] in OPERATOR_CHARS) end++
                T.OPERATOR
            }
        }
        tokenEnd = end
    }

    companion object {
        val SECTIONS = setOf(
            "library", "executable", "test-suite", "benchmark", "foreign-library", "common", "flag",
            "source-repository", "custom-setup", "package", "source-repository-package", "program-options",
            "repository", "program-locations",
        )
        val CONDITIONALS = setOf("if", "elif", "else")
        private const val OPERATOR_CHARS = "<>=^&|!*"

        fun isWordChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '/' || c == '+' || c == '@'
    }
}

object CabalColors {
    @JvmField val COMMENT = createTextAttributesKey("CABAL_COMMENT", D.LINE_COMMENT)
    @JvmField val FIELD_NAME = createTextAttributesKey("CABAL_FIELD_NAME", D.INSTANCE_FIELD)
    @JvmField val SECTION = createTextAttributesKey("CABAL_SECTION", D.KEYWORD)
    @JvmField val CONDITIONAL = createTextAttributesKey("CABAL_CONDITIONAL", D.KEYWORD)
    @JvmField val VERSION = createTextAttributesKey("CABAL_VERSION", D.NUMBER)
    @JvmField val OPERATOR = createTextAttributesKey("CABAL_OPERATOR", D.OPERATION_SIGN)
    @JvmField val STRING = createTextAttributesKey("CABAL_STRING", D.STRING)
    @JvmField val COLON = createTextAttributesKey("CABAL_COLON", D.OPERATION_SIGN)
}

class CabalSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = CabalLexer()
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(
        when (tokenType) {
            CabalTokenTypes.COMMENT -> CabalColors.COMMENT
            CabalTokenTypes.FIELD_NAME -> CabalColors.FIELD_NAME
            CabalTokenTypes.SECTION -> CabalColors.SECTION
            CabalTokenTypes.CONDITIONAL -> CabalColors.CONDITIONAL
            CabalTokenTypes.VERSION -> CabalColors.VERSION
            CabalTokenTypes.OPERATOR -> CabalColors.OPERATOR
            CabalTokenTypes.STRING -> CabalColors.STRING
            CabalTokenTypes.COLON -> CabalColors.COLON
            else -> null
        },
    )
}

class CabalSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        CabalSyntaxHighlighter()
}

class CabalFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CabalLanguage) {
    override fun getFileType(): FileType = CabalFileType
}

class CabalParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = CabalLexer()
    override fun createParser(project: Project?): PsiParser = FlatParser
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = CabalTokenTypes.COMMENTS
    override fun getStringLiteralElements(): TokenSet = CabalTokenTypes.STRINGS
    override fun getWhitespaceTokens(): TokenSet = CabalTokenTypes.WHITESPACES
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = CabalFile(viewProvider)

    companion object {
        val FILE = IFileElementType(CabalLanguage)
    }
}

class CabalCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "-- "
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
