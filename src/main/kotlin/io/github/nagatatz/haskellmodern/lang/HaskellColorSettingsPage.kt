package io.github.nagatatz.haskellmodern.lang

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.nagatatz.haskellmodern.HaskellIcons
import javax.swing.Icon

class HaskellColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = HaskellIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = HaskellSyntaxHighlighter()
    override fun getDisplayName(): String = "Haskell"
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Comments//Line comment", HaskellColors.LINE_COMMENT),
        AttributesDescriptor("Comments//Block comment", HaskellColors.BLOCK_COMMENT),
        AttributesDescriptor("Comments//Haddock comment", HaskellColors.DOC_COMMENT),
        AttributesDescriptor("Pragma", HaskellColors.PRAGMA),
        AttributesDescriptor("CPP / hsc2hs directive", HaskellColors.CPP),
        AttributesDescriptor("Keyword", HaskellColors.KEYWORD),
        AttributesDescriptor("Reserved operator", HaskellColors.RESERVED_OP),
        AttributesDescriptor("Identifiers//Variable", HaskellColors.VARID),
        AttributesDescriptor("Identifiers//Top-level declaration", HaskellColors.TOP_DECL),
        AttributesDescriptor("Identifiers//Type / constructor / module", HaskellColors.CONID),
        AttributesDescriptor("Identifiers//Qualified variable", HaskellColors.QVARID),
        AttributesDescriptor("Operators//Variable operator", HaskellColors.VARSYM),
        AttributesDescriptor("Operators//Constructor operator", HaskellColors.CONSYM),
        AttributesDescriptor("Operators//Backquote", HaskellColors.BACKQUOTE),
        AttributesDescriptor("Promotion / TH quote tick", HaskellColors.TICK),
        AttributesDescriptor("Literals//String", HaskellColors.STRING),
        AttributesDescriptor("Literals//Char", HaskellColors.CHAR),
        AttributesDescriptor("Literals//Number", HaskellColors.NUMBER),
        AttributesDescriptor("Braces//Parentheses", HaskellColors.PARENS),
        AttributesDescriptor("Braces//Brackets", HaskellColors.BRACKETS),
        AttributesDescriptor("Braces//Braces", HaskellColors.BRACES),
        AttributesDescriptor("Comma", HaskellColors.COMMA),
        AttributesDescriptor("Semicolon", HaskellColors.SEMICOLON),
        AttributesDescriptor("Bad character", HaskellColors.BAD_CHARACTER),
    )

    override fun getDemoText(): String = """
{-# LANGUAGE OverloadedStrings #-}
#if MIN_VERSION_base(4,18,0)
{- block comment {- nested -} -}
#endif
-- | Haddock comment for the module
module Main (main) where

import qualified Data.Map.Strict as Map
import Data.List (foldl')

data Shape = Circle Double | Rect { w :: Double, h :: Double }
  deriving (Show, Eq)

area :: Shape -> Double
area (Circle r) = pi * r ^ (2 :: Int)
area Rect {..}  = w * h

main :: IO ()
main = do
  let m = Map.fromList [(1 :: Int, 'a'), (2, '\n')]
      xs = 0x1F : 1_000 : [] -- line comment
  print (Map.lookup 1 m, foldl' (+) 0 xs, 1.5e3 `div'` 2)
  putStrLn "hello\tworld"
""".trimIndent()
}
