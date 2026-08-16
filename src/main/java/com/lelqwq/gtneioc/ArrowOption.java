package com.lelqwq.gtneioc;

import codechicken.nei.config.OptionButton;

/**
 * NEI 选项页上的「电压切档箭头」开关按钮。状态即 {@link Config#enableArrows}，
 * 点击后同步写回 gtnEIOc.cfg（{@link GTNEIOverclock#saveConfig()}）。
 */
public class ArrowOption extends OptionButton {

    public ArrowOption() {
        super("gtneioc.arrows", null, "", "");
    }

    @Override
    public boolean onClick(int button) {
        Config.enableArrows = !Config.enableArrows;
        GTNEIOverclock.saveConfig();
        return true;
    }

    @Override
    public String getPrefix() {
        return "电压切档箭头";
    }

    @Override
    public String getButtonText() {
        return Config.enableArrows ? "已开启" : "已关闭";
    }
}
