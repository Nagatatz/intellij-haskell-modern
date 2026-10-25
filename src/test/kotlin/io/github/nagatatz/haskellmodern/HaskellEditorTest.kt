package io.github.nagatatz.haskellmodern

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.nagatatz.haskellmodern.cabal.CabalFileType
import io.github.nagatatz.haskellmodern.lang.HaskellFile
import io.github.nagatatz.haskellmodern.lang.HaskellFileType

/** IDE のテストフレームワーク上でファイルタイプ登録・PSI・コメント機能を確認する。 */
class HaskellEditorTest : BasePlatformTestCase() {

    fun testFileTypes() {
        val hs = myFixture.configureByText("Main.hs", "main = pure ()")
        assertEquals(HaskellFileType, hs.virtualFile.fileType)
        assertInstanceOf(hs, HaskellFile::class.java)
        assertEquals(HaskellFileType, myFixture.configureByText("A.hs-boot", "module A where").virtualFile.fileType)
        assertEquals(CabalFileType, myFixture.configureByText("demo.cabal", "name: demo").virtualFile.fileType)
        assertEquals(CabalFileType, myFixture.configureByText("cabal.project", "packages: .").virtualFile.fileType)
    }

    fun testLineComment() {
        myFixture.configureByText("Main.hs", "ma<caret>in = pure ()\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("-- main = pure ()\n")
    }

    fun testBlockComment() {
        myFixture.configureByText("Main.hs", "<selection>main = pure ()</selection>\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        myFixture.checkResult("{- main = pure () -}\n")
    }

    fun testCabalLineComment() {
        myFixture.configureByText("demo.cabal", "na<caret>me: demo\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("-- name: demo\n")
    }

    fun testBraceAutoClose() {
        myFixture.configureByText("Main.hs", "main = print <caret>\n")
        myFixture.type("(")
        myFixture.checkResult("main = print (<caret>)\n")
    }

    fun testHighlightingDoesNotCrash() {
        myFixture.configureByText(
            "Main.hs",
            "{-# LANGUAGE LambdaCase #-}\nmodule Main where\nimport Data.List (sortOn)\nmain :: IO ()\nmain = print \$ sortOn negate [3, 1, 2 :: Int]\n",
        )
        myFixture.doHighlighting()
    }
}
