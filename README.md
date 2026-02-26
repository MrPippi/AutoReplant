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
- [運作原理](#運作原理)
- [從原始碼編譯](#從原始碼編譯)
- [授權](#授權)

---

## 功能特色

- 偵測玩家破壞**完全成熟**的農作物時自動回種（未成熟不觸發）
- 支援 **Fortune 附魔**：掉落計算由伺服器原生處理，結果完全正確
- **個人開關**：每位玩家獨立控制，設定跨重啟持久保存
- **種子消耗模式**（可設定）：
  - `check-seeds: true` — 從玩家背包扣除一顆種子（需持有種子才能回種）
  - `check-seeds: false` — 免費回種，不消耗種子
- 指令短縮寫 `/arp`，方便快速切換
- 支援 **熱重載** `/arp reload`，無需重啟伺服器即可套用新設定
- 訊息與預設狀態皆可在 `config.yml` 自訂，支援 MiniMessage 與傳統 `&` 代碼

---

## 支援農作物

| 農作物 | 觸發條件 | 消耗種子（check-seeds: true 時） |
|--------|---------|--------------------------------|
| 小麥（Wheat） | 完全成熟（age 7） | 小麥種子（Wheat Seeds） |
| 胡蘿蔔（Carrot） | 完全成熟（age 7） | 胡蘿蔔（Carrot，自身即種子） |
| 馬鈴薯（Potato） | 完全成熟（age 7） | 馬鈴薯（Potato，自身即種子） |
| 甜菜根（Beetroot） | 完全成熟（age 3） | 甜菜根種子（Beetroot Seeds） |

> **提示**：種子比對僅依材質（Material）判斷，自訂名稱或附魔的種子同樣能被識別與消耗。

---

## 伺服器需求

| 項目 | 需求 |
|------|------|
| 伺服器核心 | [Paper](https://papermc.io/) 或 [Purpur](https://purpurmc.org/) |
| Minecraft 版本 | 1.21.x |
| Java 版本 | Java 21 以上 |

---

## 安裝方式

1. 從 [Releases](../../releases) 下載最新的 `AutoReplant-x.x.x.jar`，
   或自行編譯（見下方「[從原始碼編譯](#從原始碼編譯)」）。
2. 將 JAR 檔放入伺服器的 `plugins/` 資料夾。
3. 啟動或重啟伺服器。
4. 插件會自動生成 `plugins/AutoReplant/config.yml`，可依需求修改後執行 `/arp reload`。

---

## 指令

| 指令 | 縮寫 | 權限 | 說明 |
|------|------|------|------|
| `/autoreplant on` | `/arp on` | `autoreplant.use` | 開啟自動回種 |
| `/autoreplant off` | `/arp off` | `autoreplant.use` | 關閉自動回種 |
| `/autoreplant reload` | `/arp reload` | `autoreplant.reload` | 熱重載 config.yml |

> 開關設定為**個人**設定，每位玩家獨立控制，重新登入後仍保留。

---

## 權限

| 權限節點 | 預設 | 說明 |
|---------|------|------|
| `autoreplant.use` | 所有玩家（`true`） | 允許使用 `/autoreplant on\|off` |
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
#   true  — 回種前必須從玩家背包消耗一顆對應種子
#           若玩家身上沒有種子，跳過回種，所有掉落物正常生成
#   false — 直接回種，不消耗任何種子（所有掉落物正常生成）
check-seeds: true

# 訊息設定
# 支援兩種語法（可於同一字串內自由混用）：
#   MiniMessage 標籤：<green>, <bold>, <#ff0000>, <gradient:#f00:#00f> ...
#   傳統 & 代碼：    &a, &l, &#00ff00 ...（大小寫均可）
# 佔位符：<command> 會被玩家實際輸入的指令名稱取代（autoreplant 或 arp）
messages:
  prefix:         "<dark_gray>[<green>AutoReplant<dark_gray>] "
  enabled:        "<green>自動回種植 <bold>開啟"
  disabled:       "<red>自動回種植 <bold>關閉"
  usage:          "<yellow>用法: /<command> <on|off>"
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

## 運作原理

插件使用**兩階段事件架構**，確保掉落物計算正確且不會產生免費物品 bug：

```
【階段一】BlockBreakEvent（所有插件處理完後，HIGHEST 優先度）
玩家破壞農作物
      │
      ├─ 不符條件（非成熟作物 / 創意模式 / 個人已關閉）？
      │         └─▶ 跳過，並清除該位置的殘留紀錄
      │
      └─ 符合條件
                └─▶ 標記此位置為「待回種」（暫存，等待確認）

【階段二】BlockDropItemEvent（方塊確實破壞後才觸發，NORMAL 優先度）
      │
      ├─ 非標記位置？ ──▶ 略過
      │
      └─ 標記位置
                │
                ├─ check-seeds: true
                │       ├─ 玩家背包有對應種子？
                │       │       ├─ 是 ──▶ 消耗一顆種子
                │       │       └─ 否 ──▶ 放棄回種，所有掉落物正常生成
                │       │
                │       └─ （繼續）
                │
                └─ check-seeds: false ──▶ 不消耗種子，直接繼續
                          │
                          ▼
              下一 tick：確認該位置為空氣且下方為耕地
                          │
                          ▼
              回種（幼苗，age = 0），所有掉落物正常生成
```

**設計優點：**
- `BlockDropItemEvent` 只在方塊確實被破壞後才觸發，徹底避免「先扣種子後取消破壞」的競爭條件
- Fortune 附魔由伺服器原生計算，無需模擬
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
