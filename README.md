# AutoReplant

> 採收成熟農作物後自動回種植的 Paper / Purpur 插件

---

## 功能特色

- 偵測玩家破壞**完全成熟**的農作物時自動回種（未成熟不觸發）
- 從掉落物中自動扣除一顆種子用於回種，其餘掉落物正常掉出
- 支援 **Fortune 附魔**的掉落計算
- 每位玩家可獨立開關，設定跨重啟持久保存
- 指令短縮寫 `/arp`，方便快速切換
- 訊息與預設狀態皆可在 `config.yml` 自訂

---

## 支援農作物

| 農作物 | 觸發條件 | 消耗種子 |
|--------|---------|---------|
| 小麥 (Wheat) | 完全成熟（age 7） | 小麥種子 |
| 胡蘿蔔 (Carrot) | 完全成熟（age 7） | 胡蘿蔔（自身） |
| 馬鈴薯 (Potato) | 完全成熟（age 7） | 馬鈴薯（自身） |
| 甜菜根 (Beetroot) | 完全成熟（age 3） | 甜菜根種子 |

> **注意**：甜菜根有機率不掉落種子（0 顆），此時不會觸發回種植，所有掉落物正常給予。

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
   或自行編譯（見下方「從原始碼編譯」）。
2. 將 JAR 檔放入伺服器的 `plugins/` 資料夾。
3. 啟動或重啟伺服器。
4. 插件會自動生成 `plugins/AutoReplant/config.yml`。

---

## 指令

| 指令 | 縮寫 | 說明 |
|------|------|------|
| `/autoreplant on` | `/arp on` | 開啟自動回種植 |
| `/autoreplant off` | `/arp off` | 關閉自動回種植 |

> 設定為**個人**開關，每位玩家獨立控制，重新登入後仍會保留。

---

## 權限

| 權限節點 | 預設 | 說明 |
|---------|------|------|
| `autoreplant.use` | 所有玩家（`true`） | 允許使用 `/autoreplant` 指令 |

---

## 設定檔

### `config.yml`

```yaml
# 所有玩家的預設狀態（true = 開啟，false = 關閉）
default-enabled: true

# 種子消耗檢查
#   true  — 掉落物中必須有種子才能回種植（消耗一顆種子）
#   false — 直接回種植，不消耗種子（所有掉落物正常給予）
check-seeds: true

# 訊息設定 — 支援兩種語法（可混用）：
#   MiniMessage 標籤：<green>, <bold>, <#ff0000>, <gradient:#f00:#00f> ...
#   傳統 & 代碼：    &a, &l, &#00ff00 ...（大小寫均可）
# 佔位符：<command> 會被玩家實際輸入的指令名稱取代
messages:
  prefix:        "<dark_gray>[<green>AutoReplant<dark_gray>] "
  enabled:       "<green>自動回種植 <bold>開啟"
  disabled:      "<red>自動回種植 <bold>關閉"
  usage:         "<yellow>用法: /<command> <on|off>"
  no-permission: "<red>你沒有權限使用此指令。"
```

#### 訊息語法說明

兩種格式可**自由混用**於同一字串中：

**MiniMessage 標籤**（推薦）

| 標籤 | 效果 |
|------|------|
| `<green>` / `<red>` / `<yellow>` … | 顏色 |
| `<bold>` / `<italic>` / `<underlined>` | 格式 |
| `<#ff0000>` | 十六進位 RGB 顏色 |
| `<gradient:#ff0000:#0000ff>text</gradient>` | 漸層色 |
| `<rainbow>text</rainbow>` | 彩虹色 |
| `<reset>` | 清除所有格式 |

完整語法參考：[MiniMessage 文件](https://docs.advntr.dev/minimessage/format.html)

**傳統 `&` 代碼**（向下相容）

| 代碼 | 顏色 | 代碼 | 格式 |
|------|------|------|------|
| `&a` / `&A` | 綠色 | `&l` | 粗體 |
| `&c` / `&C` | 紅色 | `&o` | 斜體 |
| `&e` / `&E` | 黃色 | `&n` | 底線 |
| `&8` | 深灰 | `&r` | 重設 |
| `&#RRGGBB` | 十六進位色 | `&k` | 隨機字元 |

**混用範例**

```yaml
prefix: "<dark_gray>[&aAutoReplant<dark_gray>] "
enabled: "&a自動回種植 <bold>開啟 <#00ff88>✔"
```

---

## 運作原理

```
【階段一】BlockBreakEvent（所有插件處理完後）
玩家破壞農作物
      │
      ▼
  成熟的支援作物 + 玩家已開啟？ ──否──▶ 跳過（清除殘留紀錄）
      │ 是
      ▼
  標記此位置為「待回種植」

【階段二】BlockDropItemEvent（方塊確實破壞後）
      │
      ▼
  是否為標記位置？ ──否──▶ 略過
      │ 是
      ▼
  check-seeds: true？
      ├─ 是 → 掉落物中有種子？ ──否──▶ 放棄，物品正常掉落
      │           │ 是
      │           ▼
      │        消耗一顆種子（其餘掉落物正常生成）
      │
      └─ 否 → 不消耗種子（所有掉落物正常生成）
      │
      ▼
  下一 tick：確認空氣 + 耕地存在
      │
      ▼
  回種植（幼苗，age = 0）
```

---

## 從原始碼編譯

**需求**：JDK 21、Maven 3.6+

```bash
git clone <此倉庫>
cd AutoReplant
mvn package
```

編譯完成後，JAR 檔位於 `target/AutoReplant-1.0.0.jar`。

---

## 授權

本插件依照 [MIT License](LICENSE) 授權開放使用。
