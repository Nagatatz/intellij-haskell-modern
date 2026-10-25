# Haskell Modern (IntelliJ plugin)

<!-- Plugin description -->
Modern Haskell support for IntelliJ IDEA, inspired by the archived IntelliJ-Haskell plugin and rebuilt on today's tooling:

- Haskell and Cabal file types with syntax highlighting, commenting and brace matching
- Haskell Language Server via the built-in IntelliJ LSP client: diagnostics, completion, navigation, rename, formatting (ormolu / fourmolu / stylish-haskell, cabal-gild / cabal-fmt), hlint code actions
- Works with local, WSL and Dev Container projects; HLS is detected from PATH or ghcup
- Hoogle tool window and *Search Hoogle* action (Alt+Shift+H) using the Hoogle JSON API, a custom Hoogle server, or a local `hoogle` CLI
- Quick Documentation from HLS, falling back to Hoogle with Hackage links when HLS has no docs
<!-- Plugin description end -->

## 要件
- IntelliJ IDEA 2026.1.4 以降（内蔵 LSP API が必要）
- `haskell-language-server`（ghcup 推奨：`ghcup install hls`）、cabal、GHC

## ビルド
```bash
./gradlew test buildPlugin verifyPlugin
# → build/distributions/intellij-haskell-modern-0.1.0.zip
```
Settings → Plugins → ⚙ → Install Plugin from Disk… で zip をインストール。

## 設定
Settings → Languages & Frameworks → Haskell

設計は [docs/DESIGN.md](docs/DESIGN.md) を参照。
