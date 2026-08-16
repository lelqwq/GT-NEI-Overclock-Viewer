package com.lelqwq.gtneioc;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * NEI 配置入口。NEI 按类名模式（NEI*Config）自动发现并调用 {@link #loadConfig()}，
 * 本类即向 NEI 选项页注册「电压切档箭头」开关。
 */
public class NEIGTNEIOCConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.addOption(new ArrowOption());
    }

    @Override
    public String getName() {
        return GTNEIOverclock.NAME;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
