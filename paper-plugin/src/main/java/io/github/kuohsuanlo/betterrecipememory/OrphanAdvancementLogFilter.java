package io.github.kuohsuanlo.betterrecipememory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * 掛在 root LoggerConfig 的 log4j filter。vanilla 的 logger
 * (net.minecraft.server.PlayerAdvancements)沒有獨立 LoggerConfig,
 * 事件會流經 root config,所以在這裡攔得到 —— 常見的 root LoggerConfig filter 手法。
 * 只 DENY 同時滿足「以 Ignored advancement ' 開頭」且「含 doesn't exist anymore」
 * 的訊息,其餘一律 NEUTRAL,不影響任何其他 log(含檔案 log)。
 */
public final class OrphanAdvancementLogFilter extends AbstractFilter {

    public static void install() {
        ((Logger) LogManager.getRootLogger()).addFilter(new OrphanAdvancementLogFilter());

        // 自測掛鉤:-Dbetterrecipememory.selftest=true 時對 vanilla logger 丟一則
        // 同款誘餌訊息;若 filter 有效,這行不會出現在 log(整合測試以此驗收)。
        if (Boolean.getBoolean("betterrecipememory.selftest")) {
            LogManager.getLogger("net.minecraft.server.PlayerAdvancements")
                    .warn("Ignored advancement 'minecraft:recipes/selftest/bait' in progress file selftest.json - it doesn't exist anymore?");
        }
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) return Result.NEUTRAL;

        String msg = event.getMessage().getFormattedMessage();
        if (msg != null && msg.startsWith("Ignored advancement '") && msg.contains("doesn't exist anymore")) {
            return Result.DENY;
        }
        return Result.NEUTRAL;
    }
}
