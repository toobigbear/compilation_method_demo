package c07_full_pipeline

import c01_lexer.Lexer
import c03_parser.Parser
import c03_parser.AstPrinter
import c04_semantic.SemanticAnalyzer
import c04_semantic.Scope
import c05_ir_optimize.IrGenerator
import c05_ir_optimize.Optimizer
import c06_runtime.TacVM

/**
 * 第9课：完整编译流水线
 * ============================================================
 * 知识点：
 *   把前面每一课串起来，走一遍编译器的完整流程：
 *
 *      源代码
 *        │  ① 词法分析 (Lexer)          —— 字符流 → Token 序列
 *        ▼
 *      Token 序列
 *        │  ② 语法分析 (Parser)         —— Token 序列 → 抽象语法树 AST
 *        ▼
 *      抽象语法树 AST
 *        │  ③ 语义分析 (Semantic)       —— 符号表 + 类型检查
 *        ▼
 *      标注过的 AST
 *        │  ④ 中间代码生成 (IrGen)      —— AST → 三地址码
 *        ▼
 *      三地址码
 *        │  ⑤ 代码优化 (Optimizer)      —— 常量折叠/传播/死代码删除
 *        ▼
 *      优化后的三地址码
 *        │  ⑥ 目标代码生成 / 执行 (VM)  —— 虚拟机执行
 *        ▼
 *      程序运行结果
 *
 *   每一阶段只做一件事，把复杂问题分解 —— 这是编译器设计的核心思想。
 * ============================================================
 */

fun main() {
    val source = """
        int n = 5;
        int fact = 1;
        while (n > 1) {
            fact = fact * n;
            n = n - 1;
        }
        print fact;
        if (fact == 120) {
            print "5! = 120, correct";
        } else {
            print "wrong";
        }
    """.trimIndent()

    println("╔══════════════════════════════════════════════╗")
    println("║        完整编译流水线演示（计算 5!）          ║")
    println("╚══════════════════════════════════════════════╝")
    println("源代码:")
    println(source)

    // ---------- ① 词法分析 ----------
    printStage(1, "词法分析", "字符流 → Token 序列")
    val tokens = Lexer(source).tokenize()
    println("共 ${tokens.size} 个 Token，前 8 个:")
    tokens.take(8).forEach { println("  $it") }
    println("  ...")

    // ---------- ② 语法分析 ----------
    printStage(2, "语法分析", "Token 序列 → 抽象语法树 AST")
    val ast = Parser(tokens).parseProgram()
    AstPrinter.printProgram(ast)

    // ---------- ③ 语义分析 ----------
    printStage(3, "语义分析", "符号表 + 类型检查")
    val analyzer = SemanticAnalyzer()
    analyzer.check(ast, Scope(null, "global"))
    if (analyzer.errors.isEmpty()) println("✔ 语义检查通过") else analyzer.errors.forEach { println("  ✘ ${it.message}") }

    // ---------- ④ 中间代码生成 ----------
    printStage(4, "中间代码生成", "AST → 三地址码")
    var ir = IrGenerator().generate(ast)
    ir.forEach { println("  $it") }

    // ---------- ⑤ 代码优化 ----------
    printStage(5, "代码优化", "常量折叠 + 传播 + 死代码删除")
    ir = Optimizer.constantFolding(ir)
    ir = Optimizer.constantPropagation(ir)
    ir = Optimizer.constantFolding(ir)
    ir = Optimizer.deadCodeElimination(ir)
    ir.forEach { println("  $it") }

    // ---------- ⑥ 执行 ----------
    printStage(6, "目标执行", "虚拟机运行三地址码")
    val vm = TacVM(ir)
    vm.run()

    println("\n╔══════════════════════════════════════════════╗")
    println("║  全部阶段顺利完成！最终输出: ${vm.output}  ║")
    println("╚══════════════════════════════════════════════╝")
}

private fun printStage(num: Int, name: String, desc: String) {
    println("\n${"─".repeat(50)}")
    println("阶段 $num: $name    ($desc)")
    println("─".repeat(50))
}
