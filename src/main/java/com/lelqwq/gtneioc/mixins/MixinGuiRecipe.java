package com.lelqwq.gtneioc.mixins;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lelqwq.gtneioc.Config;
import com.lelqwq.gtneioc.TierState;

import codechicken.nei.Widget;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.scroll.ScrollContainer;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * 处理 ▲▼ 点击与页面状态重置。
 *
 * <p>
 * 绘制发生在配方区局部坐标（NEI 绘制时对 GL 平移过），而 mouseClicked 收到的是屏幕坐标。
 * 命中检测锚定第一个配方部件：反编译实机 NEI 2.8.44 的 {@code getRefIndexPosition} 可知
 * 其返回值就是「widget 位置 − guiLeft」，即原换算 {@code guiLeft + pos.x} 展开后恰为
 * {@code widget.x}。因此直接取 {@code container.getWidgets().get(0)} 的 {@code x/y}
 * （NEI 自家字段，dev 名原样保留）把局部矩形平移回屏幕空间，全程不触碰 vanilla 成员。
 *
 * <p>
 * 注意 {@code initGui}/{@code mouseClicked} 是 vanilla 方法（NEI jar 中以 SRG 名
 * {@code func_73866_w_}/{@code func_73864_a} 存在），必须 {@code remap = true}
 * 走 refmap，否则运行时因混淆名找不到注入点而崩溃（见 CLAUDE.md 踩坑备忘）。
 */
@Mixin(value = GuiRecipe.class, remap = false)
public abstract class MixinGuiRecipe {

    @Shadow(remap = false)
    public ArrayList currenthandlers;

    @Shadow(remap = false)
    public int recipetype;

    /** 滚动容器，装着本页各配方部件；widget[0] 即绘制索引 0（箭头挂载处）。 */
    @Shadow(remap = false)
    private ScrollContainer container;

    /** 每次打开/重排页面时，把该页面各 handler 的切档状态复位（关闭页面即重置）。 */
    @Inject(method = "initGui", at = @At("HEAD"), remap = true)
    private void gtneioc$resetShifts(CallbackInfo ci) {
        for (Object handler : this.currenthandlers) {
            if (handler instanceof GTNEIDefaultHandler) {
                TierState.resetShift((GTNEIDefaultHandler) handler);
            }
        }
    }

    /**
     * updateScreen HEAD：暂存配方部件 0 的绘制原点（含 yShift），供悬停与点击对齐使用。
     * 用 updateScreen 而非 drawScreen：drawScreen 是 GuiRecipe 未 override 的纯 vanilla
     * 方法，注解处理器在 NEI jar 里解析不到、refmap 无条目（运行时必崩）；updateScreen
     * 有 override、每 tick 执行，部件位置变化（翻页/滚动）后最迟 50ms 内刷新。
     */
    @Inject(method = "updateScreen", at = @At("HEAD"), remap = true)
    private void gtneioc$stashWidgetAnchor(CallbackInfo ci) {
        if (!Config.enableArrows) return;
        if (this.recipetype < 0 || this.recipetype >= this.currenthandlers.size()) return;
        Object active = this.currenthandlers.get(this.recipetype);
        if (!(active instanceof GTNEIDefaultHandler)) return;
        List<Widget> widgets = this.container.getWidgets();
        if (widgets.isEmpty()) return;
        Widget anchor = widgets.get(0);
        if (!(anchor instanceof NEIRecipeWidget)) return;
        TierState.setWidgetAnchor(
            (GTNEIDefaultHandler) active,
            new Point(
                anchor.x,
                anchor.y + ((NEIRecipeWidget) anchor).getHandlerInfo()
                    .getYShift()));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void gtneioc$handleArrowClick(int mousex, int mousey, int button, CallbackInfo ci) {
        if (!Config.enableArrows || button != 0) return;
        if (this.recipetype < 0 || this.recipetype >= this.currenthandlers.size()) return;
        Object active = this.currenthandlers.get(this.recipetype);
        if (!(active instanceof GTNEIDefaultHandler)) return;
        GTNEIDefaultHandler handler = (GTNEIDefaultHandler) active;

        Rectangle[] rects = TierState.getArrowRects(handler);
        if (rects == null) return;
        // 绘制原点（含 yShift）由 drawScreen 暂存，保证点击区与显示区精确对齐
        Point pos = TierState.getWidgetAnchor(handler);
        if (pos == null) return;
        Rectangle up = new Rectangle(rects[0]);
        up.translate(pos.x, pos.y);
        Rectangle down = new Rectangle(rects[1]);
        down.translate(pos.x, pos.y);

        if (up.contains(mousex, mousey)) {
            TierState.adjustShift(handler, 1);
            ci.cancel();
        } else if (down.contains(mousex, mousey)) {
            TierState.adjustShift(handler, -1);
            ci.cancel();
        }
    }
}
