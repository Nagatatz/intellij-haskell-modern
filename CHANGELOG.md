<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Haskell Modern Changelog

## [Unreleased]

### Added

- Haskell (`.hs`, `.hs-boot`, `.hsig`, `.hsc`) and Cabal file types with lexer-based highlighting, commenter and brace matching
- Haskell Language Server integration via the IntelliJ Platform LSP API (auto-detect from PATH / ghcup, WSL / Dev Container aware)
- Settings: HLS path, extra arguments, formatter, cabal formatter, hlint, semantic tokens, extra HLS JSON
- Hoogle tool window and *Search Hoogle* action (Alt+Shift+H), HTTP API or `hoogle` CLI
- Quick Documentation from HLS hover with Hoogle / Hackage fallback
