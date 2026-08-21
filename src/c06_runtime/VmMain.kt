package c06_runtime

import c01_lexer.Lexer
import c03_parser.Parser
import c05_ir_optimize.IrGenerator
import c05_ir_optimize.TAC

/**
 * 第8课：虚拟机执行 —— 运行三地址码
 * ============================================================
 * 知识点：
 *   第6课直接解释 AST；这一课我们写一个"小虚拟机"来执行三地址码。
 *   这就是"编译 + 执行"的完整模拟：
 *
 *      源代码 -> 词法 -> 语法 -> 三地址码 -> 【虚拟机执行】
 *
 *   虚拟机的组成：
 *     - 变量存储: Map<String, Value>   （相当于内存）
 *     - 程序计数器 pc: 当前执行到第几条指令
 *     - 取指 -> 译码 -> 执行 的循环（真实 CPU 就是这么工作的）
 *
 *   和第6课对比着看，能直观理解"解释 AST"和"执行线性指令流"的区别。
 * ============================================================
 */

class TacVM(private val code: List<TAC>) {

    private val vars = mutableMapOf<String, Int>()
    private val strings = mutableMapOf<String, String>()   // 字符串字面量单独存
    private var pc = 0                                     // 程序计数器
    val output = mutableListOf<String>()

    // 预扫描：建立 标号 -> 指令下标 的映射，让 goto 能 O(1) 跳转
    private val labelPos: Map<String, Int> = buildMap {
        code.forEachIndexed { i, instr ->
            if (instr is TAC.Label) put(instr.name, i)
        }
    }

    fun run(maxSteps: Int = 10000) {
        var steps = 0
        while (pc < code.size) {
            if (++steps > maxSteps) throw RuntimeException("疑似死循环，已终止")
            when (val instr = code[pc]) {
                is TAC.Binary -> {
                    val a = read(instr.a); val b = read(instr.b)
                    vars[instr.result] = when (instr.op) {
                        "+" -> a + b;  "-" -> a - b;  "*" -> a * b;  "/" -> a / b
                        "<" -> if (a < b) 1 else 0;   ">" -> if (a > b) 1 else 0
                        "==" -> if (a == b) 1 else 0; "!=" -> if (a != b) 1 else 0
                        "&&" -> if (a != 0 && b != 0) 1 else 0
                        "||" -> if (a != 0 || b != 0) 1 else 0
                        else -> throw RuntimeException("未知运算 ${instr.op}")
                    }
                }
                is TAC.Unary -> {
                    vars[instr.result] = when (instr.op) {
                        "-" -> -read(instr.a)
                        "!" -> if (read(instr.a) == 0) 1 else 0
                        else -> throw RuntimeException("未知运算 ${instr.op}")
                    }
                }
                is TAC.Copy -> {
                    if (instr.a.startsWith("\"")) strings[instr.result] = instr.a.trim('"')
                    else vars[instr.result] = read(instr.a)
                }
                is TAC.Print -> {
                    val text = when {
                        instr.a.startsWith("\"") -> instr.a.trim('"')
                        strings.containsKey(instr.a) -> strings[instr.a]!!
                        else -> read(instr.a).toString()
                    }
                    println("  >>> 输出: $text")
                    output.add(text)
                }
                is TAC.Goto -> pc = labelPos[instr.label]!!
                is TAC.IfFalse -> if (read(instr.cond) == 0) pc = labelPos[instr.label]!!
                is TAC.Label -> { /* 标号本身不执行任何操作 */ }
            }
            pc++
        }
    }

    // 读一个操作数：数字字面量直接返回，否则查变量表
    private fun read(operand: String): Int =
        operand.toIntOrNull() ?: operand.toBooleanStrictOrNull()?.let { if (it) 1 else 0 }
        ?: vars[operand] ?: 0
}

// ---------------- 运行演示 ----------------
fun main() {
    val code = """
        int n = 5;
        int sum = 0;
        while (n > 0) {
            sum = sum + n;
            n = n - 1;
        }
        print sum;
        if (sum == 15) {
            print "correct";
        } else {
            print "wrong";
        }
    """.trimIndent()

    println("========== 源代码（计算 5+4+3+2+1） ==========")
    println(code)

    val ast = Parser(Lexer(code).tokenize()).parseProgram()
    val ir = IrGenerator().generate(ast)

    println("\n========== 三地址码 ==========")
    ir.forEachIndexed { i, tac -> println("  %2d: %s".format(i, tac)) }

    println("\n========== 虚拟机执行 ==========")
    val vm = TacVM(ir)
    vm.run()

    println("\n========== 对比：用第6课的 AST 解释器执行同一段代码 ==========")
    val interp = Interpreter()
    interp.run(ast, Environment(null))

    println("\n两种执行方式结果一致:")
    println("  VM 输出:  ${vm.output}")
    println("  解释器输出: ${interp.output}")
}
