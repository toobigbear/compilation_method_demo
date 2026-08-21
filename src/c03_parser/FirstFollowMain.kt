package c03_parser

/**
 * 第3课补充：FIRST 集 与 FOLLOW 集
 * ============================================================
 * 知识点：
 *   手工写递归下降时我们靠"直觉"决定什么时候选哪条产生式。
 *   课本（LL(1) 分析法）则给出了严格的判断依据：
 *
 *     FIRST(A)  = 从 A 能推出的所有串的"开头终结符"集合
 *                 作用：看到一个 Token，就知道该用 A 的哪条产生式
 *     FOLLOW(A) = 在句型中可能紧跟在 A 后面的终结符集合
 *                 作用：当 A 可以为空（ε）时，判断该不该让 A 消失
 *
 *   构造预测分析表的规则：
 *     对产生式 A -> α：
 *       1. 对 FIRST(α) 中每个终结符 a，把 A->α 填入表[A, a]
 *       2. 若 ε ∈ FIRST(α)，则对 FOLLOW(A) 中每个 b，把 A->α 填入表[A, b]
 *
 *   本文件自动计算 FIRST 集，并用一张手工构造的预测分析表演示表驱动解析。
 * ============================================================
 */

// ---------------- 1. 文法的数据结构 ----------------
// 产生式：左部非终结符 -> 右部符号串（"E" -> ["T", "+", "E"] 表示 E -> T + E）
// ε（空串）用空列表 emptyList() 表示
data class Production(val lhs: String, val rhs: List<String>)

class Grammar(
    val startSymbol: String,
    val productions: List<Production>,
    val terminals: Set<String>,        // 终结符（Token）
    val nonTerminals: Set<String>      // 非终结符
) {
    fun isTerminal(sym: String) = sym in terminals
    fun prodsOf(lhs: String) = productions.filter { it.lhs == lhs }
}

// ---------------- 2. 自动计算 FIRST 集 ----------------
fun computeFirst(g: Grammar): MutableMap<String, MutableSet<String>> {
    val first = mutableMapOf<String, MutableSet<String>>()

    // 终结符的 FIRST 就是它自己
    g.terminals.forEach { first[it] = mutableSetOf(it) }
    g.nonTerminals.forEach { first[it] = mutableSetOf() }

    // 不断迭代，直到所有集合都不再增大（不动点算法）
    var changed = true
    while (changed) {
        changed = false
        for (p in g.productions) {
            val lhsFirst = first[p.lhs]!!

            if (p.rhs.isEmpty()) {                    // A -> ε
                if (lhsFirst.add("ε")) changed = true
                continue
            }
            // 扫描右部：把 FIRST(X1) 加进来；
            // 若 X1 可空，还要继续看 X2，以此类推
            var allNullable = true
            for (sym in p.rhs) {
                val symFirst = first[sym]!!
                if (lhsFirst.addAll(symFirst - "ε")) changed = true
                if ("ε" !in symFirst) {               // X_i 不可空 → 停止
                    allNullable = false
                    break
                }
            }
            if (allNullable) {                        // 右部全部可空 → A 可空
                if (lhsFirst.add("ε")) changed = true
            }
        }
    }
    return first
}

// ---------------- 3. 运行演示 ----------------
fun main() {
    // 课本经典文法（消除左递归后的表达式文法）：
    //   E  -> T E'
    //   E' -> + T E' | ε
    //   T  -> F T'
    //   T' -> * F T' | ε
    //   F  -> ( E ) | num
    val grammar = Grammar(
        startSymbol = "E",
        productions = listOf(
            Production("E",  listOf("T", "E'")),
            Production("E'", listOf("+", "T", "E'")),
            Production("E'", emptyList()),                 // ε
            Production("T",  listOf("F", "T'")),
            Production("T'", listOf("*", "F", "T'")),
            Production("T'", emptyList()),                 // ε
            Production("F",  listOf("(", "E", ")")),
            Production("F",  listOf("num")),
        ),
        terminals = setOf("+", "*", "(", ")", "num"),
        nonTerminals = setOf("E", "E'", "T", "T'", "F")
    )

    println("========== 文法 ==========")
    grammar.productions.forEach { p ->
        println("  ${p.lhs} -> ${if (p.rhs.isEmpty()) "ε" else p.rhs.joinToString(" ")}")
    }

    val first = computeFirst(grammar)

    println("\n========== FIRST 集（自动计算） ==========")
    grammar.nonTerminals.forEach { nt ->
        println("  FIRST($nt) = { ${first[nt]!!.joinToString(", ")} }")
    }

    println("\n========== FIRST 集怎么指导解析？ ==========")
    println("  假设 Parser 正在展开 F，看到下一个 Token 是 '('")
    println("  F 有两条产生式:  F -> ( E )   和   F -> num")
    println("  FIRST( ( E ) ) = { ( }   包含 '('  → 选 F -> ( E )")
    println("  如果看到的是 num，FIRST(num) = { num } → 选 F -> num")
    println("  → 只要各产生式的 FIRST 集互不相交，就能唯一确定选哪条，这就是 LL(1)！")

    println("\n========== 手工构造的 LL(1) 预测分析表 ==========")
    // 表[非终结符][终结符] = 选用的产生式
    val table = mapOf(
        "E"  to mapOf("(" to "E -> T E'",              "num" to "E -> T E'"),
        "E'" to mapOf("+" to "E' -> + T E'",           ")" to "E' -> ε", "$" to "E' -> ε"),
        "T"  to mapOf("(" to "T -> F T'",              "num" to "T -> F T'"),
        "T'" to mapOf("+" to "T' -> ε", "*" to "T' -> * F T'", ")" to "T' -> ε", "$" to "T' -> ε"),
        "F"  to mapOf("(" to "F -> ( E )",             "num" to "F -> num"),
    )
    val cols = listOf("+", "*", "(", ")", "num", "$")
    println("  %-4s ${cols.joinToString("") { "%-14s" }.format(*cols.toTypedArray())}")
    for (nt in grammar.nonTerminals) {
        val row = cols.joinToString("") { c -> "%-14s".format(table[nt]?.get(c) ?: "") }
        println("  %-4s %s".format(nt, row))
    }
    println("\n  这张表 = 递归下降 Parser 的'理论形态'")
    println("  第3课 Grammar.kt 里的 if/while 判断，本质上就是在'查这张表'")
}
