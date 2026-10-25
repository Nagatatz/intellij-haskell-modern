package io.github.nagatatz.haskellmodern.hoogle

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction

/** 選択範囲、またはキャレット位置の識別子 / 演算子で Hoogle を検索する (既定: Alt+Shift+H)。 */
class SearchHoogleAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val query = editor?.let { ed ->
            val selection = ed.selectionModel.selectedText
            if (!selection.isNullOrBlank()) {
                selection.trim().replace(Regex("\\s+"), " ")
            } else {
                HoogleQuery.atOffset(ed.document.charsSequence, ed.caretModel.offset)
            }
        }
        HoogleToolWindowFactory.search(project, query)
    }
}
