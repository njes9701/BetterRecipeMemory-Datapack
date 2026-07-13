# 原始證據檔(去識別後)

這裡是 [EXPERIMENTS.md](../EXPERIMENTS.md) 所有數字的原始 `jcmd GC.class_histogram` 直方圖。

| 檔案 | 說明 |
|---|---|
| `server-A_with-datapack_T0.txt.gz` | Server A(已裝 datapack,登錄表 127),第一個時間點 |
| `server-A_with-datapack_T1.txt.gz` | Server A,第二個時間點(+31 分鐘) |
| `server-B_baseline_T0.txt.gz` | Server B(未裝,基準,登錄表 1688),第一個時間點 |
| `server-B_baseline_T1.txt.gz` | Server B,第二個時間點(+31 分鐘) |
| `redact_histogram.py` | 產生上述檔案的去識別腳本 |

## 去識別做了什麼(以及沒動什麼)

這些直方圖來自一台跑了大量第三方外掛的正式伺服器。為了只公開**本技術相關的證據**、不洩漏該伺服器
的外掛與基礎設施組成,`redact_histogram.py` 用**白名單**處理:

- **逐字保留**(官方伺服器 / JDK):`net.minecraft.*`、`org.bukkit.*`、`io.papermc.paper.*`、
  `com.destroystokyo.paper.*`、`ca.spottedleaf.*`、`com.mojang.*`、`net.kyori.*`、
  `it.unimi.dsi.fastutil.*`、`org.joml.*`,以及 `java.*` / `javax.*` / `jdk.*` / `sun.*` / `com.sun.*`
  和其原始基本型別陣列。**本報告引用的每一個 class(AdvancementProgress、CriterionProgress、
  ServerPlayer、CraftPlayer、以及 E6 用到的 HashMap / String / Instant 等)全都在白名單內,原封不動。**
- **一律隱去**:上述以外的所有 class(第三方外掛、其函式庫、以及任何基礎設施相依)—— 名稱不保留,
  整批物件數/位元組合併進單一 `redacted.thirdparty_classes` 列。

**完整性可自行驗證:** 每個檔案裡「所有保留列的位元組總和 + `redacted.thirdparty_classes` 的位元組」
恰好等於 `Total` 列。也就是說,官方列是真數字、沒被動過,只是把非官方 class 的**名稱**藏起來。

```bash
# 驗證某檔的總和一致(用 python,位元組總和約 40 億會超過 32-bit awk 的上限):
zcat server-B_baseline_T0.txt.gz | python3 -c '
import sys,re
kept=red=tot=0
for l in sys.stdin:
    if l.startswith("#"): continue
    if re.match(r"^\s*---:", l): red=int(l.split()[2]); continue
    if l.startswith("Total"): tot=int(l.split()[2]); continue   # Total <objects> <bytes>
    m=re.match(r"^\s*\d+:\s+\d+\s+(\d+)\s", l)
    if m: kept+=int(m.group(1))
print("kept+redacted=%d  Total=%d  match=%s"%(kept+red, tot, kept+red==tot))'
# → kept+redacted=4420258248  Total=4420258248  match=True
```

原始的分流名稱、主機位址、PID、取樣絕對時間也都已從檔頭移除(改為相對標記 T0/T1)。
