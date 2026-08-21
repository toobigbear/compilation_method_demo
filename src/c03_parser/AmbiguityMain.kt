package c03_parser

/**
 * 第3课补充：文法的二义性（Ambiguity）
 * ============================================================
 * 知识点：
 *   同一个句子，如果一棵文法能推出两棵不同的语法树，这个文法就是"二义"的。
 *
 *   经典例子：E -> E + E | E * E | num
 *   句子  2 + 3 * 4  有两种推导：
 *
 *     树A（先算*，正确）      树B（先算+，错误）
 *        (+)                    (*)
 *       /   \                  /   \
 *      2    (*)              (+)    4
 *          /   \            /   \
 *         3     4          2     3
 *
 * 消除二义性的常用办法：
 *   1. 分层文法：为每个优先级写一个非终结符（E -> T + T，T -> F * F）
 *   2. 在 Parser 里用函数层级体现优先级（第3课 Grammar.kt 就是这么做的）
 *   3. 对 " dangling else"（else 挂在哪个 if 上）：约定"就近原则"
 * ============================================================
 */

// ---------- 演示1：同一个表达式的两棵语法树 ----------
sealed class MiniExpr {
    data class Num(val v: Int) : MiniExpr()
    data class Add(val l: MiniExpr, val r: MiniExpr) : MiniExpr()
    data class Mul(val l: MiniExpr, val r: MiniExpr) : MiniExpr()
}

fun pretty(e: MiniExpr): String = when (e) {
    is MiniExpr.Num -> e.v.toString()
    is MiniExpr.Add -> "(${pretty(e.l)} + ${pretty(e.r)})"
    is MiniExpr.Mul -> "(${pretty(e.l)} * ${pretty(e.r)})"
}

// 手动构造 2+3*4 的两棵不同语法树
fun ambiguityDemo() {
    val treeA = MiniExpr.Add(MiniExpr.Num(2), MiniExpr.Mul(MiniExpr.Num(3), MiniExpr.Num(4)))
    val treeB = MiniExpr.Mul(MiniExpr.Add(MiniExpr.Num(2), MiniExpr.Num(3)), MiniExpr.Num(4))
    println("句子: 2 + 3 * 4")
    println("  树A: ${pretty(treeA)}  = 2 + (3*4) = 14   ← 我们想要的")
    println("  树B: ${pretty(treeB)}  = (2+3) * 4 = 20   ← 如果文法二义，编译器可能选这棵")
    println("  → 二义文法本身不知道哪个对，必须靠文法分层或优先级规则消除")
}

// ---------- 演示2：分层文法消除二义性 ----------
// 二义文法：    E -> E + E | E * E | num
// 分层文法（无二义）：
//   E -> T ( + T )*       （加法层）
//   T -> F ( * F )*       （乘法层）
//   F -> num | ( E )      （原子层）
//
// 关键思想：把 * 压到更下层，* 会先被解析成完整的树，再参与 +
object LayeredParser {
    // 用一个极简的词法器（只认数字、+、*、括号）
    fun tokenize(s: String): List<Char> = s.filter { !it.isWhitespace() }.toList()

    var tokens = listOf<Char>()
    var pos = 0

    fun parse(s: String): MiniExpr {
        tokens = tokenize(s); pos = 0
        return parseE()
    }

    // E -> T ( + T )*
    fun parseE(): MiniExpr {
        var left = parseT()
        while (pos < tokens.size && tokens[pos] == '+') {
            pos++
            left = MiniExpr.Add(left, parseT())
        }
        return left
    }

    // T -> F ( * F )*
    fun parseT(): MiniExpr {
        var left = parseF()
        while (pos < tokens.size && tokens[pos] == '*') {
            pos++
            left = MiniExpr.Mul(left, parseF())
        }
        return left
    }

    // F -> num | ( E )
    fun parseF(): MiniExpr {
        return if (tokens[pos] == '(') {
            pos++
            val e = parseE()
            pos++  // 吃掉 ')'
            e
        } else {
            MiniExpr.Num(tokens[pos++].digitToInt())
        }
    }
}

// ---------- 演示3：dangling else ----------
// if a if b print "x" else print "y"
// else 到底属于内层 if 还是外层 if？—— 业界约定：属于最近的 if
fun danglingElseDemo() {
    println("""
        |句子:  if a  if b  print "x"  else  print "y"
        |两种理解:
        |  理解1: else 属于内层 if   ->  if a { if b {print "x"} else {print "y"} }
        |  理解2: else 属于外层 if   ->  if a { if b {print "x"} } else {print "y"}
        |约定: else 总是匹配最近的、还没配对的 if（就近原则）
        |第3课的 Parser 就是这么实现的：else 紧跟在最近的 if 解析之后被检查
    """.trimMargin())
}

fun main() {
    println("========== 1. 二义性：一个句子，两棵语法树 ==========")
    ambiguityDemo()

    println("\n========== 2. 分层文法消除二义性 ==========")
    val expr = "2+3*4"
    val tree = LayeredParser.parse(expr)
    println("用分层文法解析 \"$expr\":  ${pretty(tree)}")
    println("结果唯一！因为文法强制 * 必须先于 + 被组合")

    println("\n========== 3. dangling else（另一种二义性） ==========")
    danglingElseDemo()

    println("========== 小结 ==========")
    println("二义性 = 一个句子对应多棵语法树")
    println("消除手段: 文法分层（优先级）、结合性规则、else 就近原则")
}
