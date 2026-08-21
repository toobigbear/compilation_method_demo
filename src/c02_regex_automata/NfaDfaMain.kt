package c02_regex_automata

/**
 * 第2课：正则表达式 与 有限自动机（NFA / DFA）
 * ============================================================
 * 知识点：
 *   词法分析器（如第1课的 Lexer）不需要纯手工写！
 *   课本上的自动化路线是：
 *
 *      正则表达式 --(Thompson构造)--> NFA --(子集构造)--> DFA --(驱动)--> 词法分析器
 *
 *   本文件演示最核心的概念：
 *     1. 什么是 NFA：状态 + 转移，一个字符可能走向多个状态（不确定）
 *     2. 什么是 DFA：每个状态面对一个字符只有唯一去向（确定）
 *     3. NFA 模拟：同时走所有可能的路径，集合推演
 *     4. DFA 模拟：一步一步走，简单直接
 *   5. 为什么说 DFA 更快（每个字符只查一次表）
 * ============================================================
 */

// ---------------- 1. 手写一个 NFA：识别标识符 [a-zA-Z_][a-zA-Z0-9_]* ----------------
//
// 状态图（→ 表示转移，((s1)) 表示接受态）：
//
//        字母或_                字母或数字或_
//   s0 -----------> ((s1)) <------------------ (自环)
//
// "不确定"体现在：在 s1 读到字母时，可以选择"停在 s1"或"继续自环"——NFA 允许同时走多条路。
class IdentifierNFA {
    // 用"当前可能所处的状态集合"来模拟 NFA
    fun match(input: String): Boolean {
        var currentStates = setOf(0)              // 初始：在状态0
        for (ch in input) {
            val next = mutableSetOf<Int>()
            for (state in currentStates) {
                when (state) {
                    0 -> if (ch.isLetter() || ch == '_') next.add(1)   // 0 --字母--> 1
                    1 -> if (ch.isLetterOrDigit() || ch == '_') next.add(1) // 1 自环
                }
            }
            if (next.isEmpty()) return false      // 所有路都走死了 → 不匹配
            currentStates = next
        }
        return currentStates.contains(1)          // 结束时能停在接受态1 → 匹配成功
    }
}

// ---------------- 2. 手写一个 DFA：识别数字 [0-9]+ ----------------
//
// 状态图：
//        数字               数字
//   q0 -------> ((q1)) <--------- (自环)
//
// DFA 特点：每个状态 + 一个字符 = 唯一确定的下一个状态 → 可以直接用二维表（转移表）实现
class NumberDFA {
    // 转移表：transitionTable[状态][字符类别] = 下一个状态，-1 表示拒绝
    // 状态0：起始态；状态1：接受态（读过至少一个数字）
    private fun nextState(state: Int, ch: Char): Int = when (state) {
        0 -> if (ch.isDigit()) 1 else -1
        1 -> if (ch.isDigit()) 1 else -1
        else -> -1
    }

    fun match(input: String): Boolean {
        var state = 0
        for (ch in input) {
            state = nextState(state, ch)
            if (state == -1) return false         // 走进死胡同 → 不匹配
        }
        return state == 1                         // 停在接受态 → 匹配
    }
}

// ---------------- 3. 通用 DFA：真正用"转移表"驱动的版本 ----------------
// 这就是词法分析器生成器（如 lex/flex）背后的原理：
//   用户写正则 -> 工具自动构造出这样一张表 -> 运行时只查表，速度极快
class TableDrivenDFA(
    private val startState: Int,
    private val acceptStates: Set<Int>,
    // 转移表: transitions[状态] = Map<字符类别判断, 下一状态>
    private val transitions: Map<Int, List<Pair<(Char) -> Boolean, Int>>>
) {
    fun match(input: String): Boolean {
        var state = startState
        for (ch in input) {
            val next = transitions[state]?.firstOrNull { (cond, _) -> cond(ch) }?.second
                ?: return false
            state = next
        }
        return state in acceptStates
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    println("========== 1. NFA 模拟：识别标识符 ==========")
    val nfa = IdentifierNFA()
    listOf("x", "total_2", "_temp", "2abc", "hello world").forEach { s ->
        println("  \"$s\"  ->  ${if (nfa.match(s)) "✔ 是标识符" else "✘ 不是标识符"}")
    }

    println("\n========== 2. DFA 模拟：识别数字 ==========")
    val dfa = NumberDFA()
    listOf("42", "007", "3.14", "abc", "").forEach { s ->
        println("  \"$s\"  ->  ${if (dfa.match(s)) "✔ 是数字" else "✘ 不是数字"}")
    }

    println("\n========== 3. 表驱动 DFA：识别关键字 if ==========")
    // 正则 "if" 对应的 DFA：  q0 -i-> q1 -f-> ((q2))
    val ifDFA = TableDrivenDFA(
        startState = 0,
        acceptStates = setOf(2),
        transitions = mapOf(
            0 to listOf({ c: Char -> c == 'i' } to 1),
            1 to listOf({ c: Char -> c == 'f' } to 2)
        )
    )
    listOf("if", "i", "iff", "int").forEach { s ->
        println("  \"$s\"  ->  ${if (ifDFA.match(s)) "✔ 匹配关键字 if" else "✘ 不匹配"}")
    }

    println("\n========== 小结 ==========")
    println("NFA：一个字符可能走多条路，模拟时要维护'状态集合'")
    println("DFA：每个状态对每个字符只有一条路，运行时只需查表，快！")
    println("词法分析器生成器 = 正则 -> NFA -> DFA -> 查表程序")
    println("第1课的 Lexer 是手工写的；用生成器的话，我们只要写正则规则就够了。")
}
