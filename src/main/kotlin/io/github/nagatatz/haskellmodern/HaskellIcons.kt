package io.github.nagatatz.haskellmodern

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object HaskellIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/haskell.svg", HaskellIcons::class.java)

    @JvmField
    val CABAL: Icon = IconLoader.getIcon("/icons/cabal.svg", HaskellIcons::class.java)
}
