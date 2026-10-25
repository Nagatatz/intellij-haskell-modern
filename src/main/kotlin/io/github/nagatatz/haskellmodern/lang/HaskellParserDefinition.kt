package io.github.nagatatz.haskellmodern.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class HaskellFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, HaskellLanguage) {
    override fun getFileType(): FileType = HaskellFileType
    override fun toString(): String = "Haskell File"
}

/**
 * フラットな PSI (トークン列のみ)。構文解析・名前解決・参照は HLS (LSP) が担当する。
 * コメント化・括弧対応・単語選択などトークン単位のエディタ機能のために最小限の PSI を提供する。
 */
class HaskellParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = HaskellLexer()
    override fun createParser(project: Project?): PsiParser = FlatParser
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = HaskellTokenTypes.COMMENTS
    override fun getStringLiteralElements(): TokenSet = HaskellTokenTypes.STRINGS
    override fun getWhitespaceTokens(): TokenSet = HaskellTokenTypes.WHITESPACES
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = HaskellFile(viewProvider)

    companion object {
        val FILE = IFileElementType(HaskellLanguage)
    }
}

/** すべてのトークンをルート直下に並べるだけのパーサ。Haskell / Cabal 共用。 */
object FlatParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        return builder.treeBuilt
    }
}
