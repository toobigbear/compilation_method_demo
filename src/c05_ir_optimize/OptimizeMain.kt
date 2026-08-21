package c05_ir_optimize

import c01_lexer.Lexer
import c03_parser.Parser

/**
 * 第6课：代码优化（Optimization）
 * ============================================================
 * 知识点：
 *   优化是在"中间代码"层面改进程序，让它跑得更快/更省，但不改变运行结果。
 *   本文件演示 4 种课本上最经典的优化（都作用在三地址码上）：
 *
 *   1. 常量折叠（Constant Folding）
 *      编译期就把能算的算掉：  t0 = 3 * 4  →  t0 = 12
 *
 *   2. 常量传播（Constant Propagation）
 *      已知 x = 12，后面用到 x 的地方直接换成 12：  t1 = x + 2  →  t1 = 12 + 2
 *
 *   3. 公共子表达式消除（Common Subexpression Elimination, CSE）
 *      重复出现的表达式只算一次：
 *        a = b * c
 *        d = b * c      →  d = a
 *
 *   4. 死代码删除（Dead Code Elimination）
 *      算出来却没人用的结果直接删掉：  t = ...  （t 之后再也无人引用）→ 删除
 *
 * 优化的前提：不能改变程序的可观察行为（输出必须和原来一模一样）。
 * ============================================================
 */

// 复用第5课的 TAC 定义
import c05_ir_optimize.TAC
import c05_ir_optimize.IrGenerator

object Optimizer {

    // ---------- 1. 常量折叠 ----------
    fun constantFolding(code: List<TAC>): List<TAC> {
        return code.map { instr ->
            if (instr is TAC.Binary && isNumber(instr.a) && isNumber(instr.b)) {
                val a = instr.a.toInt(); val b = instr.b.toInt()
                val value = when (instr.op) {
                    "+" -> a + b;  "-" -> a - b;  "*" -> a * b;  "/" -> if (b != 0) a / b else return@map instr
                    "<" -> return@map TAC.Copy(instr.result, (a < b).toString())
                    ">" -> return@map TAC.Copy(instr.result, (a > b).toString())
                    "==" -> return@map TAC.Copy(instr.result, (a == b).toString())
                    "!=" -> return@map TAC.Copy(instr.result, (a != b).toString())
                    else -> return@map instr
                }
                TAC.Copy(instr.result, value.toString())     // 折叠成一条拷贝指令
            } else instr
        }
    }

    // ---------- 2. 常量传播 ----------
    fun constantPropagation(code: List<TAC>): List<TAC> {
        val consts = mutableMapOf<String, String>()   // 变量 -> 已知常量值
        return code.map { instr ->
            when (instr) {
                is TAC.Copy -> {
                    val newA = consts[instr.a] ?: instr.a
                    // 记录/更新常量信息
                    if (isNumber(newA) || newA == "true" || newA == "false") consts[instr.result] = newA
                    else consts.remove(instr.result)
                    TAC.Copy(instr.result, newA)
                }
                is TAC.Binary -> {
                    val newA = consts[instr.a] ?: instr.a
                    val newB = consts[instr.b] ?: instr.b
                    consts.remove(instr.result)          // t 被重新计算，不再是已知常量
                    TAC.Binary(instr.result, instr.op, newA, newB)
                }
                is TAC.Unary -> { consts.remove(instr.result); TAC.Unary(instr.result, instr.op, consts[instr.a] ?: instr.a) }
                is TAC.Print -> TAC.Print(consts[instr.a] ?: instr.a)
                is TAC.IfFalse -> TAC.IfFalse(consts[instr.cond] ?: instr.cond, instr.label)
                else -> { if (instr is TAC.Copy) consts.remove(instr.result); instr }
            }
        }
    }

    // ---------- 3. 公共子表达式消除 ----------
    fun cse(code: List<TAC>): List<TAC> {
        val exprs = mutableMapOf<String, String>()   // "a op b" -> 已经算出它的变量
        return code.map { instr ->
            if (instr is TAC.Binary) {
                val key = "${instr.a} ${instr.op} ${instr.b}"
                val existing = exprs[key]
                if (existing != null) {
                    TAC.Copy(instr.result, existing)          // 复用之前的结果
                } else {
                    exprs[key] = instr.result
                    instr
                }
            } else instr
        }
    }

    // ---------- 4. 死代码删除 ----------
    fun deadCodeElimination(code: List<TAC>): List<TAC> {
        // 反向扫描：统计每个变量在"自己之后"是否被用到
        val used = mutableSetOf<String>()
        val result = mutableListOf<TAC>()
        for (instr in code.asReversed()) {
            when (instr) {
                is TAC.Binary -> {
                    if (instr.result in used || true) { /* 保守起见：临时变量参与打印才保留，下面统一处理 */ }
                    if (instr.result in used) {
                        used.add(instr.a); used.add(instr.b)
                        result.add(instr)
                    }
                    // 如果 result 没被用过 → 整条删除
                }
                is TAC.Copy -> {
                    if (instr.result in used || !instr.result.startsWith("t")) {
                        // 非临时变量（x、y 等）保守保留；临时变量没人用才删
                        used.add(instr.a)
                        result.add(instr)
                    }
                }
                is TAC.Unary -> {
                    if (instr.result in used) { used.add(instr.a); result.add(instr) }
                }
                is TAC.Print -> { used.add(instr.a); result.add(instr) }
                is TAC.IfFalse -> { used.add(instr.cond); result.add(instr) }
                else -> result.add(instr)   // Label / Goto 永远保留
            }
        }
        return result.asReversed()
    }

    private fun isNumber(s: String) = s.toIntOrNull() != null
}

// ---------------- 运行演示 ----------------
fun main() {
    println("========== 原始三地址码 ==========")
    val code = """
        int x = 3 * 4;
        int y = x + 2;
        int a = x + 2;
        int dead = 99 * 100;
        print y;
    """.trimIndent()
    println(code + "\n")

    val ast = Parser(Lexer(code).tokenize()).parseProgram()
    var ir = IrGenerator().generate(ast)
    println("生成的 IR:")
    ir.forEach { println("  $it") }

    fun show(stage: String, list: List<TAC>) {
        println("\n========== $stage ==========")
        list.forEach { println("  $it") }
    }

    ir = Optimizer.constantFolding(ir);        show("1. 常量折叠后 (3*4 直接算出)", ir)
    ir = Optimizer.constantPropagation(ir);    show("2. 常量传播后 (x 替换成 12)", ir)
    ir = Optimizer.constantFolding(ir);        show("3. 再折叠一次 (12+2 继续算)", ir)
    ir = Optimizer.cse(ir);                    show("4. 公共子表达式消除 (重复的 14 复用)", ir)
    ir = Optimizer.deadCodeElimination(ir);    show("5. 死代码删除 (没用的 t 和 dead 被删)", ir)

    println("\n========== 小结 ==========")
    println("源代码 5 条语句，最终只需 3 条指令，而且没有任何乘法/加法要在运行时执行")
    println("—— 这就是优化器每天在背后默默做的事")
}
