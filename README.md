# BetterRecipeMemory

移除原版約 1,572 條**配方成就**(`minecraft:recipes/*`),把每位上線玩家物化的成就物件從
**1688 → 127**(記憶體 -92%);同時用「首登一次性觸發」把每位玩家的**配方書塞滿** ——
玩家「點配方一鍵帶料」的合成體驗完全不變,甚至更好。

*crafted by 廢土貓大 LogoCat · 廢土 · mcfallout.net*

本 repo 含兩個部分:

| 目錄 | 產物 | 角色 |
|---|---|---|
| [`datapack/`](datapack/) | `dist/BetterRecipeMemory.zip` | **主體** —— 移除配方成就 + 首登塞滿配方書 |
| [`paper-plugin/`](paper-plugin/) | `dist/BetterRecipeMemoryPlugin-26.2-1.jar` | **配套** —— 吞掉 datapack 造成的「孤兒成就」log 洪水(成就存檔開=可選;關=必裝,見 E4) |

- 適用:Paper / MC 26.2(pack_format 107;其他版本見〔技術版〕的 pack_format 一節)
- 安裝:zip 丟進主世界 `datapacks/`;jar 丟進 `plugins/`;重啟生效
- 實測:一台外掛數量龐大的 Paper 26.2 伺服器通過 —— `Loaded 1688 advancements → 127`、recipes 數不變、零載入錯誤

### 作者 / 查詢指令

| 位置 | 指令 | 顯示 |
|---|---|---|
| 外掛 | `/brm about` · `/brm help` · `/brm version` | 作者與專案卡片 |
| datapack | `/function fallout:about` | 作者與專案卡片(遊戲內) |
| 後台 | 伺服器啟動時 | 兩者都印出 `crafted by … LogoCat · mcfallout.net` |

## 🔬 實驗區(正式環境實測)

| 文件 | 內容 |
|---|---|
| [`experiments/EXPERIMENTS.md`](experiments/EXPERIMENTS.md) | 五組實驗 + 完整算式:比值(127 vs 1688,精確整除)、時間序列 lockstep、每玩家 bytes 精算(E6)、log 噪音 A/B |
| [`experiments/data/`](experiments/data/) | **原始證據** —— `jcmd GC.class_histogram` 直方圖。官方列(net.minecraft / org.bukkit / io.papermc / JDK)逐字保留;第三方 class 名稱一律隱去,見 [`experiments/data/README.md`](experiments/data/README.md) |
| [`experiments/REPRODUCE.md`](experiments/REPRODUCE.md) | 逐步教學:用 JDK 內建 `jcmd` + `grep` 在你自己的伺服器上量出同樣的數字 |

一句話結論:**每玩家成就物件 1688 → 127(-92.5%);數量與線上玩家數完全 lockstep,六次取樣比值全部整除 —— 機制級因果,不是統計巧合。**

---

## 一、白話版

### 問題是什麼

為了做「撿到鐵錠 → 配方書自動出現鐵劍」這個功能,原版 Minecraft 替**每一位玩家**掛了
**約 1,572 個感應器**(配方成就),每個全天候盯著「他撿到某某材料了嗎?」。感應器又肥又重
—— 每人約 **1,688 個活物件(≈0.5–1 MB)**,玩家每次上線重新掛一整套、下線變垃圾。玩家多、
重登頻繁的伺服器,記憶體就是被這個灌爆的。

### 玩家真正用到的是什麼

玩家在意的體驗 —— 開工作台、點配方、材料自動排好 —— 讀的是**配方書**(哪些配方打了勾),
根本不看感應器。感應器唯一的工作就是幫那張清單慢慢打勾,順便跳一個沒人看的 toast。

### 這個 datapack 做的事

1. **把約 1,572 個感應器整批拆掉**(datapack `filter` 把配方成就從遊戲移除)
2. **玩家第一次進服的瞬間,直接把整本配方書打滿勾**(隱形成就觸發 `recipe give`,一次性、之後永不重跑)

配方書只是一張名字列表(~65 KB),而被拆掉的感應器約 1 MB。

### 配套外掛在幹嘛

拆掉感應器後,**既有玩家**的存檔裡還留著舊感應器紀錄。他們下次上線,遊戲會對每一條印一行
警告(每人最多 ~1,572 行)。無害,但會洗版後台。配套外掛就是把**這一種訊息**(只有這種)吞掉。
要不要裝取決於你的成就存檔設定(見 [E4](experiments/EXPERIMENTS.md)):vanilla 存檔開 →
下次存檔即清、數天自然消退,可裝可不裝;vanilla 成就存檔**關**(持久化交給外部同步系統)→
檔案永不重寫,**每次登入重印一整批**,此時實質必裝。

### 玩家會感覺到什麼

| | 之前 | 之後 |
|---|---|---|
| 點配方一鍵帶料 | ✅ | ✅ 一模一樣 |
| 合成本身 | ✅ | ✅(從來不歸這系統管) |
| 新手配方書 | 要慢慢撿材料解鎖 | **進服就全滿** |
| 「配方已解鎖」toast | 常常跳 | 首登跳一批,之後安靜 |

---

## 二、技術版

### datapack 機制

**1. Filter 移除配方成就**(`datapack/pack.mcmeta`):

```json
{
  "pack": {
    "pack_format": 107,
    "min_format": 107,
    "max_format": 107,
    "description": "..."
  },
  "filter": {
    "block": [
      {"namespace": "minecraft", "path": "advancement/recipes/.*"},
      {"namespace": "minecraft", "path": "advancements/recipes/.*"}
    ]
  }
}
```

datapack `filter`(MC 1.19+ 官方機制)把更低優先層(含原版內建 pack)中符合 pattern 的檔案從
載入剔除。26.2 的實際目錄是**單數** `data/minecraft/advancement/recipes/`(1,572 檔,佔全部
1,703 條成就的 92%,已從 server jar 清點);複數那條 pattern 是舊版命名保險,匹配不到任何東西、無害。

> `min_format` / `max_format` 為必填:26.2 的 metadata codec(NMS `packs/metadata/pack/PackFormat.java`)
> 規定 pack_format > 81 的包必須宣告這兩欄;缺少時每次開機噴一行
> `Error reading pack metadata, attempting fallback type`(fallback 仍會正確載入,但別依賴它)。
> int 值 107 會自動展開為 min=107.0、max=107.*;**不可**加 `supported_formats`(>81 已棄用,加了直接報錯)。
>
> `pack_format: 107` 來源:server jar 的 `version.json` → `pack_version.data_major`
> (`unzip -p paper-26.2.jar version.json`)。

移除後:成就登錄表 1703 → 131(含本包 +1);`PlayerAdvancements` 不再為任何玩家物化這些條目
→ 每上線玩家的 `AdvancementProgress` 物件 1688 → 127;join 時的全登錄表掃描
(`checkForAutomaticTriggers` / listener 註冊)規模同步縮減。

**2. 首登一次性塞滿配方書**:

- `datapack/data/fallout/advancement/fill_recipe_book.json`:無 `display`(隱形、無 toast)的成就,
  criterion = `minecraft:tick` trigger → 玩家第一次出現在該伺服器時自動達成
- `rewards.function` = `fallout:fill_recipe_book` → 以該玩家身分執行 `recipe give @s *`
- 達成狀態持久化於玩家成就檔;配方書清單持久化於玩家檔 `recipeBook` → **同一玩家永不重複觸發,零週期工作**

### 配套外掛機制(`paper-plugin/`)

- **一個 log4j filter,別無其他**:`onLoad` 時掛到 root LoggerConfig(vanilla 的
  `net.minecraft.server.PlayerAdvancements` logger 沒有獨立 config,事件流經 root,攔得到)
- 只 `DENY` 同時滿足「以 `Ignored advancement '` 開頭」且「含 `doesn't exist anymore`」的訊息;其餘一律 `NEUTRAL`
- 失敗安全:install 包在 `catch (Throwable)`,掛不上頂多回到「有噪音」的原狀
- 自測掛鉤:`-Dbetterrecipememory.selftest=true` 開機時對 vanilla logger 丟一則同款誘餌訊息;filter 有效則該行不出現在 log
- 建置:`JAVA_HOME=<jdk25> mvn package`(paper-api 26.2;log4j-core 為 provided,Paper runtime 內建)

### 成本 / 收益

| 項目 | 拆掉的 | 加回的 |
|---|---|---|
| 每上線玩家 heap | ~0.5–1 MB(1688 物件 + map) | ~65 KB(recipeBook set,key 為共享實例) |
| 玩家檔磁碟 | 成就 JSON 大幅縮減 | recipeBook 清單 ~10–20 KB(gzip) |
| join 時物化成本 | 1688 物件/次 | 127 物件/次 |

### 安裝

```bash
# datapack(必要):放主世界(server.properties 的 level-name)的 datapacks/,重啟生效
cp dist/BetterRecipeMemory.zip <server>/<level-name>/datapacks/

# 配套外掛(可選,消 log 噪音):
cp dist/BetterRecipeMemoryPlugin-26.2-1.jar <server>/plugins/
```

### 部署前必查:limited_crafting 必須為 false

唯一遊戲性風險:若 gamerule「只能合成已解鎖配方」開啟,拆掉配方成就後、首登觸發前的瞬間會無法合成。
**26.2 起 gamerule 已改為命名空間制**:舊名 `doLimitedCrafting` 更名為 `minecraft:limited_crafting`
(遷移碼:NMS `GameRuleRegistryFix.java:61`;定義 `gamerules/GameRules.java:52`,預設 false)。查詢:

```
gamerule minecraft:limited_crafting     # 必須回 false(舊指令 gamerule doLimitedCrafting 在 26.2 會報錯)
```

### 驗證

1. 開機 log:`Loaded ~127 advancements`(原 1688)+ `Found new data pack ... loading it automatically`;裝了配套外掛則多一行 `Orphan-advancement log filter installed.`
2. heap:`AdvancementProgress ÷ ServerPlayer` 應 ≈127(原 1688);量法 = `jcmd <pid> GC.class_histogram`
3. 遊戲性:合成任意物品(驗 limited_crafting)、拿一個 story 成就(驗一般成就正常)、開配方書點配方(驗一鍵帶料)

### 已知邊角與回滾

- 玩家成就檔中的舊配方成就條目成為孤兒:join 載入時原版對每條印一行 `Ignored advancement ... doesn't exist anymore`(最多 ~1572 行/人)。**成就存檔開啟**(vanilla 預設)→ 下次存檔即清,一生一次,數天消退;**成就存檔關閉**(`advancements.disable-saving: true`)→ 檔案永不重寫,**每次 join 重印一整批**,不會消退 —— 此時配套外掛必裝(實測見 EXPERIMENTS E4)
- 未來**新增**配方時,已觸發過的老玩家不會自動解鎖新配方(手動排料仍可合成);把 `fill_recipe_book.json` 換檔名 = 全員重新觸發
- 回滾:刪 zip(+ jar)→ 重啟。玩家的配方解鎖會在遊玩中自然重新累積,無永久損失

---

## 三、給 AI 的結構化脈絡(machine-oriented)

> 供接手維運的 AI/工具快速載入脈絡,人類可略過。

```yaml
artifacts:
  - type: minecraft-datapack
    source: datapack/                               # pack.mcmeta + data/
    deploy_file: dist/BetterRecipeMemory.zip        # cd datapack && zip -r ../dist/... pack.mcmeta data/
    target: Paper/MC 26.2 (pack_format 107; server jar version.json -> pack_version.data_major)
    scope: per-world (<level-name>/datapacks/), restart to apply, auto-enabled
    required: true
  - type: paper-plugin
    source: paper-plugin/                           # maven, JAVA_HOME=jdk25 mvn package
    deploy_file: dist/BetterRecipeMemoryPlugin-26.2-1.jar
    scope: plugins/, restart to apply
    required: false                                 # log-noise only; harmless without the datapack
    mechanism: onLoad installs a log4j AbstractFilter on root LoggerConfig; DENY iff
               startsWith("Ignored advancement '") && contains("doesn't exist anymore");
               selftest=-Dbetterrecipememory.selftest=true emits a decoy to verify swallow

problem_statement:
  symptom: heap fills over hours on high-population / high-relog servers
  root_cause: vanilla materialises every advancement's progress object per online player;
              of 1703 registry entries, 1572 are minecraft:recipes/*
  evidence_chain:
    - heap histogram diff (jcmd GC.class_histogram, two samples subtracted): AdvancementProgress
      grows in proportion to ServerPlayer, ratio constant at 1688
    - controlled A/B (registry filtered vs not) shows the materialisation is vanilla behaviour
    - per-player retained ~0.5-1 MB of advancement objects; churn garbage dominates the fill rate
  measured_result:
    before: {loaded_advancements: 1688, per_player_objects: 1688}
    after:  {loaded_advancements: 127,  per_player_objects: 127, recipes_loaded: unchanged, load_errors: 0}

design_decisions:
  - filter, not override: removes at the registry layer so join scan + materialisation both shrink
  - singular path advancement/ (26.2 real) + plural advancements/ (legacy safety net, zero matches)
  - recipe-book compensation = invisible advancement (minecraft:tick trigger, no display)
    + rewards.function running recipe give @s *
  - considered and dropped: a periodic recipe give @a * (re-does completed work every cycle);
    on-first-seen grant state persists -> zero repeat work

invariants_and_gotchas:
  - pack.mcmeta must include min_format+max_format (26.2 codec requires it for format>81); must NOT
    include supported_formats (deprecated for >81 -> hard error); NMS: packs/metadata/pack/PackFormat.java
  - gamerule minecraft:limited_crafting must be false (namespaced since 26.2; old doLimitedCrafting
    rejected by brigadier; NMS: GameRuleRegistryFix.java:61, gamerules/GameRules.java:52 default false)
  - do NOT strip "completed" recipe advancements from player files while keeping the registry entries
    -- that is a different, broken fix (listeners re-arm and idle forever)
  - reload gotcha: minecraft:reload can wipe plugin-registered custom recipes -- restart to apply
  - acceptance metric: boot log Loaded ~127 advancements; heap ratio AdvancementProgress/ServerPlayer ~= 127
```

---

*BetterRecipeMemory — crafted by 廢土貓大 LogoCat · 廢土 · mcfallout.net*
