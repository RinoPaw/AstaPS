# AstaPS

[English](README.md) · 繁體中文

一個基於 Grasscutter 的《原神》**7.1.0** 私人伺服器。

> 這是一個研究與保存性質的專案，與 HoYoverse / miHoYo 沒有任何從屬、背書或關聯，亦不作商業用途。

如果你可以修復錯誤，請幫助我。

## 這是什麼

- **內容跟得上版本。** 怪物與裝置的生成資料對齊 7.1.0，深境螺旋輪換、秘境、聖遺物商店、戰令等營運內容都在。
- **撐得住出事的那天。** 寫庫拆成四個有界執行緒池，塞滿時回壓而不是把玩家的存檔丟掉；某個世界在 tick 裡拋例外不再讓其他所有人的世界一起停。
- **出問題看得見。** 狀態日誌定時輸出 CPU、記憶體、GC 和每個執行緒池的佇列深度，`/api/status` 也以 HTTP 提供同一份數字。
- **全英文。** 原始碼、註解、提交訊息、指令輸出皆然。

## 需求

| | |
|---|---|
| Java | 編譯與執行皆需要 JDK 21。建置強制使用 Java 21 toolchain 與 `--release 21`。 |
| MongoDB | Community Server，啟動伺服器前必須先跑起來。 |
| 遊戲客戶端 | 原神 7.1.0。官方客戶端會校驗 region 的簽名，要連私服需要另外打客戶端補丁，例如 [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch)。AstaPS 本身不附帶補丁。 |
| 資源檔 | 7.1.0 的資源包，解壓到伺服器目錄下的 `resources/`。如果你沒有資源檔，可以透過[該連結](https://github.com/MeChen618/AstaPS-Resource)下載。 |

## 編譯

```
./gradlew jar -PskipHandbook=1 -PjarFilename=grasscutter
```

`grasscutter.jar` 會產生在專案根目錄。拿掉 `-PskipHandbook=1` 後，建置會在手冊來源資料與 npm 可用時產生遊戲內手冊；缺少時會自動略過。

Windows 可用 `.\gradlew.bat` 搭配相同參數，或直接執行 `gradlew-jar.bat`。`ServerStart.cmd` 會啟動產生的 `grasscutter.jar`。

## 執行

1. 啟動 MongoDB。
2. 把 7.1.0 資源包放進 `resources/`。
3. 先跑一次 jar。它會寫出 `config.json`，缺少必要東西時會停下來。
4. 再跑一次。Dispatch 預設監聽 `8088`，遊戲伺服器 `22101`。
5. 把客戶端指向 dispatch。用 Fiddler、mitmproxy 之類的代理可以，客戶端補丁也可以。

### 帳號

沒有註冊網頁。建立帳號有兩條路：

- **從主控台。** `account create <使用者名稱> [uid] [密碼]`
- **登入時直接註冊。** 用一個沒人占用的名字登入就等於註冊。開啟 `account.useIntegrationPassword` 後，在使用者名稱欄填 `帳號&&密碼`、密碼欄留空即可，很方便在代理一堆客戶端時用。

密碼以 BCrypt 雜湊儲存。主控台需要 `server.game.enableConsole` 設為 `true` 才會接受輸入。

## 指令

`help` 會列出全部。幾個常用的：

| | |
|---|---|
| `give` | 角色、武器、聖遺物、材料。預設 100 級。 |
| `account` | 建立、刪除帳號，重設密碼。 |
| `banip` / `unbanip` | 封禁位址。封 IP 會連帶封掉從該位址登入的帳號。 |
| `sysmail` | 對全體玩家發送系統郵件。 |

## TPS 射擊玩法（7.1）

至冬的第三人稱射擊玩法可以玩：槍械和手榴彈裝備在角色原本的武器旁邊，在 TPS 秘境裡瞄準射擊。指令需要 `player.tps` 和 `player.enterdungeon` 權限。

**1. 取得武器**

```
/tps give          全部八把 TPS 武器（224001–224008），或指定一把：/tps give 224001
/tps accessory     解鎖已擁有武器的全部配件
```

**2. 進入 TPS 秘境**

```
/dungeon 10955                       射擊靶場
/dungeon 10953、10960 到 10964        灰原（Emerged Grey Field）各關
```

進入後，隊伍會換成 TPS 旅行者（與你的旅行者同性別，20 級），裝備你的 TPS 配裝，第一次進入時是 224001。在秘境裡換的武器會保存成你的配裝。離開秘境後隊伍會恢復原狀。

**3. 秘境外**

任何角色都能裝備 TPS 武器，方便試用：

```
/tps wear 224001 224004    場上角色裝備一把步槍和一顆手榴彈（最多 2 把槍、1 顆手榴彈）
/tps refill                補滿全部彈藥
```

彈藥處理仍有部分屬於實驗性質。伺服器端的實作細節、`/tps ammo` 的切換選項，以及尚未確定的部分，見 [docs/tps/README.md](docs/tps/README.md)。

## 授權

本專案採用 **GNU General Public License v3.0**，見 [`LICENSE`](LICENSE)。

`LICENSE-ClassGraph.txt` 不是本專案的授權條款。ClassGraph 是一個 MIT 授權的相依套件，它的 class 會被打包進 `grasscutter.jar`，而 MIT 只要求該聲明隨之一起帶著。

## 致謝

本伺服器基於 **Grasscutter**。參考專案：**LunaGC**、**HunkyMeow**。

「需求」中提到的客戶端補丁 [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch) 由 **capyb2222** 維護，基於 [xeondev](https://git.xeondev.com/reversedrooms/hk4e-patch) 的原始 hk4e-patch 與 [oureveryday](https://github.com/oureveryday/) 的原始 hk4e-patch-universal。它是獨立專案，以自己的 GPL-3.0 授權發布。

本倉庫根部的匯入提交中，以姓名列出了它所承載的各位作者。

## Proto 來源

協議定義來自 [genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol)。
