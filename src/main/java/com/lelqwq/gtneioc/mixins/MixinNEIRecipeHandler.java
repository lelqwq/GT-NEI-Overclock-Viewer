package com.lelqwq.gtneioc.mixins;

import java.awt.Point;
import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lelqwq.gtneioc.Config;
import com.lelqwq.gtneioc.IGTNEIOCAmperage;
import com.lelqwq.gtneioc.TierState;

import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.enums.GTValues;
import gregtech.api.objects.overclockdescriber.EUNoOverclockDescriber;
import gregtech.api.objects.overclockdescriber.EUOverclockDescriber;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * 切档核心：在 {@code GTNEIDefaultHandler} 的绘制入口把 describer 临时替换为目标电压档，
 * 绘制管线（能量/时长/档位名）自动跟随；绘制结束后恢复原 describer，并画 ▲▼ 按钮与超频级数。
 *
 * <p>
 * 参与切档的类型为精确 {@code EUOverclockDescriber} 或精确 {@code EUNoOverclockDescriber}
 * （482 物品查找路径的惰性默认，档位恒为 LV）：聚变（EUOverclockDescriber 的子类）/蒸汽/
 * 质量发生器等自定义子类的特殊规则不因替换而丢失（它们拿不到箭头，页面原样显示）。
 */
@Mixin(value = GTNEIDefaultHandler.class, remap = false)
public class MixinNEIRecipeHandler {

    /** GT_VALUES 的 MAX 档（VN 数组第 14 项），2.8.4 实测存在。 */
    private static final int MAX_TIER = 14;

    @Shadow(remap = false)
    protected OverclockDescriber overclockDescriber;

    /** drawDescription HEAD 暂存原 describer，drawExtras TAIL 恢复。 */
    private OverclockDescriber gtneioc$scratchDescriber;

    /** 参与切档的 describer 类型（精确匹配，排除聚变等子类）。 */
    private static boolean gtneioc$isEligibleDescriber(OverclockDescriber describer) {
        return describer != null && (describer.getClass() == EUOverclockDescriber.class
            || describer.getClass() == EUNoOverclockDescriber.class);
    }

    @Inject(method = "drawDescription", at = @At("HEAD"), remap = false)
    private void gtneioc$shiftDescriber(GTNEIDefaultHandler.CachedDefaultRecipe cachedRecipe, CallbackInfo ci) {
        if (!Config.enableArrows) return;
        GTNEIDefaultHandler handler = (GTNEIDefaultHandler) (Object) this;
        // 配方列表 arecipes 是 NEI 父类字段（@Shadow 不搜父类），配方经 TierState 暂存传递；
        // 暂存放在类型检查之前：首帧 describer 可能为 null（482 惰性默认尚未赋值）
        TierState.setCurrentRecipe(handler, cachedRecipe.mRecipe);
        OverclockDescriber describer = this.overclockDescriber;
        if (!gtneioc$isEligibleDescriber(describer)) return;
        byte minTier = GTUtility.getTier(cachedRecipe.mRecipe.mEUt);
        // 482 惰性默认 EUNoOverclockDescriber 档位恒为 LV，真实基础档取配方原生档
        int baseTier = Math.max(minTier, describer.getTier());
        int target = Math.max(minTier, Math.min(MAX_TIER, baseTier + TierState.getShift(handler)));
        if (target == describer.getTier()) return;
        this.gtneioc$scratchDescriber = describer;
        this.overclockDescriber = new EUOverclockDescriber(
            (byte) target,
            ((IGTNEIOCAmperage) describer).gtneioc$getAmperage());
    }

    @Inject(method = "drawExtras", at = @At("TAIL"), remap = false)
    private void gtneioc$restoreAndDrawArrows(int aRecipeIndex, CallbackInfo ci) {
        if (this.gtneioc$scratchDescriber != null) {
            this.overclockDescriber = this.gtneioc$scratchDescriber;
            this.gtneioc$scratchDescriber = null;
        }
        // 每页只画一组箭头（挂在第一个配方部件上），避免多配方页重复绘制
        if (!Config.enableArrows || aRecipeIndex != 0) return;
        OverclockDescriber describer = this.overclockDescriber;
        if (!gtneioc$isEligibleDescriber(describer)) return;
        GTNEIDefaultHandler handler = (GTNEIDefaultHandler) (Object) this;
        // 绘制顺序保证此处读到的是索引 0 的配方（由 drawDescription 暂存）
        GTRecipe recipe = TierState.getCurrentRecipe(handler);
        if (recipe == null) return;

        byte minTier = GTUtility.getTier(recipe.mEUt);
        int shift = TierState.getShift(handler);
        int baseTier = Math.max(minTier, describer.getTier());
        int current = Math.max(minTier, Math.min(MAX_TIER, baseTier + shift));
        boolean canUp = current < MAX_TIER;
        boolean canDown = current > minTier;

        int amperage = ((IGTNEIOCAmperage) describer).gtneioc$getAmperage();
        OverclockCalculator calculator = new EUOverclockDescriber((byte) current, amperage).createCalculator(
            new OverclockCalculator().setRecipeEUt(recipe.mEUt)
                .setDuration(recipe.mDuration),
            recipe);
        calculator.calculate();
        int overclocks = calculator.getPerformedOverclocks();

        // 配方区右下角：▲/▼ 按钮并排于 NEI 收藏/合成表覆盖按钮列的左侧（动态检测其位置，永不重叠），
        // 档位名 (+N OC) 右对齐于按钮上方。NEI 按钮位置复刻 getDefatulButtons 公式：
        // x = min(168, w) - 12、覆盖按钮 y = 内容高 - 18、收藏按钮 y = 覆盖 - 13（内容坐标，yShift 相消）。
        // 按钮风格复刻 LayoutStyleMinecraft.drawButton：LayoutManager.drawButtonBackground 背景 +
        // 居中无阴影文字；NEI 配色常态 0xE0E0E0/悬停 0xFFFFA0/禁用 0x601010，type 0=禁用 1=常态 2=悬停。
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        HandlerInfo info = GuiRecipeTab.getHandlerInfo((IRecipeHandler) (Object) this);
        int btnSize = 12;
        int neiBtnX = Math.min(168, info.getWidth()) - btnSize;
        int overlayY = info.getHeight() - 12 - 6;
        boolean showOverlay = info.getShowOverlayButton();
        boolean showFavorite = NEIClientConfig.favoritesEnabled() && info.getShowFavoritesButton();
        int yBtn = showOverlay ? overlayY : (showFavorite ? overlayY - 13 : info.getHeight() - btnSize - 4);
        int xDown = neiBtnX - 2 - btnSize;
        int xUp = xDown - 2 - btnSize;

        // 悬停检测：drawScreen 暂存的部件原点（含 yShift）+ LWJGL 鼠标换算内容局部坐标
        Point anchor = TierState.getWidgetAnchor(handler);
        Rectangle upRect = new Rectangle(xUp, yBtn, btnSize, btnSize);
        Rectangle downRect = new Rectangle(xDown, yBtn, btnSize, btnSize);
        boolean hoverUp = false;
        boolean hoverDown = false;
        if (anchor != null) {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int localX = Mouse.getX() * res.getScaledWidth() / mc.displayWidth - anchor.x;
            int localY = res.getScaledHeight() - Mouse.getY() * res.getScaledHeight() / mc.displayHeight - 1 - anchor.y;
            hoverUp = upRect.contains(localX, localY);
            hoverDown = downRect.contains(localX, localY);
        }
        TierState.setArrowRects(handler, upRect, downRect);

        String tierText = GTValues.VN[current] + (overclocks > 0 ? " (+" + overclocks + " OC)" : "");
        font.drawStringWithShadow(tierText, neiBtnX - 2 - font.getStringWidth(tierText), yBtn - 4 - 8, 0xFFFFFF);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        LayoutManager.drawButtonBackground(xUp, yBtn, btnSize, btnSize, true, hoverUp ? 2 : (canUp ? 1 : 0));
        font.drawString(
            "▲",
            xUp + (btnSize - font.getStringWidth("▲")) / 2,
            yBtn + (btnSize - 8) / 2,
            hoverUp ? 0xFFFFA0 : (canUp ? 0xE0E0E0 : 0x601010));
        LayoutManager.drawButtonBackground(xDown, yBtn, btnSize, btnSize, true, hoverDown ? 2 : (canDown ? 1 : 0));
        font.drawString(
            "▼",
            xDown + (btnSize - font.getStringWidth("▼")) / 2,
            yBtn + (btnSize - 8) / 2,
            hoverDown ? 0xFFFFA0 : (canDown ? 0xE0E0E0 : 0x601010));
    }
}
