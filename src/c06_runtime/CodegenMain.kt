package c06_runtime

import c01_lexer.Lexer
import c03_parser.Parser
import c05_ir_optimize.IrGenerator
import c05_ir_optimize.TAC

/**
 * 第7课：目标代码生成 —— 三地址码 -> 伪汇编
 * ============================================================
 * 知识点：
 *   编译器的最后一步：把中间代码翻译成目标机器的指令。
 *   真实目标是 x86/ARM 汇编；为了教学，我们定义一种"伪汇编"，
 *   它有真实汇编的核心要素：
 *
 *     寄存器:  R0, R1, R2 ...        （CPU 里最快的存储）
 *     指令:    LOAD  R, x            把变量/常量装进寄存器
 *             STORE x, R            把寄存器写回变量
 *             ADD/SUB/MUL/DIV R, a, b
 *             CMP / JMP / JZ ...    比较与跳转
 *
 *   核心难题：寄存器分配（Register Allocation）
 *     寄存器只有几个，临时变量可能有很多，
 *     谁放寄存器？谁"溢出"到内存？—— 图着色算法（本课只演示最简单的朴素策略）
 * ============================================================
 */

object Codegen {

    // 朴素策略：轮流使用 R0..R3，不超出 4 个寄存器（教学够用）
    private var nextReg = 0
    private val tempReg = mutableMapOf<String, String>()   // 临时变量 -> 寄存器

    private fun regOf(temp: String): String {
        return tempReg.getOrPut(temp) {
            val r = "R${nextReg % 4}"
            nextReg++
            r
        }
    }

    fun generate(ir: List<TAC>): List<String> {
        val asm = mutableListOf<String>()
        for (instr in ir) {
            when (instr) {
                is TAC.Binary -> {
                    val r = regOf(instr.result)
                    asm.add("    LOAD  $r, ${instr.a}")          // 左操作数进寄存器
                    val opName = when (instr.op) {
                        "+" -> "ADD"; "-" -> "SUB"; "*" -> "MUL"; "/" -> "DIV"
                        "<" -> "LT";  ">" -> "GT";  "==" -> "EQ"; "!=" -> "NEQ"
                        "&&" -> "AND"; "||" -> "OR"
                        else -> instr.op
                    }
                    asm.add("    $opName  $r, $r, ${instr.b}")   // r = r op b
                }
                is TAC.Unary -> {
                    val r = regOf(instr.result)
                    asm.add("    LOAD  $r, ${instr.a}")
                    asm.add("    ${if (instr.op == "-") "NEG" else "NOT"}  $r, $r")
                }
                is TAC.Copy -> {
                    // 目标是真实变量 → 直接 STORE；目标是临时变量 → 放寄存器
                    if (instr.result.startsWith("t")) {
                        asm.add("    LOAD  ${regOf(instr.result)}, ${instr.a}")
                    } else {
                        // 源可能在寄存器里
                        val src = tempReg[instr.a] ?: instr.a
                        asm.add("    STORE ${instr.result}, $src")
                    }
                }
                is TAC.Print -> {
                    val src = tempReg[instr.a] ?: instr.a
                    asm.add("    PRINT $src")
                }
                is TAC.Label -> asm.add("${instr.name}:")
                is TAC.Goto -> asm.add("    JMP   ${instr.label}")
                is TAC.IfFalse -> {
                    val src = tempReg[instr.cond] ?: instr.cond
                    asm.add("    JZ    $src, ${instr.label}")   // 为 0(false) 则跳转
                }
            }
        }
        return asm
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    println("========== 演示1: 表达式 -> 伪汇编 ==========")
    val code = """
        int x = 2 + 3 * 4;
        print x;
    """.trimIndent()
    println("源代码:\n$code")

    val ast = Parser(Lexer(code).tokenize()).parseProgram()
    val ir = IrGenerator().generate(ast)
    println("\n三地址码:")
    ir.forEach { println("  $it") }

    val asm = Codegen.generate(ir)
    println("\n伪汇编:")
    asm.forEach { println(it) }

    println("\n========== 演示2: 带控制流的伪汇编 ==========")
    val code2 = """
        int x = 3;
        while (x > 0) {
            print x;
            x = x - 1;
        }
    """.trimIndent()
    println("源代码:\n$code2")
    val asm2 = Codegen.generate(IrGenerator().generate(Parser(Lexer(code2).tokenize()).parseProgram()))
    println("\n伪汇编:")
    asm2.forEach { println(it) }

    println("\n========== 小结 ==========")
    println("三地址码 -> 汇编 基本是'一对一翻译'，真正的难点是：")
    println("  1. 寄存器分配：寄存器有限，变量很多，谁放寄存器？")
    println("  2. 指令选择：x86 一条 lea 指令能干好几条三地址码的活")
    println("  3. 指令调度：调整顺序让 CPU 流水线不空转")
    println("这些是'编译器后端'的研究内容，本课只展示了最朴素的翻译。")
}
