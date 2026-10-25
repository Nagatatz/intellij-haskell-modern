package io.github.nagatatz.haskellmodern.lsp

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.github.nagatatz.haskellmodern.HaskellIcons
import io.github.nagatatz.haskellmodern.hoogle.HoogleClient
import io.github.nagatatz.haskellmodern.hoogle.HoogleQuery
import io.github.nagatatz.haskellmodern.hoogle.HoogleResult
import io.github.nagatatz.haskellmodern.hoogle.HtmlText
import io.github.nagatatz.haskellmodern.lang.HaskellFile
import io.github.nagatatz.haskellmodern.settings.HaskellSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position

/**
 * Quick Documentation (Ctrl+Q / F1 / マウスホバー)。
 * HLS の hover を表示し、Haddock 本文が無い場合は Hoogle で補完する。HLS 未起動時は Hoogle のみ。
 */
class HaskellDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file !is HaskellFile) return emptyList()
        val vFile = file.virtualFile ?: file.originalFile.virtualFile ?: return emptyList()
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
        if (offset < 0 || offset > document.textLength) return emptyList()
        val name = HoogleQuery.atOffset(document.charsSequence, offset) ?: return emptyList()
        val line = document.getLineNumber(offset)
        val character = offset - document.getLineStartOffset(line)
        return listOf(HaskellDocumentationTarget(file.project, vFile, line, character, name))
    }
}

class HaskellDocumentationTarget(
    private val project: Project,
    private val file: VirtualFile,
    private val line: Int,
    private val character: Int,
    private val name: String,
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(name).icon(HaskellIcons.FILE).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
        val html = withContext(Dispatchers.IO) { HaskellDocumentationBuilder.build(project, file, line, character, name) }
        html?.let { DocumentationResult.documentation(it) }
    }
}

object HaskellDocumentationBuilder {
    private val LOG = logger<HaskellDocumentationBuilder>()

    suspend fun build(project: Project, file: VirtualFile, line: Int, character: Int, name: String): String? {
        val hoverMarkdown = requestHover(project, file, line, character)
        val info = hoverMarkdown?.let { HoverInfo.parse(it) }
        val settings = HaskellSettings.state

        val hoogle: HoogleResult? = if (settings.hoogleQuickDocFallback && (info == null || !info.hasDocs)) {
            lookupHoogle(project, info, name)
        } else {
            null
        }
        if (info == null && hoogle == null) return null
        return render(info, hoogle, name)
    }

    private suspend fun requestHover(project: Project, file: VirtualFile, line: Int, character: Int): String? {
        val client = HlsIntegrationProvider.runningClient(project) ?: return null
        val hover: Hover? = try {
            withTimeoutOrNull(5_000) {
                client.sendRequest { server ->
                    server.textDocumentService.hover(HoverParams(client.getDocumentIdentifier(file), Position(line, character)))
                }
            }
        } catch (e: Exception) {
            LOG.debug("HLS hover failed", e)
            null
        }
        return hover?.let { hoverToMarkdown(it) }?.takeIf { it.isNotBlank() }
    }

    fun hoverToMarkdown(hover: Hover): String {
        val contents = hover.contents ?: return ""
        return if (contents.isRight) {
            val mc: MarkupContent = contents.right
            mc.value ?: ""
        } else {
            contents.left.joinToString("\n\n") { e ->
                if (e.isLeft) e.left else "```${e.right.language ?: "haskell"}\n${e.right.value}\n```"
            }
        }
    }

    private fun lookupHoogle(project: Project, info: HoverInfo?, name: String): HoogleResult? {
        val bare = HoogleQuery.unqualified(name) ?: name
        val query = buildString {
            append(bare)
            info?.packageName?.let { append(" +").append(it) }
        }
        val results = try {
            HoogleClient.search(project, query, 20)
        } catch (e: Exception) {
            LOG.debug("Hoogle lookup failed for '$query'", e)
            return null
        }
        return pickBest(results, bare, info?.definedInModule)
    }

    fun pickBest(results: List<HoogleResult>, name: String, module: String?): HoogleResult? {
        val target = name.removeSurrounding("(", ")")
        val sameName = results.filter { it.name.removeSurrounding("(", ")") == target }
        return sameName.firstOrNull { module != null && it.moduleName == module }
            ?: sameName.firstOrNull { it.docs.isNotBlank() }
            ?: sameName.firstOrNull()
    }

    fun render(info: HoverInfo?, hoogle: HoogleResult?, name: String): String {
        val sb = StringBuilder()
        val signatures = info?.signatures.orEmpty().ifEmpty { listOfNotNull(hoogle?.item?.takeIf { it.isNotBlank() }) }
        if (signatures.isNotEmpty()) {
            sb.append(DocumentationMarkup.DEFINITION_START)
            sb.append(signatures.joinToString("\n\n") { HtmlText.escape(it) })
            sb.append(DocumentationMarkup.DEFINITION_END)
        }
        sb.append(DocumentationMarkup.CONTENT_START)
        when {
            info != null && info.hasDocs -> sb.append(MarkdownHtml.toHtml(info.docMarkdown))
            hoogle != null && hoogle.docs.isNotBlank() -> {
                sb.append(plainDocsToHtml(hoogle.docs))
                sb.append("<p><i>Documentation from Hoogle</i></p>")
            }
            else -> sb.append("<p><i>No documentation</i></p>")
        }
        sb.append(DocumentationMarkup.CONTENT_END)

        sb.append(DocumentationMarkup.SECTIONS_START)
        val module = info?.definedInModule ?: hoogle?.moduleName
        val pkg = info?.definedInPackageId ?: hoogle?.packageName
        if (module != null) section(sb, "Module:", "<code>${HtmlText.escape(module)}</code>")
        if (pkg != null) section(sb, "Package:", "<code>${HtmlText.escape(pkg)}</code>")
        val links = mutableListOf<String>()
        val hackage = when {
            hoogle != null && hoogle.url.isNotBlank() -> hoogle.url
            info?.definedInModule != null && info.definedInPackageId != null ->
                HoogleClient.hackageDocUrl(info.definedInPackageId, info.definedInModule, HoogleQuery.unqualified(name) ?: name)
            else -> null
        }
        hackage?.let { links.add("<a href=\"${HtmlText.escape(it)}\">Hackage</a>") }
        val hoogleSearch = HoogleClient.buildUrl(HaskellSettings.state.hoogleUrl, HoogleQuery.unqualified(name) ?: name, 30)
            .replace("mode=json&format=text&", "")
        links.add("<a href=\"${HtmlText.escape(hoogleSearch)}\">Hoogle</a>")
        section(sb, "Links:", links.joinToString(" · "))
        sb.append(DocumentationMarkup.SECTIONS_END)
        return sb.toString()
    }

    private fun section(sb: StringBuilder, title: String, html: String) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START).append(title)
            .append(DocumentationMarkup.SECTION_SEPARATOR).append(html)
            .append(DocumentationMarkup.SECTION_END)
    }

    fun plainDocsToHtml(docs: String): String =
        docs.split(Regex("\n\\s*\n")).joinToString("") { para ->
            val lines = para.lines()
            if (lines.all { it.startsWith(">") || it.startsWith("  ") || it.isBlank() }) {
                "<pre><code>" + HtmlText.escape(para.trimEnd()) + "</code></pre>"
            } else {
                "<p>" + HtmlText.escape(para.trim()).replace("\n", "<br/>") + "</p>"
            }
        }
}
