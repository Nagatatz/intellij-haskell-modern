package io.github.nagatatz.haskellmodern.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil

enum class HaskellFormatter(val hlsValue: String, val label: String) {
    ORMOLU("ormolu", "ormolu"),
    FOURMOLU("fourmolu", "fourmolu"),
    STYLISH_HASKELL("stylish-haskell", "stylish-haskell"),
    NONE("none", "None (disable formatting)"),
    ;

    override fun toString(): String = label
}

enum class CabalFormatter(val hlsValue: String, val label: String) {
    CABAL_GILD("cabal-gild", "cabal-gild"),
    CABAL_FMT("cabal-fmt", "cabal-fmt"),
    NONE("none", "None"),
    ;

    override fun toString(): String = label
}

enum class HoogleMode(val label: String) {
    /** hoogle.haskell.org またはローカル `hoogle server` の HTTP JSON API */
    HTTP("HTTP JSON API (hoogle.haskell.org / local hoogle server)"),
    /** プロジェクト環境 (ローカル/WSL/Dev Container) 内の `hoogle search --json` */
    CLI("hoogle CLI (hoogle search --json)"),
    ;

    override fun toString(): String = label
}

/** アプリケーション全体の設定。将来プロジェクト単位の上書きを追加予定 (Phase 2)。 */
@Service(Service.Level.APP)
@State(name = "HaskellModernSettings", storages = [Storage("haskell-modern.xml")])
class HaskellSettings : PersistentStateComponent<HaskellSettings.State> {

    class State {
        /** 空なら自動検出 (PATH → ~/.ghcup/bin) */
        var hlsPath: String = ""
        var hlsExtraArgs: String = ""
        var formatter: HaskellFormatter = HaskellFormatter.ORMOLU
        var cabalFormatter: CabalFormatter = CabalFormatter.CABAL_GILD
        var hlintEnabled: Boolean = true
        var semanticTokensEnabled: Boolean = true
        /** HLS の `haskell` セクションへディープマージされる JSON */
        var extraHlsConfigJson: String = ""

        var hoogleMode: HoogleMode = HoogleMode.HTTP
        var hoogleUrl: String = DEFAULT_HOOGLE_URL
        var hoogleCliPath: String = "hoogle"
        var hoogleResultCount: Int = 30
        var hoogleQuickDocFallback: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        const val DEFAULT_HOOGLE_URL = "https://hoogle.haskell.org/"

        @JvmStatic
        fun getInstance(): HaskellSettings = ApplicationManager.getApplication().getService(HaskellSettings::class.java)

        val state: State get() = getInstance().state
    }
}

/** 設定変更通知。LSP 側はこれを購読して HLS を再起動 / didChangeConfiguration を送る。 */
fun interface HaskellSettingsListener {
    /** @param restartRequired HLS 実行ファイルや引数が変わり、プロセスの再起動が必要な場合 true */
    fun settingsChanged(restartRequired: Boolean)

    companion object {
        @JvmField
        val TOPIC: Topic<HaskellSettingsListener> =
            Topic.create("Haskell Modern settings", HaskellSettingsListener::class.java)
    }
}
