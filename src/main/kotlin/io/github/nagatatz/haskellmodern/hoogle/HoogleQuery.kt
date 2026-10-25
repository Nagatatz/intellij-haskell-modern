package io.github.nagatatz.haskellmodern.hoogle

import io.github.nagatatz.haskellmodern.lang.HaskellLexer

/** キャレット位置から Hoogle 検索語 (識別子 / 修飾名 / 演算子) を取り出す。 */
object HoogleQuery {

    fun atOffset(text: CharSequence, offset: Int): String? {
        if (text.isEmpty()) return null
        val candidates = listOf(offset, offset - 1).filter { it in text.indices }
        for (pos in candidates) {
            val c = text[pos]
            if (isIdentChar(c)) return identAround(text, pos)
            if (HaskellLexer.isSymbol(c)) return symbolAround(text, pos)
        }
        return null
    }

    private fun isIdentChar(c: Char) = HaskellLexer.isIdentPart(c)

    private fun identAround(text: CharSequence, pos: Int): String? {
        var start = pos
        var end = pos + 1
        while (start > 0 && isIdentChar(text[start - 1])) start--
        while (end < text.length && isIdentChar(text[end])) end++
        // 後方: `Data.Map.insert` の修飾部分 (大文字始まりのセグメント) を取り込む
        while (start >= 2 && text[start - 1] == '.') {
            var segStart = start - 1
            while (segStart > 0 && isIdentChar(text[segStart - 1])) segStart--
            if (segStart == start - 1 || !text[segStart].isUpperCase()) break
            start = segStart
        }
        // 前方: カーソルがモジュール部分にある場合
        while (end + 1 < text.length && text[end] == '.' && text[end - 1].let { isIdentChar(it) } &&
            currentSegmentIsConId(text, start, end) && (text[end + 1].isLetter() || text[end + 1] == '_')
        ) {
            var e = end + 1
            while (e < text.length && isIdentChar(text[e])) e++
            end = e
        }
        val word = text.subSequence(start, end).toString()
        if (word.isEmpty() || word.first().isDigit() || word in HaskellLexer.KEYWORDS) return null
        return word
    }

    private fun currentSegmentIsConId(text: CharSequence, start: Int, end: Int): Boolean =
        text.subSequence(start, end).toString().substringAfterLast('.').firstOrNull()?.isUpperCase() == true

    /** 修飾名 `M.insert` で結果が無い場合に使う非修飾名 */
    fun unqualified(query: String): String? {
        val q = query.trim()
        if (q.contains(' ') || !q.contains('.') || q.startsWith("(")) return null
        val last = q.substringAfterLast('.')
        return last.takeIf { it.isNotEmpty() && it != q }
    }

    private fun symbolAround(text: CharSequence, pos: Int): String? {
        var start = pos
        var end = pos + 1
        while (start > 0 && HaskellLexer.isSymbol(text[start - 1])) start--
        while (end < text.length && HaskellLexer.isSymbol(text[end])) end++
        val op = text.subSequence(start, end).toString()
        if (op in setOf("=", "::", "->", "<-", "=>", "|", "\\", "@", "~", "--") || op.all { it == '-' }) return null
        return "($op)"
    }
}
