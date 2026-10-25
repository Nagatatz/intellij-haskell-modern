package io.github.nagatatz.haskellmodern.hoogle

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import io.github.nagatatz.haskellmodern.settings.HaskellSettings
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.DefaultListModel
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent

class HoogleToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = HooglePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        content.preferredFocusableComponent = panel.searchField.textEditor
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val ID = "Hoogle"

        /** ツールウィンドウを開いて [query] を検索する */
        fun search(project: Project, query: String?) {
            val tw = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
            tw.activate({
                val panel = tw.contentManager.contents.firstOrNull()?.component as? HooglePanel ?: return@activate
                if (!query.isNullOrBlank()) panel.searchNow(query) else panel.focusSearch()
            }, true, true)
        }
    }
}

class HooglePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    val searchField = SearchTextField(true, "HoogleSearchHistory")
    private val model = DefaultListModel<HoogleResult>()
    private val list = JBList(model)
    private val details = JEditorPane().apply {
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        isEditable = false
        border = JBUI.Borders.empty(8)
        addHyperlinkListener { e -> if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) e.url?.let { BrowserUtil.browse(it) } ?: e.description?.let { BrowserUtil.browse(it) } }
    }
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val generation = AtomicInteger()

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.emptyText.text = "Type a name or a type signature, e.g. (a -> b) -> [a] -> [b]"
        list.cellRenderer = object : ColoredListCellRenderer<HoogleResult>() {
            override fun customizeCellRenderer(
                list: JList<out HoogleResult>, value: HoogleResult?, index: Int, selected: Boolean, hasFocus: Boolean,
            ) {
                if (value == null) return
                append(value.item, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                if (value.location.isNotEmpty()) append("   " + value.location, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                toolTipText = value.url
            }
        }
        list.addListSelectionListener { showDetails(list.selectedValue) }
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                openSelected(); return true
            }
        }.installOn(list)
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) { openSelected(); e.consume() }
            }
        })

        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> { searchNow(searchField.text); e.consume() }
                    KeyEvent.VK_DOWN -> if (model.size() > 0) {
                        list.requestFocusInWindow()
                        if (list.selectedIndex < 0) list.selectedIndex = 0
                        e.consume()
                    }
                }
            }
        })
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                alarm.cancelAllRequests()
                val text = searchField.text
                if (text.isNotBlank()) alarm.addRequest({ runSearch(text) }, 450)
            }
        })

        val splitter = OnePixelSplitter(true, 0.6f)
        splitter.firstComponent = ScrollPaneFactory.createScrollPane(list, true)
        splitter.secondComponent = ScrollPaneFactory.createScrollPane(details, true)
        add(searchField, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
    }

    fun focusSearch() {
        searchField.requestFocusInWindow()
    }

    fun searchNow(query: String) {
        alarm.cancelAllRequests()
        if (searchField.text != query) {
            searchField.text = query // DocumentListener によるデバウンス検索はキャンセルする
            alarm.cancelAllRequests()
        }
        searchField.addCurrentTextToHistory()
        ApplicationManager.getApplication().executeOnPooledThread { runSearch(query) }
    }

    private fun runSearch(query: String) {
        val gen = generation.incrementAndGet()
        ApplicationManager.getApplication().invokeLater({
            if (gen == generation.get()) list.setPaintBusy(true)
        }, ModalityState.any())
        var error: String? = null
        var results = try {
            HoogleClient.search(project, query)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            emptyList()
        }
        // `M.insert` のようなエイリアス修飾で 0 件なら非修飾名で再検索
        if (error == null && results.isEmpty()) {
            HoogleQuery.unqualified(query)?.let { bare ->
                results = runCatching { HoogleClient.search(project, bare) }.getOrDefault(emptyList())
            }
        }
        ApplicationManager.getApplication().invokeLater({
            if (gen != generation.get() || project.isDisposed) return@invokeLater
            list.setPaintBusy(false)
            model.clear()
            results.forEach(model::addElement)
            list.emptyText.text = when {
                error != null -> "Hoogle error: $error"
                else -> "No results for \"$query\" (${HaskellSettings.state.hoogleMode.name})"
            }
            if (model.size() > 0) list.selectedIndex = 0 else showDetails(null)
        }, ModalityState.any())
    }

    private fun showDetails(r: HoogleResult?) {
        details.text = if (r == null) "" else renderDetails(r)
        details.caretPosition = 0
    }

    private fun openSelected() {
        val r = list.selectedValue ?: return
        val url = r.url.ifBlank { r.moduleUrl ?: r.packageUrl ?: return }
        BrowserUtil.browse(url)
    }

    override fun dispose() = Unit

    companion object {
        fun renderDetails(r: HoogleResult): String = buildString {
            append("<html><body>")
            append("<pre><b>").append(HtmlText.escape(r.item)).append("</b></pre>")
            val pkg = r.packageName
            val mod = r.moduleName
            if (pkg != null) {
                append("<p>Package: ")
                if (r.packageUrl != null) append("<a href=\"${HtmlText.escape(r.packageUrl)}\">${HtmlText.escape(pkg)}</a>") else append(HtmlText.escape(pkg))
                if (mod != null) {
                    append(" &nbsp; Module: ")
                    if (r.moduleUrl != null) append("<a href=\"${HtmlText.escape(r.moduleUrl)}\">${HtmlText.escape(mod)}</a>") else append(HtmlText.escape(mod))
                }
                append("</p>")
            }
            if (r.docs.isNotBlank()) {
                append(io.github.nagatatz.haskellmodern.lsp.HaskellDocumentationBuilder.plainDocsToHtml(r.docs))
            }
            if (r.url.isNotBlank()) append("<p><a href=\"${HtmlText.escape(r.url)}\">Open on Hackage</a></p>")
            append("</body></html>")
        }
    }
}
