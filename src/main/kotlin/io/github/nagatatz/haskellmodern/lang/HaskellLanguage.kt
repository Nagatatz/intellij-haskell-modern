package io.github.nagatatz.haskellmodern.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import io.github.nagatatz.haskellmodern.HaskellIcons
import javax.swing.Icon

object HaskellLanguage : Language("Haskell") {
    private fun readResolve(): Any = HaskellLanguage
    override fun getDisplayName(): String = "Haskell"
    override fun isCaseSensitive(): Boolean = true
}

/** `.hs`, `.hs-boot`, `.hsig`, `.hsc` (hsc2hs) を扱う。`.lhs` は Phase 2 で専用 Lexer を追加予定。 */
object HaskellFileType : LanguageFileType(HaskellLanguage) {
    override fun getName(): String = "Haskell"
    override fun getDescription(): String = "Haskell source file"
    override fun getDefaultExtension(): String = "hs"
    override fun getIcon(): Icon = HaskellIcons.FILE
}
