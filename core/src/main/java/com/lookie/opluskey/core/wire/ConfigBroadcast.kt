package com.lookie.opluskey.core.wire

import android.content.Intent
import android.os.Bundle
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId

/** 配置的广播线格式：宿主 / 设置页发往注入侧（system_server）的编解码。 */
object ConfigBroadcast {
    const val ACTION_CONFIG = "com.lookie.opluskey.CONFIG"

    // 各项 extra 的名字**不在这里写**：同一个逻辑字段的两套名字（落盘 JSON 的键 + 本广播的 extra 名）
    //  成对收在 `core/model/ConfigFields` 一处，这里只引用 `*_EXTRA` 那一列。两列的取值不一样
    //  （`longPressMs` 对 `long_press_ms`），分开放时改一边编译器不会提醒另一边。

    /**
     * 一条配置广播的**内容指纹**（仅用于「设置页手上这份」与「注入侧那份」是不是同一个）。
     *
     *  注入侧原来只回一个「收到过配置没有」的布尔 —— 那答不了「后来改过的那一次到了没有」：
     *  开机时收到过一条，之后哪条广播丢了，它那边还是旧配置，界面照样报「正常」。
     *  这里两边算同一个指纹，设置页比一次就知道同步没同步。
     *
     *  按 **extras** 算而不是按 [Config] 算：注入侧拿到的是广播解出来的对象，若按对象算，
     *  `longIsSerial` 这类由发送侧现算的字段、以及 `normalizeForGesture` 的归一化都会让
     *  同一份配置在两边的指纹不同 —— 那是假警报。指纹只回答「发出去的那条 = 收到的那条」。
     *
     *  注意：**只做同源比对，不做安全判据**（`hashCode` 有碰撞可能）：用途是让「设置生效」
     *  那一段说实话，不是访问控制。
     */
    fun tag(intent: Intent): String {
        val extras = intent.extras
        val keys = extras?.keySet()?.sorted().orEmpty()
        val sb = StringBuilder()
        for (k in keys) {
            sb
                .append(k)
                .append('=')
                .append(extras?.let { rawExtra(it, k) })
                .append(';')
        }
        return sb
            .toString()
            .hashCode()
            .toUInt()
            .toString(16)
    }

    /**
     * 取一个 extra 的原始值（只给 [tag] 用）。
     *
     * `Bundle.get(String)` 在 API 33 起弃用（返回 `Any?`、类型不安全），替代品是各类型的 getter
     * ⇒ 这里按 [ConfigFields] 那张**字段表**登记的类型分派（原先是在这里手抄第三份字段名单，
     * 于是「新增一个字段、类型忘了在这一列登记」这件事只表现为指纹算错）。
     *
     *  注意：**不能「挨个类型试」**：类型不匹配时 `getString` 抛 `ClassCastException`（不是返回 null）。
     *  注意：本函数在**注入侧**（system_server）也会被调到（见 `ConfigChannel`）⇒ 整体 catch 兜住，
     *  取值失败只当「这个键没值」（与旧的 `Bundle.get` 返回 null 同一个结果），绝不让异常越出去。
     *  表里没登记的名字按**文本**取（与本表出现之前那条兜底分支同一个口径）。
     */
    private fun rawExtra(
        extras: Bundle,
        k: String,
    ): Any? =
        try {
            when (ConfigFields.kindOfExtra(k)) {
                is ConfigFields.Kind.Bool -> extras.getBoolean(k)
                is ConfigFields.Kind.Number -> extras.getInt(k)
                else -> extras.getString(k)
            }
        } catch (e: Throwable) {
            null
        }

    /**
     * [tag] 的发送侧入口：按 [intent] 的口径算，与接收侧逐字节对得上。
     * [longSerial] 与 [intent] 同源 —— 由调用方算好（见那里的说明）。
     */
    fun tag(
        config: Config,
        longSerial: Boolean,
    ): String = tag(intent(config, longSerial))

    /**
     * 把一份配置编成广播；宿主与设置页共用此构造器，格式只在这里写一次。
     *
     *  逐项 extra 的名字与类型由 `ConfigFields` 那张**字段表**给出（本层只负责把值放进 intent），
     *  于是加一个字段不必再来这里改一遍 —— 这里原来是全项目第三个手抄字段名单的地方。
     *
     *  [longSerial] 由**调用方**算好传进来：那一位要读宿主私有目录里的命令库
     *  （`CommandLibrary.longIsSerial`，两个调用方都与宿主同 uid、读得到，且此路径不在按键热路径上）。
     *  本包因此**不认识落盘那包** —— 注入侧要 import 它，这条编译期保证不能靠传递依赖绕开
     *  （见 `docs/decoupling.md` 动作 3）。
     *
     *  注意：`longIsSerial` 是**发送侧现算**的一位，不是 `KeyConfig` 上存着的那个属性 ——
     *  所以这里按调用方给的值覆盖掉，读属性的话这一位恒为 `false`（它只由注入侧从广播解出来）。
     */
    fun intent(
        config: Config,
        longSerial: Boolean,
    ): Intent {
        val key = config.key(KeyId.PLUS)
        val override = ConfigFields.Values(key.fieldValues().raw() + (ConfigFields.F_LONG_SERIAL to longSerial))
        return Intent(ACTION_CONFIG)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .also { putFields(it, override, ConfigFields.KEY_FIELDS) }
            .also { putFields(it, config.global.fieldValues(), ConfigFields.GLOBAL_FIELDS) }
    }

    /** 按字段表把一份取值写进广播的 extras（[ConfigFields.Field.extra] 为 `null` 的项跳过）。 */
    private fun putFields(
        intent: Intent,
        v: ConfigFields.Values,
        fields: List<ConfigFields.Field>,
    ) {
        for (f in fields) {
            val name = f.extra ?: continue
            when (f.kind) {
                is ConfigFields.Kind.Bool -> intent.putExtra(name, v.bool(f))
                is ConfigFields.Kind.Number -> intent.putExtra(name, v.number(f))
                is ConfigFields.Kind.Text -> intent.putExtra(name, v.text(f))
            }
        }
    }

    /**
     * 把一条配置广播解回 [Config]。
     * 调用点在 AMS 广播入口的 hooker（热路径）：只取 extra 拼对象，不做查找与 IO。
     * 拼装不得抛异常，否则 system_server 崩溃；缺项取安全默认（不接管、单击保留原生）。
     *
     *  取值仍按 `ConfigFields` 的字段表来（类型与默认值都在那张表里，本层不再逐项抄）；
     *  顶层那两位「已确认」标记不进广播（它们只落盘，见 `ConfigFields.F_DISCLAIMER`）⇒ 取默认。
     */
    fun parse(intent: Intent): Config {
        val keys = mapOf(KeyId.PLUS to KeyConfig.ofFields(KeyId.PLUS, readFields(intent, ConfigFields.KEY_FIELDS)))
        val global = GlobalConfig.ofFields(readFields(intent, ConfigFields.GLOBAL_FIELDS))
        return Config.ofFields(keys, global, ConfigFields.Values(emptyMap()))
    }

    /**
     * 按字段表从广播里取值 —— 缺项走字段表里登记的默认值（与 `ConfigCodec` 同一张表、同一套默认值）。
     *
     *  与落盘那一路的**唯一差别**在布尔：广播这边用 `getBooleanExtra(key, default)`（缺项补默认值，
     *  与「这条广播本来就不带它」的语义一致），而 JSON 那边是 `optBoolean(key)`（缺项得 `false`，
     *  见 `ConfigCodec.readFields` 那个坑的说明）。
     */
    private fun readFields(
        intent: Intent,
        fields: List<ConfigFields.Field>,
    ): ConfigFields.Values {
        val values = mutableMapOf<ConfigFields.Field, Any>()
        for (f in fields) {
            val name = f.extra ?: continue
            when (val kind = f.kind) {
                is ConfigFields.Kind.Bool -> {
                    values[f] = intent.getBooleanExtra(name, kind.default)
                }

                is ConfigFields.Kind.Number -> {
                    values[f] = intent.getIntExtra(name, kind.default).coerceIn(kind.min, kind.max)
                }

                is ConfigFields.Kind.Text -> {
                    values[f] = intent.getStringExtra(name) ?: kind.default
                }
            }
        }
        return ConfigFields.Values(values)
    }
}
