package io.github.nagatatz.haskellmodern.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.util.execution.ParametersListUtil
import io.github.nagatatz.haskellmodern.cabal.CabalFileType
import io.github.nagatatz.haskellmodern.lang.HaskellFileType
import io.github.nagatatz.haskellmodern.settings.HaskellSettings
import io.github.nagatatz.haskellmodern.settings.HaskellSettingsConfigurable
import org.eclipse.lsp4j.ConfigurationItem

class HlsClientDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Haskell Language Server") {

    private val eelDescriptor = EelProviderUtil.getEelDescriptor(project)
    private val isRemote get() = eelDescriptor != LocalEelDescriptor

    override fun isSupportedFile(file: VirtualFile): Boolean = isHlsFile(file)

    override fun getLanguageId(file: VirtualFile): String =
        if (file.fileType == CabalFileType) "cabal" else "haskell"

    override fun createCommandLine(): GeneralCommandLine {
        val settings = HaskellSettings.state
        val located = HlsLocator.locateHls(project, settings.hlsPath)
        if (located == null) {
            notifyNotFound(project)
            throw ExecutionException(
                "haskell-language-server-wrapper not found on PATH or in ~/.ghcup/bin. " +
                    "Install it with ghcup or set the path in Settings | Languages & Frameworks | Haskell.",
            )
        }
        LOG.info("Starting HLS: ${located.targetPath} (remote=$isRemote)")
        val cmd = GeneralCommandLine(located.exe.toString())
            .withParameters("--lsp")
            .withParameters(ParametersListUtil.parse(settings.hlsExtraArgs))
            .withCharset(Charsets.UTF_8)
        project.basePath?.let { cmd.withWorkingDirectory(java.nio.file.Path.of(it)) }
        if (located.fromGhcup) {
            // GUI から起動した IDE / コンテナで PATH に ghcup が無い場合、ghc や cabal も見つからないため補う
            val sep = if (eelDescriptor.osFamily == com.intellij.platform.eel.EelOsFamily.Windows) ";" else ":"
            val binDir = located.targetPath.substringBeforeLast('/').substringBeforeLast('\\')
            val basePath = loginShellPath()
            cmd.withEnvironment("PATH", if (basePath.isNullOrEmpty()) binDir else "$binDir$sep$basePath")
        }
        return cmd
    }

    private fun loginShellPath(): String? = try {
        val eel = EelProviderUtil.toEelApiBlocking(eelDescriptor)
        runBlockingMaybeCancellable { eel.exec.fetchLoginShellEnvVariables() }["PATH"]
    } catch (e: Exception) {
        if (isRemote) null else System.getenv("PATH")
    }

    // ---- Dev Container / WSL 対応: IDE 側 (routed) パス <-> ターゲット側パス ----

    override fun getFilePath(file: VirtualFile): String {
        if (!isRemote) return super.getFilePath(file)
        val nio = file.toNioPathOrNull() ?: return super.getFilePath(file)
        return try {
            nio.asEelPath().toString()
        } catch (e: Exception) {
            super.getFilePath(file)
        }
    }

    override fun findLocalFileByPath(path: String): VirtualFile? {
        if (!isRemote) return super.findLocalFileByPath(path)
        return try {
            val nio = EelPath.parse(path, eelDescriptor).asNioPath()
            VirtualFileManager.getInstance().findFileByNioPath(nio)
        } catch (e: Exception) {
            super.findLocalFileByPath(path)
        }
    }

    // ---- 設定 ----

    override fun createInitializationOptions(): Any = HlsConfig.fullSettings()

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? = when (item.section) {
        "haskell" -> HlsConfig.haskellSection()
        null, "" -> HlsConfig.fullSettings()
        else -> null
    }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        // Hover は HaskellDocumentationTargetProvider が自前で処理し、Haddock が無い場合は Hoogle で補完する
        override val hoverCustomizer: LspHoverCustomizer = LspHoverDisabled

        override val semanticTokensCustomizer: LspSemanticTokensCustomizer =
            if (HaskellSettings.state.semanticTokensEnabled) LspSemanticTokensSupport() else LspSemanticTokensDisabled
    }

    companion object {
        private val LOG = logger<HlsClientDescriptor>()

        fun isHlsFile(file: VirtualFile): Boolean =
            file.fileType == HaskellFileType || (file.fileType == CabalFileType && file.extension == "cabal")

        fun notifyNotFound(project: Project) {
            NotificationGroupManager.getInstance().getNotificationGroup("Haskell")
                .createNotification(
                    "Haskell Language Server not found",
                    "Install HLS via <code>ghcup install hls</code> (inside the Dev Container if you use one), " +
                        "or configure the executable path.",
                    NotificationType.WARNING,
                )
                .addAction(NotificationAction.createSimpleExpiring("Open settings") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, HaskellSettingsConfigurable::class.java)
                })
                .notify(project)
        }
    }
}
