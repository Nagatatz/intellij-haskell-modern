package io.github.nagatatz.haskellmodern.lang

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.BracePair
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import io.github.nagatatz.haskellmodern.lang.HaskellTokenTypes as T

class HaskellCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix(): String = "-- "
    override fun getBlockCommentPrefix(): String = "{- "
    override fun getBlockCommentSuffix(): String = " -}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
    override fun getLineCommentTokenType(): IElementType = T.LINE_COMMENT
    override fun getBlockCommentTokenType(): IElementType = T.BLOCK_COMMENT
    override fun getDocumentationCommentTokenType(): IElementType = T.DOC_COMMENT
    override fun getDocumentationCommentPrefix(): String = "-- |"
    override fun getDocumentationCommentLinePrefix(): String = "--"
    override fun getDocumentationCommentSuffix(): String? = null
    override fun isDocumentationComment(element: PsiComment?): Boolean =
        element?.node?.elementType == T.DOC_COMMENT
}

class HaskellBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(T.LPAREN, T.RPAREN, false),
            BracePair(T.LBRACKET, T.RBRACKET, false),
            BracePair(T.LBRACE, T.RBRACE, true),
        )
    }
}

class HaskellQuoteHandler : SimpleTokenSetQuoteHandler(T.STRING)
