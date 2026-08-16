package com.lelqwq.gtneioc;

/**
 * 全局配置。Mixin 注入点在每次绘制/点击时读取。
 */
public final class Config {

    /** 总开关：是否显示 ▲▼ 电压切档箭头。默认 true，保证配置加载前也生效。 */
    public static boolean enableArrows = true;

    private Config() {}
}
