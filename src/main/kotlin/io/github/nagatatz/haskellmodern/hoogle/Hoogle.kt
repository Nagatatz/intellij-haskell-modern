package io.github.nagatatz.haskellmodern.hoogle

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.github.nagatatz.haskellmodern.lsp.HlsLocator
import io.github.nagatatz.haskellmodern.settings.HaskellSettings
import io.github.nagatatz.haskellmodern.settings.HoogleMode
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class HoogleResult(
    /** 例: `insert :: Ord k => k -> a -> Map k a -> Map k a` / `module Data.Map` / `package containers` */
    val item: String,
    /** "" (通常), "module", "package" */
    val type: String,
    val docs: String,
    val moduleName: String?,
    val moduleUrl: String?,
    val packageName: String?,
    val packageUrl: String?,
    val url: String,
) {
    /** item の先頭にある名前 (`insert`, `(<$>)`, `Map`) */
    val name: String
        get() {
            val s = item.removePrefix("type family ").removePrefix("data family ")
                .removePrefix("data ").removePrefix("newtype ").removePrefix("type ").removePrefix("class ")
                .removePrefix("module ").removePrefix("package ")
            val sig = s.indexOf(" :: ")
            val head = if (sig >= 0) s.substring(0, sig) else s.substringBefore(' ')
            return head.trim()
        }

    val location: String
        get() = listOfNotNull(packageName, moduleName).joinToString(" / ")
}

object HoogleJsonParser {
    /** hoogle.haskell.org / `hoogle server` / `hoogle search --json` の出力を解釈する。JSON 配列と JSON Lines の両方に対応。 */
    fun parse(text: String): List<HoogleResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val elements: List<JsonElement> = try {
            val root = JsonParser.parseString(trimmed)
            if (root.isJsonArray) root.asJsonArray.toList() else listOf(root)
        } catch (e: Exception) {
            trimmed.lineSequence().map { it.trim() }.filter { it.startsWith("{") }
                .mapNotNull { line -> runCatching { JsonParser.parseString(line) }.getOrNull() }
                .toList()
        }
        return elements.filter { it.isJsonObject }.map { toResult(it.asJsonObject) }
    }

    private fun toResult(o: JsonObject): HoogleResult {
        val module = o.obj("module")
        val pkg = o.obj("package")
        return HoogleResult(
            item = HtmlText.toPlain(o.str("item") ?: ""),
            type = o.str("type") ?: "",
            docs = HtmlText.toPlain(o.str("docs") ?: "").trim(),
            moduleName = module?.str("name")?.takeIf { it.isNotEmpty() },
            moduleUrl = module?.str("url")?.takeIf { it.isNotEmpty() },
            packageName = pkg?.str("name")?.takeIf { it.isNotEmpty() },
            packageUrl = pkg?.str("url")?.takeIf { it.isNotEmpty() },
            url = o.str("url") ?: "",
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

}

/** Hoogle が返す HTML 断片 (format=text 非対応の古いサーバ向け) をプレーンテキストにする。 */
object HtmlText {
    private val TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val NAMED = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    fun toPlain(html: String): String {
        if (html.indexOf('<') < 0 && html.indexOf('&') < 0) return html
        val noTags = html.replace(Regex("<br\\s*/?>"), "\n").replace(TAG, "")
        return ENTITY.replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> NAMED[e] ?: m.value
            }
        }
    }

    fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

object HoogleClient {

    fun buildUrl(baseUrl: String, query: String, count: Int): String {
        val base = baseUrl.trim().ifEmpty { HaskellSettings.DEFAULT_HOOGLE_URL }.let { if (it.endsWith("/")) it else "$it/" }
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8)
        return "${base}?mode=json&format=text&hoogle=$q&start=1&count=$count"
    }

    /** ブロッキング呼び出し。EDT から呼ばないこと。 */
    @Throws(IOException::class)
    fun search(project: Project?, query: String, count: Int = HaskellSettings.state.hoogleResultCount): List<HoogleResult> {
        if (query.isBlank()) return emptyList()
        val settings = HaskellSettings.state
        return when (settings.hoogleMode) {
            HoogleMode.HTTP -> searchHttp(settings.hoogleUrl, query, count)
            HoogleMode.CLI -> searchCli(project, settings.hoogleCliPath, query, count)
        }
    }

    @Throws(IOException::class)
    fun searchHttp(baseUrl: String, query: String, count: Int): List<HoogleResult> {
        val text = HttpRequests.request(buildUrl(baseUrl, query, count))
            .accept("application/json")
            .connectTimeout(5_000)
            .readTimeout(15_000)
            .readString()
        return HoogleJsonParser.parse(text)
    }

    @Throws(IOException::class)
    fun searchCli(project: Project?, cli: String, query: String, count: Int): List<HoogleResult> {
        val exe = if (project != null) {
            HlsLocator.locate(project, if (cli.contains('/') || cli.contains('\\')) cli else "", listOf(cli.ifBlank { "hoogle" }))
                ?.exe?.toString()
        } else {
            null
        } ?: cli.ifBlank { "hoogle" }
        val cmd = GeneralCommandLine(exe, "search", "--json", "--count=$count", query)
            .withCharset(Charsets.UTF_8)
        project?.basePath?.let { cmd.withWorkingDirectory(java.nio.file.Path.of(it)) }
        val output = try {
            CapturingProcessHandler(cmd).runProcess(30_000)
        } catch (e: Exception) {
            throw IOException("Failed to run hoogle CLI: ${e.message}", e)
        }
        if (output.isTimeout) throw IOException("hoogle CLI timed out")
        if (output.exitCode != 0 && output.stdout.isBlank()) {
            throw IOException("hoogle exited with ${output.exitCode}: ${output.stderr.trim().take(500)}")
        }
        return HoogleJsonParser.parse(output.stdout)
    }

    /** Hackage のドキュメント URL (アンカー付き) */
    fun hackageDocUrl(packageId: String, module: String, name: String): String {
        val anchor = if (name.firstOrNull()?.isUpperCase() == true) "t" else "v"
        return "https://hackage.haskell.org/package/$packageId/docs/${module.replace('.', '-')}.html#$anchor:${haddockAnchor(name)}"
    }

    /** Haddock のアンカー名。英数字以外は `-<code>-` でエンコードされる (例: `<$>` -> `-60--36--62-`)。 */
    fun haddockAnchor(name: String): String {
        val n = name.removeSurrounding("(", ")")
        return buildString {
            for (c in n) {
                if (c.isLetterOrDigit() || c == '_' || c == '\'') append(c) else append('-').append(c.code).append('-')
            }
        }
    }
}
