package c05_ir_optimize

import c01_lexer.Lexer
import c03_parser.Expr
import c03_parser.Parser
import c03_parser.Stmt

/**
 * 第5课：中间代码生成 —— 三地址码（Three-Address Code / 四元式）
 * ============================================================
 * 知识点：
 *   直接由 AST 生成机器码太复杂，编译器通常先翻译成一种简单的"中间表示"（IR）。
 *   最经典的 IR 是"三地址码"：每条指令最多三个操作数，形如
 *
 *        result = arg1 op arg2        （四元式: op, arg1, arg2, result）
 *
 *   例如表达式  x = 2 + 3 * 4  被拆成：
 *
 *        t1 = 3 * 4
 *        t2 = 2 + t1
 *        x  = t2
 *
 *   为什么这么做？
 *     - 指令形式统一，方便做优化（第6课）
 *     - 与具体机器无关，换 CPU 只需重写后端
 *     - 临时变量 t1 t2 ... 晚点再分配到寄存器或内存
 *
 *   本文件演示：AST -> 三地址码，包括 if/while 的跳转（label / goto）。
 * ============================================================
 */

// ---------------- 1. 三地址码指令 ----------------
sealed class TAC {
    // t = a op b      （op 是 + - * / < > == != && ||）
    data class Binary(val result: String, val op: String, val a: String, val b: String) : TAC()
    // t = op a        （op 是 - 或 !）
    data class Unary(val result: String, val op: String, val a: String) : TAC()
    // t = a           （赋值/拷贝）
    data class Copy(val result: String, val a: String) : TAC()
    // L1:             （标号，跳转目标）
    data class Label(val name: String) : TAC()
    // goto L1
    data class Goto(val label: String) : TAC()
    // ifFalse a goto L1    （a 为 false 时跳转）
    data class IfFalse(val cond: String, val label: String) : TAC()
    // print a
    data class Print(val a: String) : TAC()

    override fun toString(): String = when (this) {
        is Binary  -> "$result = $a $op $b"
        is Unary   -> "$result = $op$a"
        is Copy    -> "$result = $a"
        is Label   -> "$name:"
        is Goto    -> "goto $label"
        is IfFalse -> "ifFalse $cond goto $label"
        is Print   -> "print $a"
    }
}

// ---------------- 2. IR 生成器 ----------------
class IrGenerator {
    private val code = mutableListOf<TAC>()
    private var tempCount = 0     // 临时变量编号 t0, t1, t2 ...
    private var labelCount = 0    // 标号编号 L0, L1, L2 ...

    private fun newTemp() = "t${tempCount++}"
    private fun newLabel() = "L${labelCount++}"

    fun generate(stmts: List<Stmt>): List<TAC> {
        stmts.forEach { genStmt(it) }
        return code
    }

    private fun genStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.VarDecl -> {
                val v = genExpr(stmt.init)
                code.add(TAC.Copy(stmt.name, v))
            }
            is Stmt.Assign -> {
                val v = genExpr(stmt.value)
                code.add(TAC.Copy(stmt.name, v))
            }
            is Stmt.Print -> {
                val v = genExpr(stmt.value)
                code.add(TAC.Print(v))
            }
            // if (cond) then else elsePart 的翻译模式：
            //     <cond 代码>
            //     ifFalse cond goto Lelse
            //     <then 代码>
            //     goto Lend
            //   Lelse:
            //     <else 代码>
            //   Lend:
            is Stmt.If -> {
                val elseLabel = newLabel()
                val endLabel = newLabel()
                val c = genExpr(stmt.cond)
                code.add(TAC.IfFalse(c, elseLabel))
                stmt.thenBlock.forEach { genStmt(it) }
                code.add(TAC.Goto(endLabel))
                code.add(TAC.Label(elseLabel))
                stmt.elseBlock.forEach { genStmt(it) }
                code.add(TAC.Label(endLabel))
            }
            // while (cond) body 的翻译模式：
            //   Lbegin:
            //     <cond 代码>
            //     ifFalse cond goto Lend
            //     <body 代码>
            //     goto Lbegin
            //   Lend:
            is Stmt.While -> {
                val beginLabel = newLabel()
                val endLabel = newLabel()
                code.add(TAC.Label(beginLabel))
                val c = genExpr(stmt.cond)
                code.add(TAC.IfFalse(c, endLabel))
                stmt.body.forEach { genStmt(it) }
                code.add(TAC.Goto(beginLabel))
                code.add(TAC.Label(endLabel))
            }
        }
    }

    // 递归翻译表达式，返回"存放结果的临时变量名"
    private fun genExpr(expr: Expr): String = when (expr) {
        is Expr.NumberLit -> expr.value.toString()       // 数字直接用字面量
        is Expr.StringLit -> "\"${expr.value}\""
        is Expr.BoolLit -> expr.value.toString()
        is Expr.Variable -> expr.name                    // 变量直接用名字

        is Expr.Unary -> {
            val a = genExpr(expr.operand)
            val t = newTemp()
            code.add(TAC.Unary(t, expr.op, a))
            t
        }
        is Expr.Binary -> {
            val a = genExpr(expr.left)
            val b = genExpr(expr.right)
            val t = newTemp()
            code.add(TAC.Binary(t, expr.op, a, b))
            t
        }
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    println("========== 演示1: 表达式 -> 三地址码 ==========")
    val code1 = "int x = 2 + 3 * 4;"
    println("源代码: $code1\n")
    val ast1 = Parser(Lexer(code1).tokenize()).parseProgram()
    val ir1 = IrGenerator().generate(ast1)
    ir1.forEachIndexed { i, tac -> println("  %2d: %s".format(i, tac)) }
    println("\n观察: 乘法和加法被拆成了独立的指令，临时变量 t0、t1 保存中间结果")
    println("      原来树形的优先级关系，现在体现在'谁先用谁的结果'上（数据流）")

    println("\n========== 演示2: if/else -> 跳转 ==========")
    val code2 = """
        int x = 10;
        if (x > 5) {
            print "big";
        } else {
            print "small";
        }
    """.trimIndent()
    println(code2 + "\n")
    val ast2 = Parser(Lexer(code2).tokenize()).parseProgram()
    IrGenerator().generate(ast2).forEachIndexed { i, tac -> println("  %2d: %s".format(i, tac)) }

    println("\n========== 演示3: while -> 回边跳转（循环） ==========")
    val code3 = """
        int x = 3;
        while (x > 0) {
            print x;
            x = x - 1;
        }
    """.trimIndent()
    println(code3 + "\n")
    val ast3 = Parser(Lexer(code3).tokenize()).parseProgram()
    IrGenerator().generate(ast3).forEachIndexed { i, tac -> println("  %2d: %s".format(i, tac)) }
    println("\n观察: 循环 = 一个向后的 goto（跳回 L0），这就是'回边'")
}
