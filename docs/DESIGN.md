# Haskell Modern — 設計ドキュメント

アーカイブ済みの [rikvdkleij/intellij-haskell](https://github.com/rikvdkleij/intellij-haskell) が提供していた体験を、
2026 年時点のツールチェーン（cabal / GHC 9.8+ / Haskell Language Server / Hoogle JSON API / Hackage）の上で作り直す
IntelliJ プラットフォーム向けプラグイン。対象 IDE は IntelliJ IDEA 2026.1.4 以降（統合版 / Ultimate）。

AtCoder など競技プログラミング向けの機能はスコープ外とする。

---

## 1. 目標と非目標

### 目標
- 「普段の Haskell 開発（cabal プロジェクト）を IntelliJ で快適に」できること
- 解析・補完・型情報は自前実装せず **HLS に委譲**し、IDE 側は統合と UX に集中する
- ドキュメント・検索は **Hoogle / Hackage のオンライン情報**（またはローカル Hoogle）を一級市民として扱う
- Dev Container / WSL / Docker などのリモート環境（Eel）でもそのまま動く構成にする

### 非目標
- intellij-haskell のような独自パーサ＋ stack/intero/haskell-tools 依存の解析エンジンの再実装
- Stack 前提の機能（cabal を第一に、Stack は HLS 側の hie-bios 任せ）
- 競技プログラミング向け機能（テンプレート、提出、テストケース取得など）

---

## 2. 機能マップ（IntelliJ-Haskell → Haskell Modern）

| IntelliJ-Haskell の機能 | 旧実装の基盤 | Haskell Modern での実現手段 | フェーズ |
|---|---|---|---|
| ファイルタイプ / シンタックスハイライト | 独自 Grammar-Kit パーサ | 手書きレキサ（.hs/.hs-boot/.hsig/.hsc, .cabal, cabal.project*）＋ HLS セマンティックトークン（任意） | 1 ✅ |
| コメント / 括弧対応 / クォート | 独自 PSI | Commenter / BraceMatcher / QuoteHandler | 1 ✅ |
| エラー・警告表示 | intero / ghci + hlint 呼び出し | HLS の診断（GHC + hlint プラグイン） | 1 ✅ |
| コード補完 | intero + 独自インデックス | HLS completion（LSP） | 1 ✅ |
| 型情報表示 / Quick Doc | intero `:type-at` + ローカル haddock | HLS hover ＋ docs が空なら **Hoogle フォールバック**、Hackage / Hoogle リンク付き | 1 ✅ |
| 定義へ移動 / 使用箇所検索 | 独自インデックス | HLS definition / references | 1 ✅（LSP 標準） |
| リネーム | 独自 | HLS rename プラグイン | 1 ✅（LSP 標準） |
| フォーマット（hindent / stylish-haskell） | 外部コマンド直接実行 | HLS formattingProvider（ormolu / fourmolu / stylish-haskell）、cabal-gild / cabal-fmt | 1 ✅（設定可能） |
| hlint の提案・適用 | hlint 直接実行 | HLS hlint プラグイン（code action）、設定で ON/OFF | 1 ✅ |
| Hoogle 検索 | ローカル hoogle CLI | **Hoogle ツールウィンドウ**（JSON API / 任意 URL / ローカル CLI）＋ Search Hoogle アクション（Alt+Shift+H） | 1 ✅ |
| 型シグネチャ追加・import 追加などの intention | intero + 独自 | HLS code actions（import 追加、型シグネチャ、未使用 import 削除など） | 1 ✅（LSP 標準） |
| REPL | stack repl | `cabal repl` を IDE のコンソールとして起動、選択範囲送信 | 3 |
| プロジェクト設定 / SDK | Stack ベースの独自 Project SDK | cabal.project 認識、ghcup 検出、GHC/HLS バージョン表示 | 2 |
| 実行構成 | stack run/test | `cabal run` / `cabal test` / `cabal bench` の Run Configuration、テスト結果パース | 2 |
| ライブラリソース参照 | stack unpack + インデックス | HLS の go-to-definition（依存は hie ファイル次第）＋ Hackage ソースへのリンク | 2 |
| 構造ビュー / Navigate Symbol | 独自 PSI | LSP documentSymbol / workspaceSymbol | 2 |
| Inlay hints / コードレンズ（型・eval） | なし | HLS inlay hints、eval plugin（`-- >>>`） | 2〜3 |

✅ = Phase 1 MVP に含まれる（LSP 標準機能は IntelliJ の LSP クライアントがそのまま提供）。

---

## 3. アーキテクチャ

```
┌───────────────────────── IntelliJ IDEA 2026.x ──────────────────────────┐
│  lang/   HaskellFileType, HaskellLexer, SyntaxHighlighter, Commenter …  │
│  cabal/  CabalFileType, CabalLexer, Highlighter, Commenter              │
│                                                                          │
│  lsp/    HlsIntegrationProvider ── LspClientManager (built-in LSP API)   │
│            └ HlsClientDescriptor ── HlsLocator (PATH / ghcup / 設定)      │
│                    │  workspace/configuration ← HlsConfig (+ 追加 JSON)   │
│                    ▼                                                     │
│          haskell-language-server-wrapper --lsp  (Eel 経由: ローカル /     │
│                                                  Dev Container / WSL)   │
│  lsp/    HaskellDocumentationTargetProvider (Quick Doc)                  │
│            HLS hover → docs 無し → HoogleClient → Hackage リンク         │
│                                                                          │
│  hoogle/ HoogleClient (HTTP JSON / CLI) ← HoogleToolWindow, SearchAction │
│  settings/ HaskellSettings (@State) + Configurable (Kotlin UI DSL)       │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.1 技術スタック
- **Kotlin**（JVM 21 ターゲット）
- **IntelliJ Platform Gradle Plugin 2.x**（JetBrains 公式 intellij-platform-plugin-template ベース）
  - `intellijIdea(platformVersion)` で 2026.2.3 に対してビルド、`sinceBuild = 261.26222`（2026.1.4）
  - `bundledModule("intellij.platform.lsp")` で LSP API に依存、plugin.xml で `com.intellij.modules.lsp`
  - Plugin Verifier（`verifyPlugin`）、changelog プラグイン、JUnit4 + `BasePlatformTestCase`
- 外部ライブラリは追加しない（JSON は IDE 同梱の Gson、HTTP は `HttpRequests`）

### 3.2 構文レイヤ
Haskell の正確なパースはレイアウト規則・拡張が多く、独自パーサは旧プラグインの保守負担の主因だった。
そこで **レキサのみ自前**（キーワード、conid/varid、演算子、数値、文字/文字列、ネストコメント、Haddock、
プラグマ、CPP、QuasiQuote、プロモーション `'`）とし、PSI はフラット（`FlatParser`）にする。
意味解析は HLS に任せ、セマンティックトークンを有効化すれば HLS 側の色付けが上書きされる。

### 3.3 HLS 統合
- `HlsLocator`：設定パス → `PATH`（`EelExecApi.findExeFilesInPath`）→ `~/.ghcup/bin` の順で
  `haskell-language-server-wrapper` / `haskell-language-server` を探索。リモート（Eel）ならコンテナ内のパスとして解決。
- `HlsClientDescriptor`：`ProjectWideLspClientDescriptor` を継承。`--lsp` 起動、ghcup 由来なら
  ログインシェルの PATH に ghcup bin を前置（ghc / cabal を HLS が見つけられるように）。
  Eel リモート時は `getFilePath` / `findLocalFileByPath` でコンテナ内パス ⇄ IDE の VFS パスを変換。
- 設定（formatter、cabal formatter、hlint、セマンティックトークン、追加 JSON）は
  `workspace/configuration` と `initializationOptions` の両方で `{"haskell": {...}}` として送る。
  変更時は `didChangeConfiguration` を送信、パス・引数・トークン設定の変更時のみサーバ再起動。
- ステータスバーの LSP ウィジェットから設定画面を開ける。Tools メニューに **Restart Haskell Language Server**。

### 3.4 ドキュメント（Quick Documentation）
`DocumentationTargetProvider` を `order="first"` で登録し、Haskell ファイルでは独自ターゲットを返す。
1. HLS に `textDocument/hover` を投げる（5 秒タイムアウト）
2. Markdown から型シグネチャ・定義モジュール・パッケージ・Haddock 本文を抽出（`HoverInfo`）
3. 本文が空で「Hoogle フォールバック」が ON なら、`名前 +パッケージ` で Hoogle を検索し、
   同一モジュール → docs あり → 先頭 の優先順で 1 件選ぶ
4. `DocumentationMarkup` で描画し、Hackage（haddock アンカー付き）・Hoogle へのリンクを付ける

内蔵 LSP の hover は `LspHoverDisabled` で無効化して二重表示を避ける。

### 3.5 Hoogle
- HTTP モード：`{hoogleUrl}?mode=json&format=text&hoogle=Q&start=1&count=N`。URL は設定で変更可能
  （社内ミラーやローカル `hoogle server --local` を指定可）
- CLI モード：`hoogle search --json --count=N Q`（ローカル DB、オフライン用）
- ツールウィンドウ：インクリメンタル検索（450ms デバウンス）、履歴、結果リスト＋詳細 HTML、
  修飾名で結果ゼロなら非修飾で再検索、ダブルクリック / Enter でブラウザで開く
- **Search Hoogle**（Alt+Shift+H、エディタのコンテキストメニュー / Tools）：選択範囲またはキャレット位置の識別子・演算子を検索

---

## 4. LSP クライアントの選定：IntelliJ 内蔵 LSP API vs LSP4IJ

| 観点 | 内蔵 LSP API（`com.intellij.platform.lsp`） | LSP4IJ（Red Hat） |
|---|---|---|
| 提供元 / 保守 | JetBrains（プラットフォーム本体） | Red Hat（外部プラグイン） |
| 利用可能 IDE | 2025.3 以降の統合版 IDEA（無料枠含む）、各有償 IDE。2026.1 以降は主要 IDE で利用可 | Community 含むほぼ全 IDE |
| 依存 | 追加インストール不要 | ユーザーが LSP4IJ を別途インストール |
| リモート開発 / Dev Container / WSL | **Eel 対応**（`GeneralCommandLine` が Eel 経由で起動、パス変換 API あり） | 独自対応、リモート開発での実績は限定的 |
| 機能範囲 | 診断・補完・hover・定義・参照・リネーム・フォーマット・code action・セマンティックトークン・inlay hints など主要機能 | より広い（DAP、LSP コンソール、トレース UI など） |
| UI 統合 | ステータスバーウィジェット、IDE 標準の各 UI | 独自の LSP コンソールビュー |

**採用：内蔵 LSP API。** 理由：
1. ユーザー環境（IntelliJ IDEA Ultimate / 統合版 2026.x）で追加プラグイン不要で動く
2. Dev Container のリモートモードが主要ユースケースであり、Eel 統合が JetBrains 公式でサポートされる
3. API が 2026.1 で整理（`LspIntegrationProvider` / `ProjectWideLspClientDescriptor` / `LspCustomization`）され、長期保守が見込める
4. 既存の rockofox/intellij-haskell-lsp 等の Issue（LSP4IJ 依存・リモート非対応・バージョン追従）に見られる問題を避けられる

トレードオフ：DAP（デバッガ）や LSP トレースビューは自前で用意する必要がある。Experimental API（Eel 系）を使っているため、
Plugin Verifier の警告（互換性問題ではない）を IDE 更新ごとに確認する。

---

## 5. 設定項目（Settings → Languages & Frameworks → Haskell）

| 項目 | 既定値 | 説明 |
|---|---|---|
| HLS パス | 空（自動検出） | 空なら PATH → ghcup の順に検出 |
| HLS 追加引数 | 空 | 例：`--debug`、`-j4` |
| Formatter | ormolu | ormolu / fourmolu / stylish-haskell / none |
| Cabal formatter | cabal-gild | cabal-gild / cabal-fmt / none |
| hlint | ON | HLS hlint プラグインの診断・code action |
| セマンティックトークン | OFF | HLS 側の色付けを使う（重い場合は OFF） |
| 追加 HLS 設定 JSON | 空 | `{"haskell":{...}}` または中身のみ。既定値に deep merge |
| Hoogle モード | HTTP | HTTP（JSON API）/ CLI（ローカル hoogle） |
| Hoogle URL | https://hoogle.haskell.org/ | 任意の Hoogle サーバ |
| hoogle CLI パス | `hoogle` | CLI モード時 |
| 結果件数 | 30 | |
| Quick Doc の Hoogle フォールバック | ON | HLS hover に docs が無い場合 |

---

## 6. ロードマップ

### Phase 1（MVP、本リポジトリの現状）
- Haskell / Cabal ファイルタイプ、ハイライト、カラー設定ページ、コメント、括弧・クォート
- HLS（内蔵 LSP）起動・自動検出・設定画面・再起動アクション・ステータスバーウィジェット
- Hoogle ツールウィンドウ、Search Hoogle（Alt+Shift+H）、Hoogle URL / CLI 切替
- Quick Documentation：HLS hover＋Hoogle フォールバック＋Hackage リンク
- 単体テスト（レキサ、Hoogle パーサ / URL / アンカー、HLS 設定マージ、hover 解析、エディタ機能）

### Phase 2（日常開発の完成度）
- cabal Run Configuration（run / test / bench / build）、コンパイルエラーのリンク化、テスト結果ツリー（hspec / tasty）
- プロジェクト検出（cabal.project / *.cabal）、ghcup で GHC・HLS バージョン表示と不一致警告
- HLS 起動失敗の診断パネル（ログ表示、`haskell-language-server-wrapper --probe-tools`）
- 構造ビュー（documentSymbol）、Navigate → Symbol
- Inlay hints / コードレンズ（型シグネチャ、eval プラグイン `-- >>>`）の有効化と設定
- Cabal ファイルの補完（フィールド名、既知パッケージ名 = Hackage インデックス）
- Dev Container 実機での E2E 確認と不具合修正

### Phase 3（intellij-haskell からの機能移植）
- `cabal repl` コンソール（選択範囲 / ファイルのロード、`:type` / `:info` 連携）
- Hackage パッケージ検索・依存追加（build-depends へ挿入、バージョン範囲提案）
- ライブラリソースの取得とナビゲーション（`cabal get` / hie ファイル）
- Live templates（module header、instance、LANGUAGE pragma）、拡張名・GHC オプションの補完
- オフライン Hoogle DB の自動生成（`hoogle generate --local`）とツールウィンドウからの更新

### Phase 4（公開）
- GitHub リポジトリ作成、CI（GitHub Actions：build / test / verifyPlugin / UI テスト）
- JetBrains Marketplace 公開、署名、changelog 運用
- UI テスト（IntelliJ Driver / Starter）による IDE 実機テスト

---

## 7. 既知のリスク・未検証事項
- **IDE 実機での UI テストは未実施**（ヘッドレステストと Plugin Verifier のみ）
- **Dev Container / WSL での Eel パス変換**は実機未検証
- **hover の上書き**：内蔵 `LspDocumentationTargetProvider` より先に本プラグインのプロバイダが選ばれる想定（`order="first"`）だが、実 IDE での優先順位は未確認
- **Hoogle CLI モード**は hoogle 実行環境でのテスト未実施
- セマンティックトークン ON/OFF 切替時の再起動挙動
- Experimental API（Eel、ToolWindowFactory の一部）使用。Verifier 結果は Compatible だが将来変更の可能性あり
