package io.github.nagatatz.haskellmodern.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import io.github.nagatatz.haskellmodern.HaskellIcons
import io.github.nagatatz.haskellmodern.settings.HaskellSettingsConfigurable
import io.github.nagatatz.haskellmodern.settings.HaskellSettingsListener
import org.eclipse.lsp4j.DidChangeConfigurationParams

class HlsIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (HlsClientDescriptor.isHlsFile(file)) {
            clientStarter.ensureClientStarted(HlsClientDescriptor(project))
        }
    }

    override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
        LspClientWidgetItem(lspClient, currentFile, HaskellIcons.FILE, HaskellSettingsConfigurable::class.java)

    companion object {
        fun clients(project: Project): Collection<LspClient> =
            LspClientManager.getInstance(project).getClients(HlsIntegrationProvider::class.java)

        fun runningClient(project: Project): LspClient? =
            clients(project).firstOrNull { it.state == LspServerState.Running }

        fun restart(project: Project) {
            LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(HlsIntegrationProvider::class.java)
        }
    }
}

/** 設定変更を開いている全プロジェクトの HLS へ反映する。 */
class HlsSettingsListener : HaskellSettingsListener {
    override fun settingsChanged(restartRequired: Boolean) {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            if (restartRequired) {
                HlsIntegrationProvider.restart(project)
            } else {
                val settings = HlsConfig.fullSettings()
                for (client in HlsIntegrationProvider.clients(project)) {
                    if (client.state != LspServerState.Running) continue
                    client.sendNotification { server ->
                        server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(settings))
                    }
                }
            }
        }
    }
}

class RestartHlsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { HlsIntegrationProvider.restart(it) }
    }
}
