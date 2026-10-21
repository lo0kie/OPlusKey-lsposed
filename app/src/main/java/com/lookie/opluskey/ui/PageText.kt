package com.lookie.opluskey.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.BUILD_KIND
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.SHOW_BUILD_TIME
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 把草稿里的那条命令存进命令库（不是立刻绑到某个手势上）。
 *
 * 重名时表单保存按钮已被 [commandNameDuplicated] 禁掉、成环时被 [commandReferenceCycle] 禁掉；
 * 本函数保留「按名字写入」的语义，是给编辑态（名字没变 = 它自己）兜底的。多条命令按名字引用单条命令
 * （步骤里存 `cmd:<名字>`），所以改名 / 删命令只能走编辑页，别在库层面直接动名字。
 */
internal fun SettingsPage.addCommand() {
    val (cmd, next) = draftToLibrary() ?: return
    val name = cmd.name
    val editingFrom = commandDraft.draft.editingName
    val replaced = configState.commands.value.any { it.name == name }
    // 结果一句话（改 / 覆盖 / 新增各一条；失败由 [saveCommands] 自己报）
    val tip =
        when {
            editingFrom != null -> I18n.get(Strings.TOAST_COMMAND_MODIFIED, "已修改")
            replaced -> I18n.get(Strings.TOAST_COMMAND_OVERWRITTEN, "已覆盖")
            else -> I18n.get(Strings.TOAST_COMMAND_SAVED, "已保存")
        }
    // 改名回滚要用它：库先落盘、绑定后挪，绑定没挪成时得把库退回改名前的样子（见下面那条）。
    val previousLibrary = configState.commands.value
    val saveResult =
        saveCommands(
            next,
            when {
                // 编辑态：提交的是修改（名字没变 = 原地更新；改了名 = 挪到新名字下）
                editingFrom != null && editingFrom != name -> "改名保存：$editingFrom → $name（${cmd.kind.id}）"

                editingFrom != null -> "修改命令：$name（${cmd.kind.id}）"

                replaced -> "覆盖命令：$name（${cmd.kind.id}）"

                else -> "新增命令：$name（${cmd.kind.id}）"
            },
        )
    // 没落盘就**到此为止**：下面那套 `reset` + 退出会把用户刚编的整份内容丢掉，
    //  而文件里一个字节都没变（失败原因 [saveCommands] 已经报过「保存失败」）。
    //  必须看返回值：写盘失败 / 只读态**不能**照样清表单并退出。
    if (saveResult == CommandsSave.Failed) {
        XLog.w("保存命令失败：表单保持原样，不 reset、不退出（$name）")
        return
    }
    // (3) 手势上绑的还是 `cmd:旧名`，旧那条已经不在库里 —— 跟 [removeCommand] 同一套做法，
    //   把绑定一起挪到新名字下，否则那句「已修改」是句空话。
    //  三态：只有**没落盘**才算「绑定没挪成」。落盘成功、只是广播没发出去时，
    //  文件里的绑定已经是新名字 —— 这时把命令库退回旧名，就变成「主配置写新名、库里只有旧名」，
    //  那只手按下去是 NOT_FOUND（命令已经不在了）。
    var bindingMoved = CommandsSave.Ok
    if (editingFrom != null && editingFrom != name) {
        val cfg = configState.config.value
        val oldToken = Command.PREFIX + editingFrom

        fun move(a: Action) = if (a.token == oldToken) Action.Custom(name) else a

        //  条件规则里的动作是**第四处引用**（前三处是与手势那三格）。漏了它，改名之后
        //  规则命中时解析新名字失败 —— 界面上看不出任何悬空，只是那一条不再执行。
        fun moveRule(r: com.lookie.opluskey.core.model.StateRule) =
            if (r.action.token == oldToken) r.copy(action = Action.Custom(name)) else r
        // **每一颗键**都要挪：自定义键同样能绑 `cmd:名字`，只改侧键的话那些键按下去就是
        //  「命令已不存在」（NOT_FOUND），而用户看到的是「改名成功」。
        val moved = mutableMapOf<KeyId, com.lookie.opluskey.core.model.KeyConfig>()
        for (k in cfg.allKeys) {
            val key = cfg.key(k)
            val updated =
                key.copy(
                    single = move(key.single),
                    double = move(key.double),
                    long = move(key.long),
                    rules = key.rules.map(::moveRule),
                )
            if (updated != key) moved[k] = updated
        }
        if (moved.isNotEmpty()) {
            bindingMoved =
                write(
                    cfg.copy(keys = cfg.keys + moved),
                    "改名后把绑定挪到新名字：$editingFrom → $name",
                )
        }
    }
    // 绑定没挪成 ⇒ 把命令库**退回改名前的样子**：库里是新名、手势上还指着 `cmd:旧名` 的话，
    //  那只手按下去就是「这条命令已经不在了」（NOT_FOUND），而用户看到的却是「已修改」。
    //  与 [removeCommand] 同一条纪律 —— 两边要么一起变、要么都不变。
    // 绑定挪成了、但上面广播没发出去（[CommandsSave.BroadcastFailed]）时不报「已修改」：
    //  那句会让用户以为侧键行为也跟过来了，而 saveCommands 已经报过「通知注入侧失败」。
    if (bindingMoved == CommandsSave.Failed) {
        saveCommands(previousLibrary, "改名失败，回滚命令库：$editingFrom → $name")
    } else if (saveResult == CommandsSave.Ok && bindingMoved == CommandsSave.Ok) {
        //  两半都必须是 Ok 才报「已修改」：绑定那一半广播没发出去时，[write] 已经报过
        //  「已保存，但通知注入侧失败」，这里再补一句会把它顶掉（底部只有一行回执）。
        //  归到整页那一个位：连续保存两次时屏幕上改字，不叠出两条「已保存」（见 [HINT_SLOT_COMMAND]）
        hint(tip, slot = HINT_SLOT_COMMAND)
    }
    // 存完**留在本页**（以前新增那条会退回「按键绑定」）：接着编下一条是常事，
    //  每存一条就被弹回去一次纯属添乱；这条命令已经在那张「已保存的命令」列表里了，
    //  想看结果当场就能看到。
    commandDraft.draft.reset()
}

/**
 * 完整版本标识：**模块自己**的版本号 + [BUILD_KIND] + 构建时刻
 * （`1.0.0-debug-2026-10-15 15:07:12`）。
 *
 *  两处用它：导出的日志头（见 `ConfigTransfer.exportLog`）与**「更多选项」页的版本行** ——
 * 排查时要据此认出是哪个包、什么时候出的，而「什么时候」必须带日期才认得出。
 * 首页那条副标题走 [appVersionLabel]（它按构建类型决定要不要带末段）。
 */
internal fun SettingsPage.appVersionText(): String {
    val name = moduleVersionName()
    val build = moduleBuildStamp()
    val suffix = "-$BUILD_KIND"
    // 三档的 `versionName` 已经带了后缀（`versionNameSuffix`，见 `build.gradle.kts`）——
    //  已经带过就不再拼一次，否则会显示成 `0.0.1-beta-beta-…` 那种。
    return when {
        name.isNullOrBlank() -> "$BUILD_KIND-$build"
        name.endsWith(suffix) -> "$name-$build"
        else -> "$name$suffix-$build"
    }
}

/**
 * 「更多选项」页「版本」那一行的副标题 —— `version-<版本号>-<构建类型>-<yyMMddHHmm>`
 * （如 `version-1.1.0-release-2610201546`）。
 *
 *  与 [appVersionText] **分开**：那一串还喂给导出的日志头，要完整日期带秒才方便事后对时间；
 *  这一行是给人扫一眼的，时刻压到分钟，前面那截 `version-` 让它自己说明是什么。
 */
internal fun SettingsPage.appVersionBadge(): String {
    val name = moduleVersionName()
    val suffix = "-$BUILD_KIND"
    // 三档的 `versionName` 已带后缀（`versionNameSuffix`），带过就不再拼一次。
    val head =
        when {
            name.isNullOrBlank() -> BUILD_KIND
            name.endsWith(suffix) -> name
            else -> "$name$suffix"
        }
    return "$VERSION_BADGE_PREFIX$head-${compactBuildStamp(moduleBuildStamp())}"
}

/** 版本行那串的前缀（见 [appVersionBadge]）。 */
private const val VERSION_BADGE_PREFIX = "version-"

/** 构建时刻的完整形态（`GenerateBuildId` 写进资源的那一份）。 */
private const val STAMP_FULL_PATTERN = "yyyy-MM-dd HH:mm:ss"

/** 版本行上那一截压缩形态：两位年 + 月日 + 时分。 */
private const val STAMP_LABEL_PATTERN = "yyMMddHHmm"

/** 压缩形态的位数 —— 认不出的形态按这个截断，别把一整串日期甩进副标题。 */
private const val STAMP_LABEL_DIGITS = 10

/**
 * 构建时刻压成 [STAMP_LABEL_PATTERN]（`2026-10-20 15:46:31` → `2610201546`）。
 *
 *  认不出的形态（例如退回 [ModuleInfo.BUILD_ID] 的 `日期-序号`）就只留数字：
 *  宁可少两位，也不在副标题里显示成一句含糊的话。
 */
private fun compactBuildStamp(raw: String): String =
    runCatching {
        SimpleDateFormat(STAMP_FULL_PATTERN, Locale.US).parse(raw)?.let {
            SimpleDateFormat(STAMP_LABEL_PATTERN, Locale.US).format(it)
        }
    }.getOrNull() ?: raw.filter { it.isDigit() }.take(STAMP_LABEL_DIGITS)

/**
 * 首页大标题那条副标题用的版本。
 *
 *  前面带一截 `version-`：这串东西摆在大标题底下、没有标签，光一个 `1.1.0-release`
 *  看不出它是什么（页面上别处也没有「版本」两个字给它托底）。
 *
 *  两档口径（判据是 [SHOW_BUILD_TIME]，它只在 release 关着）：
 *   · 非 release（debug / log）→ 与「更多选项」页那一行**同一个形态**（[appVersionBadge]，
 *     `version-1.1.0-debug-2610201546`）：自用包一天里会出好几个，光看构建类型分不出是哪一次；
 *   · release → 不带时刻（`version-1.1.0-release`）：首页是面向用户的界面，时刻对用户没意义
 *     （要看的去「更多选项」页）。
 *
 *  读不到版本号时只显示 [BUILD_KIND]，不抛异常。
 */
internal fun SettingsPage.appVersionLabel(): String {
    if (SHOW_BUILD_TIME) return appVersionBadge()
    val name = moduleVersionName()
    val suffix = "-$BUILD_KIND"
    // 三档的 `versionName` 已带后缀（`versionNameSuffix`），带过就不再拼一次。
    val head =
        when {
            name.isNullOrBlank() -> BUILD_KIND
            name.endsWith(suffix) -> name
            else -> "$name$suffix"
        }
    return "$VERSION_LABEL_PREFIX$head"
}

/**
 * 草稿 → 「存下去之后的那条命令」与「存下去之后的那份命令库」。
 *
 * **单一来源**：[addCommand] 存的就是它、[commandReferenceCycle] 判的也是它 —— 两处各写一遍的话，
 * 判的与存的就不是同一件事（改了名字、挪了引用之后，环也可能跟着变）。
 *
 * @return 名字为空（草稿还没填名字）时返回 `null`
 */
internal fun SettingsPage.draftToLibrary(): Pair<Command, List<Command>>? {
    val name = CommandLibrary.sanitizeName(commandDraft.draft.name)
    if (name.isEmpty()) return null
    val cmd =
        when (commandDraft.draft.kind) {
            CommandKind.SINGLE -> {
                Command(name, CommandKind.SINGLE, single = commandDraft.draft.single.trim())
            }

            CommandKind.SERIAL -> {
                Command(
                    name = name,
                    kind = CommandKind.SERIAL,
                    steps = commandDraft.draft.steps.toList(),
                    stepDelayMs = commandDraft.draft.stepDelayMs,
                )
            }
        }
    // (1) 改名保存必须把**原名那条一起摘掉**：只 `filterNot { it.name == name }` 的话旧名与新名
    //   两条并存在命令库里（列表凭空 +1 条），而手势上绑的仍是旧那条 `cmd:旧名` —— 用户以为改了，
    //   侧键行为一点没变。编辑态下 `editingFrom == name` 时两个条件指同一条，去重天然成立。
    // (2) 其余命令里对旧名字的引用（多条命令的步骤 / 单条正文里的 `cmd:旧名`）也要跟着挪，
    //   否则它们指向一条已经不在库里的命令，按下去永远 NOT_FOUND。
    val editingFrom = commandDraft.draft.editingName
    val next =
        configState.commands.value
            .filterNot { it.name == name || it.name == editingFrom }
            .map { it.renamedReference(editingFrom, name) } + cmd
    return cmd to next
}

/**
 * 模块自己的版本号（读不到返回 `null`，不抛异常）。
 *
 *  包名必须显式用 [ModuleInfo.PACKAGE]（模块自己），**不能用 `context.packageName`**
 * —— 这一页跑在宿主进程里，那个 `context` 是宿主的 Context，读出来是宿主 App 的版本号。
 *
 *  `BuildConfig` 在本工程是**关掉**的（见 `build.gradle.kts` 的 `buildFeatures`），所以只能
 * 从 `PackageManager` 读。
 */
private fun SettingsPage.moduleVersionName(): String? =
    try {
        context.packageManager
            .getPackageInfo(ModuleInfo.PACKAGE, 0)
            .versionName
    } catch (e: Throwable) {
        XLog.w("读模块版本号失败：${e.javaClass.simpleName}")
        null
    }

/**
 * 出包时注入的构建时刻（`yyyy-MM-dd HH:mm:ss`）—— 由 `build.gradle.kts` 里的 `GenerateBuildId`
 * 任务生成到 `values/build_id.xml`（**执行期**取时钟，不是配置期，理由见那个任务类）。
 *
 *  不能写 `R.string.build_id`：页面跑在**宿主**进程里，`R` 会解析到宿主自己的资源表，取出来
 * 是别人的字符串（或 0）。必须按**模块包名**去拿我们那份资源。
 *
 *  `getIdentifier` 找不到时返回 **0**，接着 `getString(0)` 抛 `Resources$NotFoundException`
 *  —— 所以下面那个 `catch` 不只是「防宿主限制」，它同时兜住「资源没进包」这一档。拿不到就退回
 *  [ModuleInfo.BUILD_ID] **整串**：它本身是 `日期-序号`，也是带日期的。
 */
private fun SettingsPage.moduleBuildStamp(): String =
    try {
        val res =
            context.packageManager
                .getResourcesForApplication(ModuleInfo.PACKAGE)
        val id = res.getIdentifier("build_id", "string", ModuleInfo.PACKAGE)
        res.getString(id).takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        XLog.w("读构建标识失败（退回常量）：${e.javaClass.simpleName}")
        null
    } ?: ModuleInfo.BUILD_ID

/** 首页副标题那串版本前面的前缀（见 [appVersionLabel]）。 */
private const val VERSION_LABEL_PREFIX = "version-"

internal fun SettingsPage.actionOf(
    cfg: Config,
    g: Gesture,
): Action {
    // 取**当前在编辑的那一颗**（`KeyWatchState`）：写死侧键的话，切到自定义键之后
    //  网格高亮、打勾项、小标题、长按串行判定全都还按侧键的配置算（真机反馈「改了看不出来」）。
    val key = cfg.key(KeyWatchState.editing(cfg))
    return when (g) {
        Gesture.SINGLE -> key.single
        Gesture.DOUBLE -> key.double
        Gesture.LONG -> key.long
    }
}
