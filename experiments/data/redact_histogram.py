#!/usr/bin/env python3
"""
Allowlist-only jcmd GC.class_histogram redactor.
Keeps official server/JDK rows VERBATIM; collapses every third-party/plugin/
library class into a single aggregate line so no plugin name, package, or
per-class count survives. Totals still reconcile (kept rows + aggregate = Total).
"""
import re, sys, gzip

# Only these package roots are treated as "official vanilla server / JDK".
# Everything else (any plugin, its bundled libraries, database/cache drivers,
# and other third-party dependencies) is redacted — keep only NMS/Bukkit/Paper/JDK.
ALLOW = (
    "net.minecraft.",
    "org.bukkit.",            # incl. org.bukkit.craftbukkit.*
    "io.papermc.paper.",
    "com.destroystokyo.paper.",
    "ca.spottedleaf.",        # Paper Moonrise / dataconverter
    "com.mojang.",            # Mojang libs (brigadier, datafixerupper, serialization, authlib)
    "net.kyori.",             # Adventure (Paper-bundled)
    "it.unimi.dsi.fastutil.", # vanilla-bundled, reveals nothing
    "org.joml.",              # math, vanilla-bundled
    "java.", "javax.", "jdk.", "sun.", "com.sun.",
)
PRIMS = set("BCDFIJSZV")

def core(token):
    """Strip module suffix like ' (java.base@25.0.3)'."""
    return re.sub(r"\s*\([^)]*\)\s*$", "", token).strip()

def is_safe(token):
    t = core(token)
    # peel array markers
    while t.startswith("["):
        t = t[1:]
    if not t:
        return False
    if t[0] in PRIMS and (len(t) == 1):       # primitive array element
        return True
    if t.startswith("L") and t.endswith(";"): # object array element
        t = t[1:-1]
    return any(t.startswith(p) or t.rstrip(";") == p.rstrip(".") for p in ALLOW)

ROW = re.compile(r"^(\s*)(\d+):(\s+)(\d+)(\s+)(\d+)(\s+)(.+?)\s*$")

def process(lines, note):
    out = []
    out.append("# BetterRecipeMemory reproducibility sample — jcmd GC.class_histogram")
    out.append("# Official rows (net.minecraft / org.bukkit / io.papermc / JDK) are VERBATIM.")
    out.append("# All third-party / plugin / library class names are withheld and collapsed")
    out.append("# into a single aggregate line (marked with the ---: rank); its object & byte")
    out.append("# counts are preserved so the Total still reconciles.")
    out.append("# note=%s" % note)
    red_obj = red_bytes = red_n = 0
    total_line = None
    for ln in lines:
        s = ln.rstrip("\n")
        if s.startswith("#"):
            continue                      # drop original shard/host/pid header
        if re.match(r"^\d+:\s*$", s):
            continue                      # drop jcmd pid echo line
        if re.match(r"^\s*Total\s+\d+\s+\d+\s*$", s):
            total_line = s
            continue
        m = ROW.match(s)
        if not m:
            if s.strip() == "":
                continue
            continue                      # drop anything unrecognized (paranoid)
        token = m.group(8)
        if is_safe(token):
            out.append(s)                 # verbatim official row
        else:
            red_obj += int(m.group(4))
            red_bytes += int(m.group(6))
            red_n += 1
    out.append("  ---:%s%d%s%d   redacted.thirdparty_classes  (names withheld for privacy)"
               % ("      ", red_obj, "      ", red_bytes))
    if total_line:
        out.append(total_line)
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    src, dst, note = sys.argv[1], sys.argv[2], sys.argv[3]
    op = gzip.open if src.endswith(".gz") else open
    with op(src, "rt", errors="replace") as f:
        lines = f.readlines()
    txt = process(lines, note)
    with gzip.open(dst, "wt") as f:
        f.write(txt)
    # sanity report to stderr
    kept = [l for l in txt.splitlines() if re.match(r"^\s*\d+:", l)]
    print("  %s -> %s : kept %d official rows" % (src.split('/')[-1], dst.split('/')[-1], len(kept)), file=sys.stderr)
