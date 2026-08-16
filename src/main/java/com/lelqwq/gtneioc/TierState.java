package com.lelqwq.gtneioc;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
import java.util.WeakHashMap;

import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * 每 handler 的切档状态与箭头按钮命中区域。
 *
 * <p>
 * 状态用 WeakHashMap 挂在 handler 实例上（NEI 的 handler 是长生命周期单例），
 * 页面关闭（GuiRecipe.initGui）时由 Mixin 重置，避免跨页面残留。
 */
public final class TierState {

    private static final Map<GTNEIDefaultHandler, Integer> SHIFTS = new WeakHashMap<>();

    private static final Map<GTNEIDefaultHandler, Rectangle[]> ARROW_RECTS = new WeakHashMap<>();

    /**
     * 最近一次 drawDescription 绘制的配方（GTNEIDefaultHandler 的配方列表 arecipes 是
     * NEI 父类字段，@Shadow 不搜父类，故借注入参数在绘制时暂存、drawExtras 时读取）。
     */
    private static final Map<GTNEIDefaultHandler, GTRecipe> CURRENT_RECIPES = new WeakHashMap<>();

    /**
     * 配方部件 0 的绘制原点（屏幕坐标，已含 HandlerInfo.yShift）。drawScreen 时暂存，
     * 供 drawExtras 计算鼠标局部坐标（悬停）与 mouseClicked 换算命中（点击对齐）。
     */
    private static final Map<GTNEIDefaultHandler, Point> WIDGET_ANCHORS = new WeakHashMap<>();

    private TierState() {}

    public static int getShift(GTNEIDefaultHandler handler) {
        Integer shift = SHIFTS.get(handler);
        return shift == null ? 0 : shift;
    }

    public static void setShift(GTNEIDefaultHandler handler, int shift) {
        SHIFTS.put(handler, shift);
    }

    public static void resetShift(GTNEIDefaultHandler handler) {
        SHIFTS.remove(handler);
        CURRENT_RECIPES.remove(handler);
        WIDGET_ANCHORS.remove(handler);
    }

    /** 记录配方部件 0 的绘制原点（屏幕坐标，含 yShift）。 */
    public static void setWidgetAnchor(GTNEIDefaultHandler handler, Point anchor) {
        WIDGET_ANCHORS.put(handler, anchor);
    }

    /** @return 配方部件 0 的绘制原点，可能为 null（从未绘制过）。 */
    public static Point getWidgetAnchor(GTNEIDefaultHandler handler) {
        return WIDGET_ANCHORS.get(handler);
    }

    /** 记录最近一次 drawDescription 的配方（绘制顺序保证 drawExtras(0) 时读到配方 0）。 */
    public static void setCurrentRecipe(GTNEIDefaultHandler handler, GTRecipe recipe) {
        CURRENT_RECIPES.put(handler, recipe);
    }

    /** @return 该 handler 最近绘制的配方，可能为 null（从未绘制过）。 */
    public static GTRecipe getCurrentRecipe(GTNEIDefaultHandler handler) {
        return CURRENT_RECIPES.get(handler);
    }

    public static void adjustShift(GTNEIDefaultHandler handler, int delta) {
        setShift(handler, getShift(handler) + delta);
    }

    /** 记录最近一次绘制的 ▲/▼ 按钮（配方区局部坐标），供点击命中检测。 */
    public static void setArrowRects(GTNEIDefaultHandler handler, Rectangle up, Rectangle down) {
        ARROW_RECTS.put(handler, new Rectangle[] { up, down });
    }

    /** @return [▲, ▼] 或 null（该 handler 从未绘制过箭头）。 */
    public static Rectangle[] getArrowRects(GTNEIDefaultHandler handler) {
        return ARROW_RECTS.get(handler);
    }
}
