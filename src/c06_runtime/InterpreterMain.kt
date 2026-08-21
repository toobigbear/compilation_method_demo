package c06_runtime

import c01_lexer.Lexer
import c03_parser.Expr
import c03_parser.Parser
import c03_parser.Stmt

/**
 * 第6课：解释执行 —— 直接"运行"AST
 * ============================================================
 * 知识点：
 *   编译器把程序翻译成机器码；解释器则"直接执行"语法树。
 *   二者前端（词法/语法/语义分析）完全一样，区别只在后端：
 *
 *      编译器: AST -> 中间代码 -> 机器码 -> CPU 执行
 *      解释器: AST -> 直接遍历求值
 *
 *   本文件实现一个"树遍历解释器"（Tree-walking Interpreter）：
 *     - 用环境（Environment，本质就是运行时的符号表）存变量值
 *     - 每类 AST 节点对应一个求值动作
 *     - 顺便演示：作用域在运行时就是"环境的嵌套"
 *
 *   Python、早期 JavaScript 引擎就是这样工作的。
 * ============================================================
 */

// ---------------- 1. 运行时值 ----------------
sealed class Value {
    data class IntVal(val v: Int) : Value()
    data class BoolVal(val v: Boolean) : Value()
    data class StrVal(val v: String) : Value()

    override fun toString(): String = when (this) {
        is IntVal -> v.toString()
        is BoolVal -> v.toString()
        is StrVal -> v
    }
}

// ---------------- 2. 运行时环境（= 运行期的符号表） ----------------
// 每个大括号 { } 在运行时创建一个新环境，parent 指向外层
class Environment(val parent: Environment?) {
    private val vars = mutableMapOf<String, Value>()

    fun define(name: String, value: Value) { vars[name] = value }

    fun get(name: String): Value =
        vars[name] ?: parent?.get(name)
        ?: throw RuntimeException("运行时错误: 变量 '$name' 未定义")

    fun assign(name: String, value: Value) {
        when {
            vars.containsKey(name) -> vars[name] = value
            parent != null -> parent.assign(name, value)
            else -> throw RuntimeException("运行时错误: 变量 '$name' 未定义")
        }
    }
}

// ---------------- 3. 解释器 ----------------
class Interpreter {
    // 解释执行一组语句，返回"程序输出"（方便我们验证结果）
    val output = mutableListOf<String>()

    fun run(stmts: List<Stmt>, env: Environment) {
        for (stmt in stmts) execute(stmt, env)
    }

    private fun execute(stmt: Stmt, env: Environment) {
        when (stmt) {
            is Stmt.VarDecl -> env.define(stmt.name, eval(stmt.init, env))
            is Stmt.Assign -> env.assign(stmt.name, eval(stmt.value, env))
            is Stmt.Print -> {
                val v = eval(stmt.value, env)
                println("  >>> 输出: $v")
                output.add(v.toString())
            }
            is Stmt.If -> {
                val cond = eval(stmt.cond, env)
                when (cond) {
                    is Value.BoolVal -> if (cond.v) run(stmt.thenBlock, Environment(env))
                    else run(stmt.elseBlock, Environment(env))
                    else -> throw RuntimeException("if 条件必须是 bool")
                }
            }
            is Stmt.While -> {
                while (true) {
                    val cond = eval(stmt.cond, env)
                    if (cond !is Value.BoolVal) throw RuntimeException("while 条件必须是 bool")
                    if (!cond.v) break
                    run(stmt.body, Environment(env))
                }
            }
        }
    }

    // 对表达式求值
    private fun eval(expr: Expr, env: Environment): Value = when (expr) {
        is Expr.NumberLit -> Value.IntVal(expr.value)
        is Expr.StringLit -> Value.StrVal(expr.value)
        is Expr.BoolLit -> Value.BoolVal(expr.value)
        is Expr.Variable -> env.get(expr.name)

        is Expr.Unary -> {
            val v = eval(expr.operand, env)
            when (expr.op) {
                "-" -> Value.IntVal(-(v as Value.IntVal).v)
                "!" -> Value.BoolVal(!(v as Value.BoolVal).v)
                else -> throw RuntimeException("未知一元运算符 ${expr.op}")
            }
        }

        is Expr.Binary -> {
            val l = eval(expr.left, env)
            val r = eval(expr.right, env)
            when (expr.op) {
                "+" -> Value.IntVal((l as Value.IntVal).v + (r as Value.IntVal).v)
                "-" -> Value.IntVal((l as Value.IntVal).v - (r as Value.IntVal).v)
                "*" -> Value.IntVal((l as Value.IntVal).v * (r as Value.IntVal).v)
                "/" -> Value.IntVal((l as Value.IntVal).v / (r as Value.IntVal).v)
                "<" -> Value.BoolVal((l as Value.IntVal).v < (r as Value.IntVal).v)
                ">" -> Value.BoolVal((l as Value.IntVal).v > (r as Value.IntVal).v)
                "==" -> Value.BoolVal(l == r)
                "!=" -> Value.BoolVal(l != r)
                "&&" -> Value.BoolVal((l as Value.BoolVal).v && (r as Value.BoolVal).v)
                "||" -> Value.BoolVal((l as Value.BoolVal).v || (r as Value.BoolVal).v)
                else -> throw RuntimeException("未知二元运算符 ${expr.op}")
            }
        }
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    println("========== 演示1: 解释执行一个完整程序 ==========")
    val code = """
        int x = 10;
        int y = x * 2 + 5;
        print y;
        if (y > 20) {
            print "big";
        } else {
            print "small";
        }
        int i = 3;
        while (i > 0) {
            print i;
            i = i - 1;
        }
        print "done";
    """.trimIndent()
    println(code + "\n")
    println("运行输出:")
    val ast = Parser(Lexer(code).tokenize()).parseProgram()
    val interp = Interpreter()
    interp.run(ast, Environment(null))

    println("\n========== 演示2: 作用域在运行时的表现 ==========")
    val scopeCode = """
        int x = 1;
        if (true) {
            int x = 2;
            print x;
        }
        print x;
    """.trimIndent()
    println(scopeCode + "\n")
    println("运行输出（应依次打印 2 和 1）:")
    Interpreter().run(Parser(Lexer(scopeCode).tokenize()).parseProgram(), Environment(null))

    println("\n========== 演示3: 编译 vs 解释 ==========")
    println("  本文件: 直接遍历 AST 求值 —— 解释器")
    println("  第7课:  AST -> 三地址码 -> 用一个小虚拟机执行 —— 模拟'编译+执行'")
    println("  真实 JVM: Java -> 字节码(.class) -> JVM 执行，介于两者之间")
}
