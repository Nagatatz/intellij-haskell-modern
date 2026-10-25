package io.github.nagatatz.haskellmodern.settings

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.toNullableProperty

class HaskellSettingsConfigurable : BoundSearchableConfigurable("Haskell", "haskell.modern.settings") {

    private val settings get() = HaskellSettings.state

    override fun createPanel(): DialogPanel {
        val s = settings
        return panel {
            group("Haskell Language Server") {
                row("HLS executable:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.singleFile().withTitle("Select haskell-language-server-wrapper"),
                    ).bindText(s::hlsPath)
                        .align(AlignX.FILL)
                        .comment(
                            "Empty = auto-detect: <code>haskell-language-server-wrapper</code> on PATH, then " +
                                "<code>~/.ghcup/bin</code>. Detection runs inside the project environment " +
                                "(local, WSL or Dev Container).",
                        )
                }
                row("Extra arguments:") {
                    textField().bindText(s::hlsExtraArgs).columns(COLUMNS_LARGE)
                        .comment("Appended after <code>--lsp</code>, e.g. <code>--debug --logfile /tmp/hls.log</code>")
                }
                row("Formatter:") {
                    comboBox(HaskellFormatter.entries).bindItem(s::formatter.toNullableProperty())
                }
                row("Cabal formatter:") {
                    comboBox(CabalFormatter.entries).bindItem(s::cabalFormatter.toNullableProperty())
                }
                row {
                    checkBox("Enable hlint diagnostics and hints").bindSelected(s::hlintEnabled)
                }
                row {
                    checkBox("Enable semantic highlighting (HLS semantic tokens)").bindSelected(s::semanticTokensEnabled)
                }
                row("Extra HLS configuration (JSON):") {}
                row {
                    textArea().bindText(s::extraHlsConfigJson)
                        .rows(6)
                        .align(AlignX.FILL)
                        .comment(
                            "Deep-merged into the <code>haskell</code> section sent as initializationOptions and " +
                                "workspace/didChangeConfiguration. Example: " +
                                "<code>{\"plugin\": {\"eval\": {\"globalOn\": true}}, \"checkProject\": false}</code>",
                        )
                        .validationOnInput { area ->
                            val text = area.text.trim()
                            if (text.isEmpty()) return@validationOnInput null
                            try {
                                if (!JsonParser.parseString(text).isJsonObject) error("JSON object expected") else null
                            } catch (e: Exception) {
                                error("Invalid JSON: ${e.message}")
                            }
                        }
                }
            }
            group("Hoogle") {
                buttonsGroup {
                    row("Backend:") {
                        comboBox(HoogleMode.entries).bindItem(s::hoogleMode.toNullableProperty())
                    }
                }
                row("Hoogle URL:") {
                    textField().bindText(s::hoogleUrl).columns(COLUMNS_LARGE)
                        .comment("Default <code>https://hoogle.haskell.org/</code>. For a local index run <code>hoogle server --local -p 8080</code> and use <code>http://localhost:8080/</code>.")
                }
                row("hoogle CLI:") {
                    textField().bindText(s::hoogleCliPath).columns(COLUMNS_LARGE)
                        .comment("Used when backend is CLI; runs <code>hoogle search --json</code> in the project environment.")
                }
                row("Max results:") {
                    intTextField(1..500).bindIntText(s::hoogleResultCount)
                }
                row {
                    checkBox("Quick Documentation: fall back to Hoogle when HLS hover has no Haddock")
                        .bindSelected(s::hoogleQuickDocFallback)
                }
            }
        }
    }

    override fun apply() {
        val before = snapshot(settings)
        super.apply()
        val after = snapshot(settings)
        if (before != after) {
            // 実行ファイル・引数・semantic tokens (クライアント機能) の変更はプロセス再起動が必要
            val restart = before[0] != after[0] || before[1] != after[1] || before[5] != after[5]
            ApplicationManager.getApplication().messageBus
                .syncPublisher(HaskellSettingsListener.TOPIC)
                .settingsChanged(restart)
        }
    }

    private fun snapshot(s: HaskellSettings.State): List<Any> = listOf(
        s.hlsPath, s.hlsExtraArgs, s.formatter, s.cabalFormatter, s.hlintEnabled, s.semanticTokensEnabled,
        s.extraHlsConfigJson, s.hoogleMode, s.hoogleUrl, s.hoogleCliPath, s.hoogleResultCount, s.hoogleQuickDocFallback,
    )
}
