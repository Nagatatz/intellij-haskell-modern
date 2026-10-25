package io.github.nagatatz.haskellmodern.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.EelProviderUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * プロジェクトの実行環境 (ローカル / WSL / Dev Container) で実行ファイルを探す。
 * 返す [Located.exe] は IDE 側から見た NIO パス (リモートの場合は routed path)、
 * [Located.targetPath] はターゲット環境内でのパス文字列。
 */
object HlsLocator {
    private val LOG = logger<HlsLocator>()

    val HLS_NAMES = listOf("haskell-language-server-wrapper", "haskell-language-server")

    data class Located(val exe: Path, val targetPath: String, val fromGhcup: Boolean)

    fun descriptor(project: Project): EelDescriptor = EelProviderUtil.getEelDescriptor(project)

    fun isLocal(project: Project): Boolean = descriptor(project) == LocalEelDescriptor

    fun locateHls(project: Project, configured: String): Located? =
        locate(project, configured, HLS_NAMES)

    fun locate(project: Project, configured: String, names: List<String>): Located? {
        val descriptor = descriptor(project)
        val eel = try {
            EelProviderUtil.toEelApiBlocking(descriptor)
        } catch (e: Exception) {
            LOG.warn("Cannot obtain EelApi for $descriptor", e)
            null
        }
        if (configured.isNotBlank()) {
            return toLocated(configured.trim(), descriptor, fromGhcup = false)
        }
        if (eel == null) return null
        for (name in names) {
            val found = try {
                runBlockingMaybeCancellable { eel.exec.findExeFilesInPath(name) }
            } catch (e: Exception) {
                LOG.info("findExeFilesInPath($name) failed", e)
                emptyList()
            }
            found.firstOrNull()?.let { p ->
                return Located(p.asNioPath(), p.toString(), fromGhcup = false)
            }
        }
        ghcupBin(eel)?.let { bin ->
            for (name in names) {
                for (candidate in listOf(name, "$name.exe")) {
                    val p = try {
                        bin.resolve(candidate)
                    } catch (_: Exception) {
                        continue
                    }
                    val nio = p.asNioPath()
                    if (Files.isRegularFile(nio)) return Located(nio, p.toString(), fromGhcup = true)
                }
            }
        }
        return null
    }

    /** ~/.ghcup/bin (Windows の場合 C:\ghcup\bin も) */
    fun ghcupBin(eel: EelApi): EelPath? {
        val candidates = buildList {
            try {
                add(eel.userInfo.home.resolve(".ghcup").resolve("bin"))
            } catch (_: Exception) {
            }
            if (eel.descriptor.osFamily == com.intellij.platform.eel.EelOsFamily.Windows) {
                try {
                    add(EelPath.parse("C:\\ghcup\\bin", eel.descriptor))
                } catch (_: Exception) {
                }
            }
        }
        return candidates.firstOrNull { Files.isDirectory(it.asNioPath()) }
    }

    private fun toLocated(path: String, descriptor: EelDescriptor, fromGhcup: Boolean): Located? {
        if (descriptor == LocalEelDescriptor) {
            val p = Path.of(path)
            return Located(p, path, fromGhcup)
        }
        // ターゲット側パス (例: /home/vscode/.ghcup/bin/...) を routed path に変換
        return try {
            val eelPath = EelPath.parse(path, descriptor)
            Located(eelPath.asNioPath(), eelPath.toString(), fromGhcup)
        } catch (_: Exception) {
            // 既に IDE 側の routed path (\\wsl$\..., /docker-xxx/...) が入力されている場合
            try {
                Located(Path.of(path), path, fromGhcup)
            } catch (_: Exception) {
                null
            }
        }
    }
}
