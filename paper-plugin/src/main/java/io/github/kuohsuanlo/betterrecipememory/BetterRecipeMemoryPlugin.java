package io.github.kuohsuanlo.betterrecipememory;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * BetterRecipeMemory datapack 的配套外掛,唯一功能:
 * 吞掉 datapack 移除配方成就後,老玩家首登時原版噴的一次性孤兒清理警告
 * "Ignored advancement 'minecraft:recipes/...' ... - it doesn't exist anymore?"
 * (net.minecraft.server.PlayerAdvancements#load;每人最多 ~1572 行,存檔後自動消失)。
 * 不裝 datapack 的伺服器裝本外掛無意義(該訊息不會出現),但也無害。
 */
public class BetterRecipeMemoryPlugin extends JavaPlugin {

    static final String AUTHOR_LINE =
            "crafted by 廢土貓大 LogoCat · 廢土 · mcfallout.net";
    static final String REPO =
            "github.com/njes9701/BetterRecipeMemory-Datapack-Folia";

    @Override
    public void onLoad() {
        // onLoad 比 onEnable 早、且早於任何玩家 join(孤兒警告只在 join 時出現),
        // filter 失敗頂多回到「有噪音」的原狀,不能影響開服 → 全吞。
        try {
            OrphanAdvancementLogFilter.install();
            getLogger().info("Orphan-advancement log filter installed.");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Log filter install failed (non-fatal, noise stays): " + t);
        }
    }

    @Override
    public void onEnable() {
        // 後台作者橫幅 —— datapack + 配套外掛同屬一個作品,一併署名。
        getLogger().info("BetterRecipeMemoryFolia (Folia/Paper datapack companion) —— " + AUTHOR_LINE);
        getLogger().info(REPO);
        if (getCommand("brm") != null) {
            getCommand("brm").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "about" : args[0].toLowerCase();
        switch (sub) {
            case "help":
                s.sendMessage(ChatColor.GOLD + "BetterRecipeMemory " + ChatColor.GRAY + "commands:");
                s.sendMessage(ChatColor.YELLOW + "/brm about   " + ChatColor.GRAY + "— author & project card");
                s.sendMessage(ChatColor.YELLOW + "/brm version " + ChatColor.GRAY + "— plugin version");
                s.sendMessage(ChatColor.YELLOW + "/brm help    " + ChatColor.GRAY + "— this list");
                return true;
            case "version":
                s.sendMessage(ChatColor.GOLD + "BetterRecipeMemoryFolia "
                        + ChatColor.WHITE + getDescription().getVersion());
                return true;
            case "about":
            default:
                s.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "BetterRecipeMemory "
                        + ChatColor.GRAY + "(datapack + companion plugin)");
                s.sendMessage(ChatColor.YELLOW + "—— " + AUTHOR_LINE);
                s.sendMessage(ChatColor.WHITE + "移除原版配方成就 · 保留配方書一鍵合成 · 每玩家成就物件 1688→127");
                s.sendMessage(ChatColor.AQUA + "" + ChatColor.UNDERLINE + REPO);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("about", "help", "version");
        }
        return java.util.Collections.emptyList();
    }
}
