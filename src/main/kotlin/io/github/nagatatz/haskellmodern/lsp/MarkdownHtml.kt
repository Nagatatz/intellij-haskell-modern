package io.github.nagatatz.haskellmodern.lsp

import io.github.nagatatz.haskellmodern.hoogle.HtmlText

/**
 * HLS の hover (Markdown) を Quick Documentation 用の HTML に変換する最小限の変換器。
 * 対応: フェンスドコード, インラインコード, 強調, リンク, 箇条書き, 水平線, 段落。
 */
object MarkdownHtml {
    private val INLINE_CODE = Regex("`([^`]+)`")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
    private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val ITALIC = Regex("(?<![*\\w])\\*([^*\\s][^*]*)\\*(?![*\\w])")

    fun toHtml(markdown: String): String {
        val out = StringBuilder()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        var i = 0
        val para = mutableListOf<String>()
        var inList = false

        fun flushPara() {
            if (para.isNotEmpty()) {
                out.append("<p>").append(para.joinToString(" ") { inline(it) }).append("</p>")
                para.clear()
            }
        }
        fun closeList() {
            if (inList) { out.append("</ul>"); inList = false }
        }

        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flushPara(); closeList()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n'); i++
                    }
                    out.append("<pre><code>").append(HtmlText.escape(code.toString().trimEnd())).append("</code></pre>")
                }
                t.isEmpty() -> { flushPara(); closeList() }
                t == "* * *" || t == "---" || t == "***" || t == "___" -> { flushPara(); closeList(); out.append("<hr/>") }
                t.startsWith("- ") || (t.startsWith("* ") && !t.startsWith("* *")) -> {
                    flushPara()
                    if (!inList) { out.append("<ul>"); inList = true }
                    out.append("<li>").append(inline(t.substring(2))).append("</li>")
                }
                t.startsWith("#") -> {
                    flushPara(); closeList()
                    val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    out.append("<h$level>").append(inline(t.drop(level).trim())).append("</h$level>")
                }
                else -> { closeList(); para.add(t) }
            }
            i++
        }
        flushPara(); closeList()
        return out.toString()
    }

    fun inline(text: String): String {
        // コード部分を退避してからエスケープ・装飾
        val codes = mutableListOf<String>()
        var s = INLINE_CODE.replace(text) { m ->
            codes.add("<code>" + HtmlText.escape(m.groupValues[1]) + "</code>")
            "\u0000${codes.size - 1}\u0000"
        }
        val links = mutableListOf<String>()
        s = LINK.replace(s) { m ->
            val url = m.groupValues[2]
            val label = m.groupValues[1]
            // file:// リンク (コンテナ内の haddock) は IDE から開けないことが多いのでテキストのみにする
            links.add(
                if (url.startsWith("file:")) HtmlText.escape(label)
                else "<a href=\"" + HtmlText.escape(url) + "\">" + HtmlText.escape(label) + "</a>",
            )
            "\u0001${links.size - 1}\u0001"
        }
        s = HtmlText.escape(s)
        s = BOLD.replace(s) { "<b>${it.groupValues[1]}</b>" }
        s = ITALIC.replace(s) { "<i>${it.groupValues[1]}</i>" }
        s = Regex("\u0001(\\d+)\u0001").replace(s) { links[it.groupValues[1].toInt()] }
        s = Regex("\u0000(\\d+)\u0000").replace(s) { codes[it.groupValues[1].toInt()] }
        return s
    }
}

/** HLS hover の Markdown を分解した結果 */
data class HoverInfo(
    val signatures: List<String>,
    val definedInModule: String?,
    /** バージョン付き (例: base-4.19.1.0) */
    val definedInPackageId: String?,
    /** Haddock 本文 (Markdown)。無い場合は空 */
    val docMarkdown: String,
) {
    val packageName: String?
        get() = definedInPackageId?.let { stripVersion(it) }

    val hasDocs: Boolean get() = docMarkdown.isNotBlank()

    companion object {
        private val DEFINED_IN = Regex("\\*Defined (?:in|at) [‘'`]?([^’'`*]+?)[’'`]?\\*(?:\\s*\\*\\(([^)]+)\\)\\*)?")
        private val DOC_LINKS = Regex("\\[(?:Documentation|Source)]\\([^)]*\\)")

        fun stripVersion(pkgId: String): String = pkgId.replace(Regex("-[0-9]+(\\.[0-9]+)*$"), "")

        fun parse(markdown: String): HoverInfo {
            val sigs = mutableListOf<String>()
            val rest = StringBuilder()
            val lines = markdown.replace("\r\n", "\n").split('\n')
            var i = 0
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith("```")) {
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                    code.toString().trim().takeIf { it.isNotEmpty() }?.let { sigs.add(it) }
                } else {
                    rest.append(lines[i]).append('\n')
                }
                i++
            }
            var body = rest.toString()
            var module: String? = null
            var pkg: String? = null
            DEFINED_IN.find(body)?.let { m ->
                val where = m.groupValues[1].trim()
                // "Defined at /path/File.hs:12:1" はローカル定義
                if (!where.contains('/') && !where.contains('\\') && !where.contains(':')) module = where
                pkg = m.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            }
            body = DEFINED_IN.replace(body, "")
            body = DOC_LINKS.replace(body, "")
            val cleaned = body.lines()
                .filterNot { it.trim() in setOf("* * *", "---", "***", "___") }
                .joinToString("\n")
                .trim()
            return HoverInfo(sigs, module, pkg, cleaned)
        }
    }
}
