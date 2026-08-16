package com.lelqwq.gtneioc;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * GT NEI Overclock —— 在 NEI 格雷配方页显示 ▲▼ 箭头，按电压等级切换查看各档 EU/t 与时长。
 *
 * <p>
 * 纯客户端 mod：Mixin 注入 {@code gregtech.nei.GTNEIDefaultHandler}（见 {@code mixins} 包），
 * 切档只影响 NEI 显示，不触碰配方与存档。与 gtpoc（完美超频）解耦：装不装 gtpoc 都能用，
 * 显示计算走同一个 {@code OverclockCalculator}，语义自动一致。
 */
@Mod(
    modid = GTNEIOverclock.MODID,
    name = GTNEIOverclock.NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;required-after:NotEnoughItems")
public class GTNEIOverclock {

    public static final String MODID = "gtneioc";
    public static final String NAME = "GT NEI Overclock Preview";

    private static Configuration config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        Config.enableArrows = config
            .getBoolean("enableArrows", Configuration.CATEGORY_GENERAL, true, "在 NEI 格雷配方页显示 ▲▼ 电压切档箭头");
        if (config.hasChanged()) {
            config.save();
        }
    }

    /** 由 NEI 选项开关（{@link ArrowOption}）调用，把开关状态写回 gtnEIOc.cfg。 */
    public static void saveConfig() {
        if (config != null) {
            config.get(Configuration.CATEGORY_GENERAL, "enableArrows", true)
                .set(Config.enableArrows);
            config.save();
        }
    }
}
