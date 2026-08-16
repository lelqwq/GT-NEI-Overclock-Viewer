# GT-NEI-Overclock-Viewer — 项目备忘录

GTNH 2.8.4 专用客户端 mod：在 NEI 格雷配方页显示 ▲▼ 箭头，按电压等级切换查看各档 EU/t 与时长（高版本 GT 整合包的 NEI 切档功能）。作者 lelqwq。与 gtpoc（完美超频）解耦：装不装 gtpoc 都能用，显示走同一个 OverclockCalculator 语义自动一致。

## 一句话原理

GT5U 5.09.51.x 的 `GTNEIDefaultHandler.drawDescription()` 每次绘制都从 `overclockDescriber`（含 tier + amperage）构造计算器。
本 mod 在绘制入口把 describer **临时替换**为 `new EUOverclockDescriber(目标tier, amperage)`，绘制管线（能量/时长/档位名）自动跟随；绘制完恢复。▲▼ 按钮画在配方区右上角，点击调整每 handler 的档位偏移。

## 核心设计决策（已与用户确认，勿再询问）

- 独立 mod（不并入 gtpoc），纯客户端（mixin 配置用 `"client"` 键）
- 档位语义 = **电压等级**（LV→…→MAX=14），每档显示该档 EU/t + 时长 + 超频级数 (+N OC)
- 上限 MAX(14)；下限 = `GTUtility.getTier(recipe.mEUt)`（配方原生最低电压档）
- 已知现象（接受）：高电压段 EU/t 撞 int 上限后显示 21.4 亿封顶值（计算器真实 clamp 值）
- 显示箭头的类型：精确 `EUOverclockDescriber` **或** 精确 `EUNoOverclockDescriber`（482 物品查找路径的惰性默认，档位恒 LV）；聚变（是 EUOverclockDescriber 的子类）/蒸汽/质量发生器（自定义子类）无箭头
- 基础档 = `max(配方原生档, describer 档)`——惰性默认的 LV 档不误导切档起点，shift=0 时显示配方原生档
- 482 绘制链备忘（反编译 5.09.51.482 确认）：`drawForeground → drawExtras(i) → drawDescription(cached) + frontend.drawNEIOverlays`，drawDescription 为 private 但注入点仍有效；describer 由 `loadCraftingRecipes` 的 `results[0]` 携带（机器上下文路径）或 `drawDescription` 惰性赋 `EUNoOverclockDescriber(1, amperage)`（物品查找路径）
- ▲▼ 按钮样式（用户要求与 NEI 一致，复刻 `LayoutStyleMinecraft.drawButton` 反编译）：`LayoutManager.drawButtonBackground(x, y, w, h, true, type)` 背景 + 居中无阴影文字；NEI 配色：常态 `0xE0E0E0`、悬停 `0xFFFFA0`、禁用 `0x601010`；type 0=禁用 1=常态 2=悬停。悬停检测：`GuiRecipe.updateScreen` HEAD 暂存部件 0 原点（widget.x/y **+ HandlerInfo.yShift**，GT 页 yShift=6）进 TierState，drawExtras 用 LWJGL `Mouse` + `ScaledResolution` 换算局部坐标；**点击命中用同一锚点**（含 yShift，保证点击区=显示区）。不用 drawScreen：GuiRecipe 未 override 它，APT 解析不到、refmap 无条目（运行时必崩），updateScreen 有 override 且每 tick 刷新
- ▲▼ 按钮位置（最终定版）：**横向并排**于 NEI 收藏/覆盖按钮列的左侧（与覆盖按钮同排，间距 2px）；动态检测（复刻 `getDefatulButtons` 公式 + `HandlerInfo.getShowOverlayButton()/getShowFavoritesButton()` + `NEIClientConfig.favoritesEnabled()`，内容坐标下 yShift 相消）；无 NEI 按钮时回退右下角。档位标签右对齐于按钮上方（右缘距 NEI 按钮列 2px）。曾试过的列式（▲▼ 在上/在下）均被用户否决
- 切档状态：每 handler 一个偏移（WeakHashMap 挂实例），GuiRecipe.initGui 时重置（关页面即复位）
- 开关：NEI 选项页「电压切档箭头」+ gtnEIOc.cfg（enableArrows，默认 true），点击开关写回 Forge 配置

## 注入点（src/main/java/com/lelqwq/gtneioc/mixins/）

- `MixinNEIRecipeHandler`（GTNEIDefaultHandler）：`drawDescription` HEAD 替换 describer + 暂存配方 / `drawExtras` TAIL 恢复 + 画箭头（配方经 TierState 暂存传递，不 shadow 父类字段）
- `MixinGuiRecipe`（NEI GuiRecipe）：`initGui` HEAD 重置状态 / `mouseClicked` HEAD 命中 ▲▼ 并 cancel（这两个是 vanilla 方法，remap=true 走 refmap；点击命中锚定 `container` 私有字段的 widget[0] 屏幕坐标，NEI 自家成员 remap=false）
- `MixinDescriberAccessor`（EUNoOverclockDescriber）：注入公开的 amperage 读取方法（切档重建 describer 时保留安培数）

## 已踩坑备忘

- **@Shadow 规则**：引用目标类成员必须 @Shadow 声明；@Shadow 方法要声明 `abstract` 且类要 `abstract`；this 传给外部方法要 `(Target) (Object) this` 转换
- **@env 值是阶段不是端**：UniMixins 的 `"target": "@env(...)"` 解析值是 Mixin 阶段（DEFAULT/PREINIT 等），`@env(CLIENT)` 无效；客户端限定用 JSON 的 `"client"` 键（`"target": "@env(DEFAULT)"`）
- **NEI 配置插件发现机制**：类名必须匹配 `NEI*Config` 模式并实现 `IConfigureNEI`（NEI 的 ClassDiscoverer 扫描）
- **@Shadow 字段不搜父类**：GTNH-UniMixins 的字段解析（TargetClassContext.findAliasedField）只查目标类自身字段 + mixin 注入字段 + 别名，**不遍历父类**——arecipes 在 TemplateRecipeHandler（父类）而目标是 GTNEIDefaultHandler → not located 崩溃；同类字段（overclockDescriber/container/amperage）正常。取父类数据改用：同类的 public 方法、注入参数暂存（本 mod 方案，drawDescription 的参数暂存进 TierState）、或直接 mixin 父类本身
- **vanilla 成员必须 remap=true + refmap**：`guiLeft/guiTop/initGui/mouseClicked` 等 vanilla 字段/方法运行时是混淆名（mod jar 里为 SRG 名如 `func_73866_w_`），`remap = false` 按字面名找不到 → InvalidMixinException → 类转换失败 → 启动即崩（实测由 AE2Thing preInit 触发 GuiRecipe 类加载时爆炸）。mod 自家成员 dev 名原样保留，remap=false 安全；refmap 由 mixin 注解处理器自动生成（**仅 remap=true 成员产生条目**），构建后务必解包确认 refmap 非空
- **drawExtras 收到的是配方全局索引**（482 内部 `arecipes.get(该索引)`，多配方页翻页后不再是 0）：箭头绘制时机须与当前页 widget[0] 的 `handlerRef.recipeIndex`（public final）比对，该值由 `updateScreen` 暂存进 TierState；否则第 2 页起不画箭头但旧矩形残留、点击仍生效
- **禁用按钮不悬停不调档**：悬停高亮须 `canUp/canDown && 命中`；点击侧读 TierState 暂存的可用性（与矩形同步写入），禁用态只吞点击
- **锚点双路刷新防闪烁**：锚点（含页首索引）除 `updateScreen` 每 tick 刷新外，还要在 `refreshContainer` TAIL（NEI 方法，dev 名，remap=false）即时刷新——翻页点击瞬间 widget 重建，若只靠 tick 刷新会有最多 50ms 窗口箭头缺失 → 闪烁
- **箭头只在 NEI 配方页绘制**：物品面板悬停配方预览（NEIRecipeWidget showAsWidget 模式）复用同一 drawExtras 但无点击通路，箭头块须加 `currentScreen instanceof GuiRecipe` 门槛（NEI 自有类，dev 名）；describer 替换/恢复不受此门槛影响，预览里的档位显示与配方页保持一致
- **坐标体系**：drawExtras 在配方区局部坐标（NEI 绘制时 GL 平移过，原点 = widget 位置 + HandlerInfo.yShift，GT 页 yShift=6）；mouseClicked 收到屏幕坐标，命中检测用 `updateScreen` 暂存的同一锚点（含 yShift）换算，保证点击区=显示区
- 注入有返回值的方法必须用 `CallbackInfoReturnable`（gtpoc 的血泪教训）；本 mod 四个注入目标全是 void，用 `CallbackInfo` 正确

## 构建

- `./gradlew spotlessApply` 后 `./gradlew build` → `build/libs/gt-nei-overclock-viewer-0.1.3.jar`（显示名 GT NEI Overclock Viewer；modid 仍为 gtneioc，配置文件名 gtnEIOc.cfg 不变）
- 依赖：GT5U 5.09.51.476（transitive=false）、NotEnoughItems 2.8.44-GTNH、CodeChickenCore 1.4.10（github group 坐标）
- 构建坑：GTNH 依赖坐标是 `com.github.GTNewHorizons` 不是 `com.gtnewhorizons`；1.7.10 的 @Mod 没有 `clientSideOnly` 属性

## 工作流程

- 与 gtpoc 相同：用户实机测试每个版本，**实测通过前不提交**；提交信息中文 Conventional Commits
- 改动前先出影响分析报告、经用户批准再动手；沟通用中文

## 当前状态（2026-08-16）

- 实机测试进展（2026-08-16 晚）：① 首测启动崩溃 MixinGuiRecipe vanilla 字段 guiLeft/guiTop（已按方案 B 修复，refmap 生效）；② 二测启动崩溃 MixinNEIRecipeHandler @Shadow 父类字段 arecipes（已改 TierState 暂存传递修复）；③ 三测能进游戏但无箭头——482 物品查找路径 describer 是惰性默认 `EUNoOverclockDescriber`（档位恒 LV），精确类型检查拒绝；已放宽类型判定 + 基础档取 max；④ 四测箭头出现，样式改为 NEI 风格、位置右下角；⑤ 五测用户反馈：与收藏/覆盖按钮重叠、无悬停高亮、点击区比显示区靠上（yShift 未计入）——均已修复（动态检测 NEI 按钮位置、updateScreen 暂存锚点、悬停高亮）
- ⑥ 用户发现两 bug：多配方页第 2 页起不显示箭头（但旧矩形残留点击仍生效）、最低/最高档禁用态仍高亮且响应点击——已修复（页首索引比对 + 禁用态门槛）；⑦ 更名：jar/显示名改为 **gt-nei-overclock-preview / GT NEI Overclock Preview**（modid gtneioc 不变）
- ⑧ 翻页闪烁（tick 暂存窗口期）——refreshContainer TAIL 即时刷新修复，实测通过并已提交；⑨ 物品面板悬停配方预览也画箭头（无点击通路）——加 `currentScreen instanceof GuiRecipe` 门槛，仅配方页绘制；⑩ 按钮布局定版：横向并排于收藏/覆盖按钮左侧（列式两版均被否决）
- **v0.1.3 已构建并本地提交**（build/libs/gt-nei-overclock-preview-0.1.3.jar），⑨⑩ 经用户确认后提交
- 实机 482 兼容性已验证（javap）：CachedDefaultRecipe.mRecipe / GTRecipe.mEUt:I / EUOverclockDescriber(byte,int) 继承链 / GTValues.VN 与编译用的 476 一致
- 待测点：机器上下文路径（GUI 里点 Recipes 打开）是否正常、无箭头机器（EBF/聚变/蒸汽）页面原样、NEI 选项开关、关页面状态复位、与 gtpoc 同装时显示 ÷4
- 本地 git 仓库已 init，**未推远程**（用户要求本地先行）
- ⑪ 2026-08-16 晚：项目文件夹更名 **GT-NEI-Overclock-Viewer**，内部命名全部 preview → viewer（jar 名 gt-nei-overclock-viewer、显示名 GT NEI Overclock Viewer、NAME 常量；modid gtneioc 与配置文件名不变），版本保持 0.1.3，待实机测试后提交
