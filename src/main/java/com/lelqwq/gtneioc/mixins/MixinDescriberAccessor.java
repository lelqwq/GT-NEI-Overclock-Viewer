package com.lelqwq.gtneioc.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.lelqwq.gtneioc.IGTNEIOCAmperage;

import gregtech.api.objects.overclockdescriber.EUNoOverclockDescriber;

/**
 * 给 {@code EUNoOverclockDescriber} 注入公开的 amperage 读取方法，
 * 供 {@code MixinNEIRecipeHandler} 切档重建 describer 时保留安培数。
 */
@Mixin(value = EUNoOverclockDescriber.class, remap = false)
public class MixinDescriberAccessor implements IGTNEIOCAmperage {

    @Shadow(remap = false)
    protected int amperage;

    @Override
    public int gtneioc$getAmperage() {
        return this.amperage;
    }
}
