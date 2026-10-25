package io.github.nagatatz.haskellmodern.lsp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.nagatatz.haskellmodern.settings.HaskellSettings

/**
 * HLS へ送る設定 (`haskell` セクション) を組み立てる。
 *
 * - initializationOptions: `{"haskell": {...}}`
 * - workspace/configuration (section = "haskell"): `{...}`
 * - workspace/didChangeConfiguration: `{"haskell": {...}}`
 */
object HlsConfig {

    fun haskellSection(state: HaskellSettings.State = HaskellSettings.state): JsonObject {
        val root = JsonObject()
        root.addProperty("formattingProvider", state.formatter.hlsValue)
        root.addProperty("cabalFormattingProvider", state.cabalFormatter.hlsValue)
        val plugin = JsonObject()
        plugin.add("hlint", JsonObject().apply {
            addProperty("globalOn", state.hlintEnabled)
            addProperty("diagnosticsOn", state.hlintEnabled)
            addProperty("codeActionsOn", state.hlintEnabled)
        })
        plugin.add("semanticTokens", JsonObject().apply { addProperty("globalOn", state.semanticTokensEnabled) })
        root.add("plugin", plugin)

        parseExtra(state.extraHlsConfigJson)?.let { deepMerge(root, it) }
        return root
    }

    fun fullSettings(state: HaskellSettings.State = HaskellSettings.state): JsonObject =
        JsonObject().apply { add("haskell", haskellSection(state)) }

    /**
     * 追加 JSON を解釈する。トップレベルに `haskell` キーだけがあればその中身を使う。
     * 不正な JSON は無視 (null)。
     */
    fun parseExtra(text: String?): JsonObject? {
        if (text.isNullOrBlank()) return null
        val parsed = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return null
        }
        if (!parsed.isJsonObject) return null
        val obj = parsed.asJsonObject
        val inner = obj.get("haskell")
        return if (obj.size() == 1 && inner != null && inner.isJsonObject) inner.asJsonObject else obj
    }

    /** [overlay] を [base] へ再帰的にマージする (オブジェクト同士は再帰、それ以外は上書き)。 */
    fun deepMerge(base: JsonObject, overlay: JsonObject): JsonObject {
        for ((key, value) in overlay.entrySet()) {
            val existing: JsonElement? = base.get(key)
            if (existing != null && existing.isJsonObject && value.isJsonObject) {
                deepMerge(existing.asJsonObject, value.asJsonObject)
            } else {
                base.add(key, value.deepCopy())
            }
        }
        return base
    }
}
