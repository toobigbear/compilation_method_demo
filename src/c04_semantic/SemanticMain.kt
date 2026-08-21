package c04_semantic

import c01_lexer.Lexer
import c03_parser.Expr
import c03_parser.Parser
import c03_parser.Stmt

/**
 * 第4课：语义分析 —— 符号表 与 类型检查
 * ============================================================
 * 知识点：
 *   语法分析只保证"结构正确"，不保证"意思正确"。
 *   比如  int x = y;  语法完全合法，但如果 y 从未定义过，程序没法运行。
 *   语义分析（Semantic Analysis）就是检查这些"意思层面"的规则：
 *
 *     1. 符号表（Symbol Table）：记录每个变量的名字、类型、定义位置
 *        - 作用域（Scope）：{ } 里的变量出了大括号就失效
 *        - 遮蔽（Shadowing）：内层可以定义同名变量覆盖外层
 *
 *     2. 类型检查（Type Checking）：
 *        - 变量必须先声明后使用
 *        - 不能重复声明
 *        - 运算符两边类型要匹配：int + int ✔，int + string ✘
 *        - 赋值左右类型要一致
 *        - if/while 的条件必须是 bool
 *
 *   方法：遍历一遍 AST，边走边查/填符号表 —— 这就是"一遍扫描"语义分析。
 * ============================================================
 */

// ---------------- 1. 类型系统 ----------------
enum class Type { INT, BOOL, STRING, UNKNOWN }

// ---------------- 2. 符号表条目 ----------------
data class Symbol(val name: String, val type: Type, val line: Int)

// ---------------- 3. 符号表（带作用域链） ----------------
// 每个作用域一张表；查不到就沿 parent 链往外查 —— 这就是"嵌套作用域"的实现
class Scope(val parent: Scope?, val label: String) {
    private val symbols = mutableMapOf<String, Symbol>()

    // 在当前作用域声明变量；重复声明返回 false
    fun declare(sym: Symbol): Boolean {
        if (symbols.containsKey(sym.name)) return false
        symbols[sym.name] = sym
        return true
    }

    // 查找：先查自己，再递归查外层
    fun lookup(name: String): Symbol? = symbols[name] ?: parent?.lookup(name)
}

// ---------------- 4. 语义错误 ----------------
class SemanticError(val line: Int, message: String) : Exception("第${line}行: $message")

// ---------------- 5. 语义分析器 ----------------
class SemanticAnalyzer {
    val errors = mutableListOf<SemanticError>()

    private fun error(line: Int, msg: String) {
        errors.add(SemanticError(line, msg))
    }

    // ===== 语句检查 =====
    fun check(stmts: List<Stmt>, scope: Scope) {
        for (stmt in stmts) {
            when (stmt) {
                is Stmt.VarDecl -> {
                    val initType = checkExpr(stmt.init, scope)
                    if (initType != Type.INT && initType != Type.UNKNOWN) {
                        error(0, "变量 '${stmt.name}' 声明为 int，但初始化表达式是 $initType")
                    }
                    if (!scope.declare(Symbol(stmt.name, Type.INT, 0))) {
                        error(0, "变量 '${stmt.name}' 重复声明")
                    } else {
                        println("  [符号表] 声明变量: ${stmt.name} : INT  (作用域: ${scope.label})")
                    }
                }
                is Stmt.Assign -> {
                    val sym = scope.lookup(stmt.name)
                    if (sym == null) {
                        error(0, "赋值给未声明的变量 '${stmt.name}'")
                    }
                    val valueType = checkExpr(stmt.value, scope)
                    if (sym != null && valueType != Type.UNKNOWN && sym.type != valueType) {
                        error(0, "不能把 $valueType 赋给 ${sym.type} 类型的变量 '${stmt.name}'")
                    }
                }
                is Stmt.Print -> {
                    val t = checkExpr(stmt.value, scope)
                    println("  [检查] print 表达式类型 = $t")
                }
                is Stmt.If -> {
                    val condType = checkExpr(stmt.cond, scope)
                    if (condType != Type.BOOL && condType != Type.UNKNOWN) {
                        error(0, "if 的条件必须是 bool，但这里是 $condType")
                    }
                    // 进入新作用域
                    check(stmt.thenBlock, Scope(scope, "if-then"))
                    check(stmt.elseBlock, Scope(scope, "if-else"))
                }
                is Stmt.While -> {
                    val condType = checkExpr(stmt.cond, scope)
                    if (condType != Type.BOOL && condType != Type.UNKNOWN) {
                        error(0, "while 的条件必须是 bool，但这里是 $condType")
                    }
                    check(stmt.body, Scope(scope, "while-body"))
                }
            }
        }
    }

    // ===== 表达式检查（返回推导出的类型） =====
    private fun checkExpr(expr: Expr, scope: Scope): Type = when (expr) {
        is Expr.NumberLit -> Type.INT
        is Expr.StringLit -> Type.STRING
        is Expr.BoolLit -> Type.BOOL

        is Expr.Variable -> {
            val sym = scope.lookup(expr.name)
            if (sym == null) {
                error(0, "使用了未声明的变量 '${expr.name}'")
                Type.UNKNOWN
            } else sym.type
        }

        is Expr.Unary -> {
            val t = checkExpr(expr.operand, scope)
            when (expr.op) {
                "-" -> if (t == Type.INT || t == Type.UNKNOWN) Type.INT
                       else { error(0, "取负号 '-' 只能用于 int，不能用于 $t"); Type.UNKNOWN }
                "!" -> if (t == Type.BOOL || t == Type.UNKNOWN) Type.BOOL
                       else { error(0, "逻辑非 '!' 只能用于 bool，不能用于 $t"); Type.UNKNOWN }
                else -> Type.UNKNOWN
            }
        }

        is Expr.Binary -> {
            val lt = checkExpr(expr.left, scope)
            val rt = checkExpr(expr.right, scope)
            if (lt == Type.UNKNOWN || rt == Type.UNKNOWN) return Type.UNKNOWN
            when (expr.op) {
                "+", "-", "*", "/" -> {
                    if (lt == Type.INT && rt == Type.INT) Type.INT
                    else { error(0, "算术运算 '${expr.op}' 需要两个 int，但得到 $lt 和 $rt"); Type.UNKNOWN }
                }
                ">", "<" -> {
                    if (lt == Type.INT && rt == Type.INT) Type.BOOL
                    else { error(0, "比较运算 '${expr.op}' 需要两个 int，但得到 $lt 和 $rt"); Type.UNKNOWN }
                }
                "==", "!=" -> {
                    if (lt == rt) Type.BOOL
                    else { error(0, "'${expr.op}' 两边类型不一致: $lt 和 $rt"); Type.UNKNOWN }
                }
                "&&", "||" -> {
                    if (lt == Type.BOOL && rt == Type.BOOL) Type.BOOL
                    else { error(0, "逻辑运算 '${expr.op}' 需要两个 bool，但得到 $lt 和 $rt"); Type.UNKNOWN }
                }
                else -> Type.UNKNOWN
            }
        }
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    // ---------- 演示1：语义正确的程序 ----------
    println("========== 演示1: 正确的程序（能过语义分析） ==========")
    val goodCode = """
        int x = 10;
        int y = 20;
        bool ok = x < y;
        if (ok) {
            int z = x + y;   // 内层作用域可以定义新变量
            print z;
        }
        print x;
    """.trimIndent()
    runAnalysis(goodCode)

    // ---------- 演示2：使用未声明的变量 ----------
    println("\n========== 演示2: 使用未声明的变量 ==========")
    runAnalysis("int x = y + 1;")

    // ---------- 演示3：重复声明 ----------
    println("\n========== 演示3: 重复声明 ==========")
    runAnalysis("int x = 1; int x = 2;")

    // ---------- 演示4：类型不匹配 ----------
    println("\n========== 演示4: 类型错误 ==========")
    runAnalysis("""
        int a = "hello";
        int b = 1 + true;
        bool c = 5;
        if (a + 1) { print a; }
    """.trimIndent())

    // ---------- 演示5：作用域 ----------
    println("\n========== 演示5: 作用域 —— 出了大括号变量就失效 ==========")
    runAnalysis("""
        if (true) {
            int temp = 99;
        }
        print temp;
    """.trimIndent())

    // ---------- 演示6：遮蔽（Shadowing） ----------
    println("\n========== 演示6: 遮蔽 —— 内层可以覆盖外层同名变量 ==========")
    runAnalysis("""
        int x = 1;
        if (true) {
            int x = 2;   // 合法！这是内层的新变量
            print x;     // 打印的是内层的 x (=2)
        }
        print x;         // 打印的是外层的 x (=1)
    """.trimIndent())
}

private fun runAnalysis(code: String) {
    println("源代码:")
    println(code.prependIndent("  "))
    println("分析过程:")
    try {
        val ast = Parser(Lexer(code).tokenize()).parseProgram()
        val analyzer = SemanticAnalyzer()
        analyzer.check(ast, Scope(null, "global"))
        if (analyzer.errors.isEmpty()) {
            println("结果: ✔ 语义分析通过，没有错误")
        } else {
            println("结果: ✘ 发现 ${analyzer.errors.size} 个语义错误:")
            analyzer.errors.forEach { println("  - ${it.message}") }
        }
    } catch (e: Exception) {
        println("前置阶段出错: ${e.message}")
    }
}
