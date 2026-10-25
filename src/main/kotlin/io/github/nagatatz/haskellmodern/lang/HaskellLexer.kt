package io.github.nagatatz.haskellmodern.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import io.github.nagatatz.haskellmodern.lang.HaskellTokenTypes as T

/**
 * 手書きの Haskell 2010 + GHC 拡張向けハイライト用 Lexer。
 *
 * 1 トークンが閉じるまで (ブロックコメント・文字列) を 1 回で読み切るため、トークン境界では
 * 常に初期状態 (state = 0) になる。これにより IntelliJ のインクリメンタル再字句解析とも整合する。
 * 意味的なハイライト (関数/型変数/モジュール等) は HLS の semantic tokens に任せる。
 */
class HaskellLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        locateToken(startOffset)
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        locateToken(tokenEnd)
    }

    private fun ch(i: Int): Char = if (i < endOffset) buffer[i] else '\u0000'

    private fun locateToken(start: Int) {
        tokenStart = start
        if (start >= endOffset) {
            tokenType = null
            tokenEnd = start
            return
        }
        val c = buffer[start]
        val (type, end) = when {
            c.isWhitespace() -> T.WHITE_SPACE to skipWhile(start) { it.isWhitespace() }
            c == '{' && ch(start + 1) == '-' -> lexBlockComment(start)
            c == '#' && isLineStart(start) -> T.CPP to lexCppLine(start)
            c == '#' && ch(start + 1) == '{' -> T.CPP to lexHscSplice(start)
            c == '"' -> T.STRING to lexString(start)
            c == '\'' -> lexTick(start)
            c.isDigit() -> lexNumber(start)
            c.isUpperCase() -> lexConOrQualified(start)
            isIdentStart(c) -> lexVarId(start)
            c == '(' -> T.LPAREN to start + 1
            c == ')' -> T.RPAREN to start + 1
            c == '[' -> T.LBRACKET to start + 1
            c == ']' -> T.RBRACKET to start + 1
            c == '{' -> T.LBRACE to start + 1
            c == '}' -> T.RBRACE to start + 1
            c == ',' -> T.COMMA to start + 1
            c == ';' -> T.SEMICOLON to start + 1
            c == '`' -> T.BACKQUOTE to start + 1
            isSymbol(c) -> lexSymbol(start)
            else -> T.BAD_CHARACTER to start + 1
        }
        tokenType = type
        tokenEnd = maxOf(end, start + 1).coerceAtMost(endOffset)
    }

    private inline fun skipWhile(start: Int, pred: (Char) -> Boolean): Int {
        var i = start
        while (i < endOffset && pred(buffer[i])) i++
        return i
    }

    private fun isLineStart(pos: Int): Boolean = pos == 0 || buffer[pos - 1] == '\n'

    private fun toEol(start: Int): Int = skipWhile(start) { it != '\n' }

    private fun lexBlockComment(start: Int): Pair<IElementType, Int> {
        val pragma = ch(start + 2) == '#'
        val doc = !pragma && run {
            var j = start + 2
            while (ch(j) == ' ') j++
            ch(j) == '|' || ch(j) == '^' || ch(j) == '$' || ch(j) == '*'
        }
        var depth = 0
        var i = start
        while (i < endOffset) {
            if (buffer[i] == '{' && ch(i + 1) == '-') {
                depth++; i += 2; continue
            }
            if (buffer[i] == '-' && ch(i + 1) == '}') {
                depth--; i += 2
                if (depth == 0) break
                continue
            }
            i++
        }
        val type = when {
            pragma -> T.PRAGMA
            doc -> T.DOC_COMMENT
            else -> T.BLOCK_COMMENT
        }
        return type to i
    }

    private fun lexCppLine(start: Int): Int {
        var i = start
        while (i < endOffset) {
            val eol = toEol(i)
            // 行末バックスラッシュによる継続行
            if (eol > start && eol < endOffset && buffer[eol - 1] == '\\') {
                i = eol + 1
                continue
            }
            return eol
        }
        return endOffset
    }

    private fun lexHscSplice(start: Int): Int {
        var i = start + 2
        while (i < endOffset && buffer[i] != '}' && buffer[i] != '\n') i++
        return if (i < endOffset && buffer[i] == '}') i + 1 else i
    }

    private fun lexString(start: Int): Int {
        var i = start + 1
        while (i < endOffset) {
            when (buffer[i]) {
                '"' -> return i + 1
                '\\' -> {
                    // 文字列ギャップ (`\   \`) は改行をまたげる
                    if (i + 1 < endOffset && buffer[i + 1].isWhitespace()) {
                        var j = i + 1
                        while (j < endOffset && buffer[j].isWhitespace()) j++
                        i = if (ch(j) == '\\') j + 1 else j
                        continue
                    }
                    i += 2
                }
                '\n' -> return i
                else -> i++
            }
        }
        return endOffset
    }

    private fun lexTick(start: Int): Pair<IElementType, Int> {
        // 文字リテラル: 'a' '\n' '\'' '\x41' '\SOH'
        val c1 = ch(start + 1)
        if (c1 == '\\') {
            var i = start + 2
            if (ch(i) != '\u0000') i++
            while (i < endOffset && buffer[i] != '\'' && buffer[i] != '\n' && i - start < 12) i++
            if (ch(i) == '\'') return T.CHAR to i + 1
        } else if (c1 != '\u0000' && c1 != '\n' && c1 != '\'' && ch(start + 2) == '\'') {
            return T.CHAR to start + 3
        } else if (c1.isSurrogate() && ch(start + 3) == '\'') {
            return T.CHAR to start + 4
        }
        // TH の名前クォート ('foo, ''Foo) や DataKinds の昇格 ('[], ':)
        return T.TICK to if (c1 == '\'') start + 2 else start + 1
    }

    private fun lexNumber(start: Int): Pair<IElementType, Int> {
        val c1 = ch(start + 1).lowercaseChar()
        if (buffer[start] == '0' && (c1 == 'x' || c1 == 'o' || c1 == 'b')) {
            val end = skipWhile(start + 2) { it.isLetterOrDigit() || it == '_' }
            return T.INTEGER to end
        }
        var i = skipWhile(start) { it.isDigit() || it == '_' }
        var float = false
        if (ch(i) == '.' && ch(i + 1).isDigit()) {
            float = true
            i = skipWhile(i + 1) { it.isDigit() || it == '_' }
        }
        if ((ch(i) == 'e' || ch(i) == 'E') &&
            (ch(i + 1).isDigit() || ((ch(i + 1) == '+' || ch(i + 1) == '-') && ch(i + 2).isDigit()))
        ) {
            float = true
            i = skipWhile(i + 2) { it.isDigit() || it == '_' }
        }
        // MagicHash: 1#, 1.0##
        while (ch(i) == '#') i++
        return (if (float) T.FLOAT else T.INTEGER) to i
    }

    private fun identEnd(start: Int): Int {
        var i = skipWhile(start) { isIdentPart(it) }
        while (ch(i) == '#') i++ // MagicHash
        return i
    }

    private fun lexConOrQualified(start: Int): Pair<IElementType, Int> {
        var end = identEnd(start)
        // Module.Name.Con / Module.var / Module.+ を 1 トークンにまとめる
        while (ch(end) == '.') {
            val next = ch(end + 1)
            when {
                next.isUpperCase() -> end = identEnd(end + 1)
                isIdentStart(next) -> {
                    val e = identEnd(end + 1)
                    val word = buffer.subSequence(end + 1, e).toString()
                    return if (word in KEYWORDS) T.CONID to end else T.QVARID to e
                }
                isSymbol(next) -> {
                    val e = skipWhile(end + 1) { isSymbol(it) }
                    return T.QVARID to e
                }
                else -> return T.CONID to end
            }
        }
        return T.CONID to end
    }

    private fun lexVarId(start: Int): Pair<IElementType, Int> {
        val end = identEnd(start)
        val word = buffer.subSequence(start, end).toString()
        val type = when {
            word in KEYWORDS -> T.KEYWORD
            word in IMPORT_KEYWORDS && isOnImportLine(start) -> T.KEYWORD
            isLineStart(start) -> T.TOP_DECL
            else -> T.VARID
        }
        return type to end
    }

    private fun isOnImportLine(pos: Int): Boolean {
        var i = pos
        while (i > 0 && buffer[i - 1] != '\n') i--
        // 複数行にまたがる import リストの場合は直前の非インデント行を見る
        while (i > 0 && (buffer[i] == ' ' || buffer[i] == '\t')) {
            var j = i - 1
            while (j > 0 && buffer[j - 1] != '\n') j--
            i = j
        }
        return buffer.length >= i + 6 && buffer.subSequence(i, minOf(i + 7, buffer.length)).startsWith("import")
    }

    private fun lexSymbol(start: Int): Pair<IElementType, Int> {
        val end = skipWhile(start) { isSymbol(it) }
        val sym = buffer.subSequence(start, end).toString()
        if (sym.length >= 2 && sym.all { it == '-' }) {
            // `--` 以降は行コメント。`-- |` などは Haddock
            val eol = toEol(start)
            var j = end
            while (j < eol && buffer[j] == ' ') j++
            val c = ch(j)
            val doc = j < eol && (c == '|' || c == '^' || c == '$' || (c == '*' && j > end))
            return (if (doc) T.DOC_COMMENT else T.LINE_COMMENT) to eol
        }
        val type = when {
            sym in RESERVED_OPS -> T.RESERVED_OP
            sym.startsWith(":") -> T.CONSYM
            else -> T.VARSYM
        }
        return type to end
    }

    companion object {
        val KEYWORDS: Set<String> = setOf(
            "case", "class", "data", "default", "deriving", "do", "else", "foreign", "if", "import",
            "in", "infix", "infixl", "infixr", "instance", "let", "module", "newtype", "of", "then",
            "type", "where", "_", "forall", "family", "mdo",
        )
        val IMPORT_KEYWORDS: Set<String> = setOf("qualified", "as", "hiding", "safe")
        val RESERVED_OPS: Set<String> = setOf(
            "..", ":", "::", "=", "\\", "|", "<-", "->", "@", "~", "=>",
            "∷", "⇒", "→", "←", "∀", "-<", ">-", "-<<", ">>-", "★",
        )

        fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'
        fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '\''

        fun isSymbol(c: Char): Boolean = when (c) {
            '!', '#', '$', '%', '&', '*', '+', '.', '/', '<', '=', '>', '?', '@', '\\', '^', '|', '-', '~', ':' -> true
            '(', ')', '[', ']', '{', '}', ',', ';', '`', '"', '\'', '_' -> false
            else -> !c.isLetterOrDigit() && !c.isWhitespace() && c.code > 127 && when (Character.getType(c).toByte()) {
                Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
                Character.OTHER_SYMBOL, Character.DASH_PUNCTUATION, Character.OTHER_PUNCTUATION,
                -> true
                else -> false
            }
        }
    }
}
