# 重現指南:自己動手量出這些數字

> 目標讀者:想在**自己的伺服器**上重現問題與數據的服主/工程師。
> 所有工具**免費**且大多是 JDK/系統內建;指令可直接複製貼上。
> 想直接驗算本 repo 的數據?跳到最後一節,拿 [`data/`](data/) 的原始檔算,不用開伺服器。

## 需要的軟體

| 工具 | 用途 | 哪裡來 |
|---|---|---|
| `jcmd`、`jps` | 拍記憶體直方圖 | **JDK 內建**(bin/ 目錄,跑伺服器一定有) |
| `grep`/`awk`/`zcat` | 數 log、抽數字 | Linux/macOS 內建;Windows 用 WSL 或 Git Bash |
| `python3` | 兩張直方圖相減(選配) | 大多內建 |

## 步驟 0:確認你的伺服器中招(零成本,只看 log)

```bash
# 開機 log 的成就載入數:1.21.x 原版 ≈ 1688 → 中招
grep -m1 "Loaded .* advancements" logs/latest.log

# 數 server jar 裡的配方成就檔(佐證 92% 是配方):
unzip -l versions/*/paper-*.jar | grep -c "data/minecraft/advancement/recipes/"
# → 1572
```

## 步驟 1:拍直方圖,算「每玩家成就物件」比值

```bash
# 找伺服器 PID(用跑伺服器的同一個使用者身分執行)
jps -l          # 或 pgrep -f "server.jar"

# 拍一張(⚠️ 會觸發一次 full GC,伺服器停頓 1~3 秒;別在快死的服上拍)
jcmd <PID> GC.class_histogram > histo-1.txt

# 抽三個數字
grep -E " net.minecraft.advancements.AdvancementProgress$| net.minecraft.advancements.CriterionProgress$| net.minecraft.server.level.ServerPlayer$" histo-1.txt
```

**判讀:`AdvancementProgress 數 ÷ ServerPlayer 數`。** 原版 ≈ **1688**(1.21.x),裝了本 datapack ≈ **127**。整除到小數點 = 每個玩家物件掛著整套登錄表,鐵證。

> 直方圖先做 full GC → 數到的都是**活物件**,所以比值才會精確整除;也因此它**不能**拿來量垃圾產生速率(垃圾已被清掉)。

## 步驟 2:隔半小時再拍一張,驗 lockstep

```bash
jcmd <PID> GC.class_histogram > histo-2.txt
python3 - <<'EOF'
import re
def n(f, cls):
    for l in open(f, errors='replace'):
        m = re.match(r'\s*\d+:\s+(\d+)\s+\d+\s+'+re.escape(cls)+r'$', l)
        if m: return int(m.group(1))
    return 0
for f in ['histo-1.txt','histo-2.txt']:
    ap=n(f,'net.minecraft.advancements.AdvancementProgress'); sp=n(f,'net.minecraft.server.level.ServerPlayer')
    print(f, 'AP=',ap, 'SP=',sp, 'ratio=', round(ap/sp,3) if sp else '-')
ap1,ap2=n('histo-1.txt','net.minecraft.advancements.AdvancementProgress'),n('histo-2.txt','net.minecraft.advancements.AdvancementProgress')
sp1,sp2=n('histo-1.txt','net.minecraft.server.level.ServerPlayer'),n('histo-2.txt','net.minecraft.server.level.ServerPlayer')
print('ΔSP=',sp2-sp1,' ΔAP=',ap2-ap1,' ΔAP/ΔSP=', round((ap2-ap1)/(sp2-sp1),3) if sp2!=sp1 else '-')
EOF
```

**判讀:`ΔAP ÷ ΔSP` 應該還是 1688(或 127)** —— 玩家增減多少,成就物件精確跟著增減多少倍。

## 步驟 3:量你的登入人次(churn)

```bash
# 今天(本次開機以來)
grep -c "logged in with entity id" logs/latest.log
# 昨天全天(輪替的 gz 檔;檔名依你的日期)
zcat logs/2026-*-*.log.gz | grep -c "logged in with entity id"
```

**每日成就垃圾 = 人次 × 每人次成本**(原版實測 0.55~1.0 MB,詳見 [EXPERIMENTS.md](EXPERIMENTS.md) E6)。

## 步驟 4:裝 datapack,複測

裝法見 [README](../README.md)。重啟後:步驟 0 應變 `Loaded 127 advancements`,步驟 1 比值應 ≈127。

## 步驟 5(選配):空服時的 ServerPlayer 健檢

同一張直方圖還能做一個通用健檢:**空服(沒人在線)時 `ServerPlayer` 若不為 0,代表有東西強引用著
離線玩家物件**(full GC 後仍存活 = 被強引用)。這是一個獨立於本 datapack 的通用觀測技巧,任何伺服器
都能拿來自查。

## 不開伺服器,直接驗算本 repo 的原始檔

[`data/`](data/) 內容(詳見 [`data/README.md`](data/README.md)):

| 檔案 | 內容 |
|---|---|
| `server-A_with-datapack_T0.txt.gz` / `_T1.txt.gz` | 已裝 datapack 的完整直方圖,兩個時間點 |
| `server-B_baseline_T0.txt.gz` / `_T1.txt.gz` | 未裝(基準),同兩時間點 |
| `redact_histogram.py` | 產生上述檔案的去識別腳本(官方列逐字保留,第三方 class 名稱一律隱去) |

驗算範例:

```bash
zcat server-B_baseline_T0.txt.gz | grep -E " net.minecraft.advancements.AdvancementProgress$| net.minecraft.server.level.ServerPlayer$"
#   322408 ... AdvancementProgress
#      191 ... ServerPlayer
# → 322408 ÷ 191 = 1688.000,自己按計算機
zcat server-A_with-datapack_T1.txt.gz | grep -E " net.minecraft.advancements.AdvancementProgress$| net.minecraft.server.level.ServerPlayer$"
# → 9017 ÷ 71 = 127.000
```

E6 的每玩家 bytes 精算(0.55 MB → 0.05 MB)也全部從這四個檔案推得,方法與逐步算式見 [EXPERIMENTS.md](EXPERIMENTS.md) E6。

## 安全須知(重要)

- `jcmd GC.class_histogram`:每次 1~3 秒全域停頓。正常時段沒感覺,**別在伺服器已經垂死時拍**。
- `GC.heap_dump`:凍 30 秒以上,只在測試服/維護窗做。
- 兩者都必須用**跑伺服器的同一個使用者**執行,否則 attach 失敗。
