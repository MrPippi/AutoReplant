# 更新紀錄（Changelog）

格式參考 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)，版本號遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

## [Unreleased]

### 變更（Changed）

- 原始碼目錄由 `src/main/java/mrpippi/autoreplant/` 搬到與 package 宣告一致的 `src/main/java/dev/autoreplant/`。
  僅搬移檔案，編譯產出的 class 與先前完全相同，對伺服器與玩家沒有任何影響。

## [2.0.0] - 2026-10-09

> 發版前請先完成 [升級後需手動確認的事項](#升級後需手動確認的事項)。

### ⚠️ 破壞性變更（Breaking）

- **最低伺服器版本提高為 Paper / Purpur 26.2**（`plugin.yml` 的 `api-version` 由 `26.1.2` 改為 `26.2`）。
  26.2 以前的伺服器會拒絕載入本插件；仍在 26.1.x 的伺服器請繼續使用舊版。
- **骨粉自動收成改為遵循玩家個人開關**：已用 `/arp off` 關閉自動回種的玩家，
  以骨粉催熟作物時維持原版行為（作物只長熟、留在原地），不再被自動收成並清成空氣。

### 修正（Fixed）

- `check-seeds: true` 時，若作物下方不是正確的底部方塊（耕地 / 靈魂沙），
  先前會先扣掉一顆種子、下一 tick 才發現無法回種，導致種子白白消耗。
  現在會在扣種子之前先檢查底部方塊，不符則不回種、也不扣種子。

### 變更（Changed）

- 編譯目標改為 `paper-api` **26.2.build.132-stable**。
  先前使用開放版本範圍 `[26.1.2.build,)`，實際會抓到最新的預覽版（例如 26.3 alpha），每次編譯結果可能不同；現已固定版本。
- PlaceholderAPI（`provided`）由 2.11.6 升級至 **2.12.3**。`PlaceholderExpansion` 的公開 API 兩版相同。
- PlaceholderAPI 的 Maven 倉庫改用正式位址 `https://repo.helpch.at/releases/`（舊位址只會轉址到此）。
- 以 `getPluginMeta()` 取代已棄用的 `getDescription()`（啟動訊息與 PlaceholderAPI 擴展的作者 / 版本來源，內容不變）。
- 建置外掛版本固定：`maven-compiler-plugin` 3.16.0、`maven-resources-plugin` 3.3.1、`maven-jar-plugin` 3.4.1；
  移除多餘的 `<source>` / `<target>`，只保留 `<release>`。
- README 的版本需求更新為 Java 25 / Paper 26.2，並新增「依賴說明」。

### 新增（Added）

- 單元測試（JUnit 5.14.4 + Mockito 5.24.0，共 80 個），涵蓋回種監聽器、指令、玩家狀態與訊息格式、
  AutoPickup 相容層、PlaceholderAPI 擴展。執行 `mvn verify` 會自動跑測試。
- GitHub Actions CI：每次 push 到 `main` 與每個 Pull Request 都會以 JDK 25 執行 `mvn -B verify`，並上傳建置出的 JAR。
- 自動發版：推送 `v*` tag 時，GitHub Actions 會建置並測試、確認 tag 與 `pom.xml` 版本一致，
  再以本檔對應段落為說明建立 GitHub Release，並附上 JAR。

### 升級後需手動確認的事項

自動測試以 mock 模擬伺服器，只能證明程式邏輯，**無法**證明在真實伺服器上的行為。
發版前請在 **Paper 26.2** 測試伺服器上逐項確認：

**載入與相容性**

- [ ] 伺服器啟動時出現 `AutoReplant v<版本> 已啟動！`，且沒有任何錯誤或警告
- [ ] 在 26.1.x 伺服器上會被拒絕載入（確認錯誤訊息清楚，供發版說明引用）
- [ ] 以 JDK 25 執行伺服器（JDK 21 會出現 `UnsupportedClassVersionError`）

**核心功能**

- [ ] 小麥、胡蘿蔔、馬鈴薯、甜菜根在耕地上採收後自動回種為幼苗，並出現 Happy Villager 粒子
- [ ] 地獄疙瘩在靈魂沙上採收後自動回種
- [ ] 開啟自動回種時，按住左鍵不會打掉未成熟作物；`/arp off` 後可以正常打掉
- [ ] 使用 Fortune 鋤頭採收時，掉落數量正常（Fortune 由伺服器原生計算）
- [ ] 保護插件（如 WorldGuard）禁止破壞的區域內，不會回種也不會扣種子

**`check-seeds`**

- [ ] `check-seeds: true`：掉落物中有種子時扣掉落物；沒有時扣背包；兩者皆無時不回種
- [ ] `check-seeds: false`：直接回種，掉落物與背包都不變
- [ ] **（本次修正）** 底部方塊不對時（例如把地獄疙瘩下的靈魂沙換成其他方塊後再打掉作物），不回種，且種子仍在掉落物 / 背包中

**骨粉自動收成**

- [ ] `bone-meal-auto-replant: true` 且玩家已開啟：骨粉催熟至全熟後自動收成並回種
- [ ] **（本次變更）** 玩家已 `/arp off`：骨粉只讓作物長熟、留在原地，不會被收成
- [ ] `bone-meal-auto-replant: false`：骨粉維持原版行為

**選用插件**

- [ ] 安裝 AutoPickup 並開啟時，骨粉收成的掉落物直接進背包，背包滿時多餘的掉在地上
- [ ] 安裝 PlaceholderAPI **2.12.x** 時，`/papi parse me %autoreplant_status%` 回傳 `ON` / `OFF`
- [ ] 安裝 PlaceholderAPI **2.11.x** 時，同上指令仍正常（確認向下相容）

**指令與設定**

- [ ] `/arp`、`/arp on`、`/arp off` 正常切換並顯示訊息；Tab 補全正常
- [ ] `/arp reload` 需要 `autoreplant.reload` 權限，重新載入後新設定生效
- [ ] 重啟伺服器後，玩家個人開關保留（`plugins/AutoReplant/players.yml`）

**發版**

- [x] `pom.xml` 版本號升為 `2.0.0`，本節由 `[Unreleased]` 改為 `[2.0.0]`
- [ ] 以上項目確認無誤後，建立 `v2.0.0` tag 與 GitHub Release，並附上 `AutoReplant-2.0.0.jar`
- [ ] 發版說明中標明最低伺服器版本 26.2，以及骨粉行為的變更

## [1.0.0] - 2026-05-08

首個版本（本紀錄建立前的狀態）。

- 採收完全成熟的小麥、胡蘿蔔、馬鈴薯、甜菜根、地獄疙瘩後自動回種
- 幼苗保護、Fortune 支援、骨粉自動收成、`check-seeds` 種子消耗模式
- 玩家個人開關（`/arp`、`/arp on|off`）與 `/arp reload`，設定跨重啟保存
- MiniMessage 與 `&` 色碼訊息、PlaceholderAPI `%autoreplant_status%`、AutoPickup 相容
- 需求：Java 25、Paper 26.1.2

[Unreleased]: https://github.com/MrPippi/AutoReplant/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/MrPippi/AutoReplant/compare/cb0cad1...v2.0.0
[1.0.0]: https://github.com/MrPippi/AutoReplant/tree/cb0cad1
