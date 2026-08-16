package com.lelqwq.gtneioc;

/**
 * 由 {@code MixinDescriberAccessor} 注入到 {@code EUNoOverclockDescriber}，
 * 暴露其 protected 的 amperage 字段，供切档时重建 describer 使用。
 */
public interface IGTNEIOCAmperage {

    int gtneioc$getAmperage();
}
