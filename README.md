# AutoReplant

> 採收成熟農作物後自動回種的 Paper / Purpur 插件
>
> **作者：MrPippi** ｜ Minecraft 1.21.x ｜ Java 21 ｜ Paper / Purpur

---

## 目錄

- [功能特色](#功能特色)
- [支援農作物](#支援農作物)
- [伺服器需求](#伺服器需求)
- [安裝方式](#安裝方式)
- [指令](#指令)
- [權限](#權限)
- [設定檔](#設定檔)
- [PlaceholderAPI](#placeholderapi)
- [運作原理](#運作原理)
- [從原始碼編譯](#從原始碼編譯)
- [授權](#授權)

---

## 功能特色

- 偵測玩家破壞**完全成熟**的農作物時自動回種（未成熟不觸發）
- **幼苗保護**：開啟自動回種時，按住左鍵不會誤破尚未成熟的農作物（事件自動取消）
- 支援 **Fortune 附魔**：掉落計算由伺服器原生處理，結果完全正確
- **骨粉自動收成**（可設定）：以骨粉將作物催至全熟後，自動執行收成並回種
- **個人開關**：每位玩家獨立控制，設定跨重啟持久保存
- **種子消耗模式**（可設定）：
  - `check-seeds: true` — 優先從此次掉落物扣一顆種子；掉落物無種子則從背包扣（兩者皆無則跳過回種）
  - `check-seeds: false` — 免費回種，不消耗種子
- 回種成功時顯示 **Happy Villager 粒子特效**
- 指令短縮寫 `/arp`，支援**無參數直接切換**（`/arp` = 切換目前狀態）
- 支援 **熱重載** `/arp reload`，無需重啟伺服器即可套用新設定
- 訊息與預設狀態皆可在 `config.yml` 自訂，支援 MiniMessage 與傳統 `&` 代碼
- 支援 **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** — 提供 `%autoreplant_status%` 變數
- 支援 **[AutoPickup](https://github.com/MrPippi/AutoPickup) 相容**：骨粉收成掉落物可直接整合至 AutoPickup 撿取流程

---

## 支援農作物

| 農作物 | 觸發條件 | 消耗種子（check-seeds: true 時） | 底部方塊 |
||--------|---------|--------------------------------|---------|
|| 小麥（Wheat） | 完全成熟（age 7） | 小麥種子（Wheat Seeds） | 耕地 |
|| 胡蘿蔔（Carrot） | 完全成熟（age 7） | 胡蘿蔔（Carrot，自身即種子） | 耕地 |
|| 馬鈴薯（Potato） | 完全成熟（age 7） | 馬鈴薯（Potato，自身即種子） | 耕地 |
|| 甜菜根（Beetroot） | 完全成熟（age 3） | 甜菜根種子（Beetroot Seeds） | 耕地 |
|| 地獄疙瘩（Nether Wart） | 完全成熟（age 3） | 地獄疙瘩（Nether Wart，自身即種子） | 靈魂沙 |

> **提示**：種子比對僅依材質（Material）判斷，自訂名稱或附魔的種子同樣能被識別與消耗。

---

## 伺服器需求

| 項目 | 需求 |
|------|------|
| 伺服器核心 | [Paper](https://papermc.io/) 或 [Purpur](https://purpurmc.org/) |
| Minecraft 版本 | 1.21.x |
| Java 版本 | Java 21 以上 |
| 選用插件（soft-depend） | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)、[AutoPickup](https://github.com/MrPippi/AutoPickup) |

---

## 安裝方式

1. 從 [Releases](../../releases) 下載最新的 `AutoReplant-x.x.x.jar`，
   或自行編譯（見下方「[從原始碼編譯](#從原始碼編譯)」）。
2. 將 JAR 檔放入伺服器的 `plugins/` 資料夾。
3. 啟動或重啟伺服器。
4. 插件會自動生成 `plugins/AutoReplant/config.yml`，可依需求修改後執行 `/arp reload`。

> 若需 PlaceholderAPI 功能，請另行安裝 PlaceholderAPI。本插件將自動偵測並註冊變數，無需任何額外設定。

---

## 指令

| 指令 | 縮寫 | 權限 | 說明 |
|------|------|------|------|
| `/autoreplant` | `/arp` | `autoreplant.use` | 切換自動回種（開 ↔ 關） |
| `/autoreplant on` | `/arp on` | `autoreplant.use` | 開啟自動回種 |
| `/autoreplant off` | `/arp off` | `autoreplant.use` | 關閉自動回種 |
| `/autoreplant reload` | `/arp reload` | `autoreplant.reload` | 熱重載 config.yml |

> 開關設定為**個人**設定，每位玩家獨立控制，重新登入後仍保留。

---

## 權限

| 權限節點 | 預設 | 說明 |
|---------|------|------|
| `autoreplant.use` | 所有玩家（`true`） | 允許使用 `/autoreplant`（含無參數切換與 on/off） |
| `autoreplant.reload` | 管理員（`op`） | 允許使用 `/autoreplant reload` 熱重載設定 |

---

## 設定檔

插件啟動時會在 `plugins/AutoReplant/` 自動生成以下檔案：

```
plugins/AutoReplant/
├── config.yml    ← 主設定（首次啟動自動建立，不會覆蓋已存在的設定）
└── players.yml   ← 玩家個人狀態（執行時自動管理，無需手動編輯）
```

### `config.yml` 完整說明

```yaml
# 所有玩家的預設狀態（true = 開啟，false = 關閉）
# 玩家可用 /autoreplant on|off 個別覆蓋
default-enabled: true

# 種子消耗檢查
#   true  — 回種前必須消耗一顆種子：
#             1. 優先從此次採收的掉落物中扣除
#             2. 若掉落物無種子，則從玩家背包扣除
#             3. 兩者皆無則跳過回種，掉落物正常生成
#   false — 直接回種，不消耗任何種子（所有掉落物正常生成）
check-seeds: true

# 骨粉催熟自動收成
#   true  — 以骨粉將作物催化至全熟時，自動執行收成（生成掉落物）並回種
#           遵循 check-seeds 設定與玩家的個人開關
#   false — 骨粉催熟不觸發自動回種，只有手動破壞才觸發
bone-meal-auto-replant: true

# 訊息設定
# 支援兩種語法（可於同一字串內自由混用）：
#   MiniMessage 標籤：<green>, <bold>, <#ff0000>, <gradient:#f00:#00f> ...
#   傳統 & 代碼：    &a, &l, &#00ff00 ...（大小寫均可）
# 佔位符：<command> 會被玩家實際輸入的指令名稱取代（autoreplant 或 arp）
messages:
  prefix:         "<dark_gray>[<green>AutoReplant<dark_gray>] "
  enabled:        "<green>自動回種植 <bold>開啟"
  disabled:       "<red>自動回種植 <bold>關閉"
  usage:          "<yellow>用法: /<command> [on|off|reload]"
  no-permission:  "<red>你沒有權限使用此指令。"
  console-denied: "<red>此指令只能由玩家使用。"
  reload-success: "<green>設定檔已重新載入。"
```

### 訊息語法速查

兩種格式可**自由混用**於同一字串：

**MiniMessage 標籤**（推薦）

| 標籤 | 效果 |
|------|------|
| `<green>` / `<red>` / `<yellow>` … | 顏色 |
| `<bold>` / `<italic>` / `<underlined>` | 格式 |
| `<#ff0000>` | 十六進位 RGB 顏色 |
| `<gradient:#ff0000:#0000ff>文字</gradient>` | 漸層色 |
| `<rainbow>文字</rainbow>` | 彩虹色 |
| `<reset>` | 清除所有格式 |

完整語法參考：[MiniMessage 文件](https://docs.advntr.dev/minimessage/format.html)

**傳統 `&` 代碼**（向下相容）

| 代碼 | 效果 | 代碼 | 效果 |
|------|------|------|------|
| `&a` | 綠色 | `&l` | 粗體 |
| `&c` | 紅色 | `&o` | 斜體 |
| `&e` | 黃色 | `&n` | 底線 |
| `&8` | 深灰 | `&r` | 重設 |
| `&#RRGGBB` | 十六進位色 | `&k` | 隨機字元 |

**混用範例**

```yaml
prefix:  "<dark_gray>[&aAutoReplant<dark_gray>] "
enabled: "&a自動回種植 <bold>開啟 <#00ff88>✔"
```

---

## PlaceholderAPI

若伺服器已安裝 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)，AutoReplant 將自動偵測並註冊以下變數（無需額外設定）：

| 變數 | 說明 | 回傳值 |
|------|------|--------|
| `%autoreplant_status%` | 該玩家目前的自動回種狀態 | `ON` 或 `OFF` |

可用於計分板、TabList、NPC 對話等任何支援 PlaceholderAPI 的場景。

---

## 運作原理

插件使用**兩階段事件架構**處理手動採收，並以獨立的骨粉處理流程確保正確行為：

```
【幼苗保護】
玩家按住左鍵採集農場
  └─ 作物未成熟 + 玩家已開啟自動回種？ ──▶ 取消破壞事件（幼苗不受損）

【手動採收流程（兩階段）】

階段一：BlockBreakEvent（HIGHEST 優先度）
玩家破壞農作物
      │
      ├─ 不符條件（非成熟作物 / 創意模式）？ ──▶ 跳過
      │
      ├─ 玩家已關閉自動回種（成熟作物）？ ──▶ 清除該位置殘留紀錄，跳過
      │
      └─ 符合條件（成熟作物 + 個人已開啟）
                └─▶ 標記此位置為「待回種」

階段二：BlockDropItemEvent（NORMAL 優先度，方塊確實破壞後才觸發）
      │
      ├─ 非標記位置？ ──▶ 略過
      │
      └─ 標記位置
                │
                ├─ check-seeds: true
                │       ├─ 掉落物中有對應種子？ ──▶ 是：從掉落物扣除一顆
                │       │                          └─ 否：背包中有對應種子？
                │       │                                   ├─ 是：從背包扣除一顆
                │       │                                   └─ 否：放棄回種，掉落物正常生成
                │       └─ （繼續）
                │
                └─ check-seeds: false ──▶ 不消耗種子，直接繼續
                          │
                          ▼
              下一 tick：確認位置為空氣且下方為耕地
                          │
                          ▼
              回種（幼苗，age = 0）+ Happy Villager 粒子特效
              所有掉落物正常生成

【骨粉催熟流程（bone-meal-auto-replant: true）】

BlockFertilizeEvent（NORMAL 優先度）
玩家以骨粉催化農作物至全熟
      │
      └─ 下一 tick 執行：
                │
                ├─ 玩家已關閉自動回種？ ──▶ 生成掉落物，清除作物方塊（不回種）
                │
                └─ 玩家已開啟自動回種
                          │
                          ├─ check-seeds: true
                          │       ├─ 從掉落物扣種子（成功）或從背包扣種子（成功） ──▶ 繼續
                          │       └─ 兩者皆無 ──▶ 生成掉落物，清除作物，不回種
                          │
                          └─ check-seeds: false ──▶ 直接繼續
                                    │
                                    ▼
                        生成掉落物（若 AutoPickup 已啟用：直接加入背包，多餘掉地）
                                    │
                                    ▼
                        回種（幼苗，age = 0）+ Happy Villager 粒子特效
```

**設計優點：**
- `BlockDropItemEvent` 只在方塊確實被破壞後才觸發，徹底避免「先扣種子後取消破壞」的競爭條件
- Fortune 附魔由伺服器原生計算，無需模擬（手動採收）；骨粉流程透過 `block.getDrops(hand, player)` 模擬，同樣尊重 Fortune
- 其他插件（如保護插件）在 `BlockBreakEvent` 取消事件後，`BlockDropItemEvent` 不會觸發，回種流程自動中斷

---

## 從原始碼編譯

**需求**：JDK 21、Maven 3.6+

```bash
git clone <此倉庫>
cd AutoReplant
mvn package
```

編譯完成後，JAR 檔位於 `target/AutoReplant-1.0.0.jar`，直接放入 `plugins/` 資料夾即可。

---

## 授權

本插件依照 [MIT License](LICENSE) 授權開放使用。
