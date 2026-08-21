package c03_parser

import c01_lexer.Lexer
import c01_lexer.Token
import c01_lexer.TokenType

/**
 * 第3课：语法分析（Parser）—— 把 Token 序列变成抽象语法树 AST
 * ============================================================
 * 知识点：
 *   词法分析告诉我们"有哪些单词"，语法分析回答"这些单词组成了什么结构"。
 *   比如 Token 序列  [2] [+] [3] [*] [4]  对应的语法结构是：
 *
 *            (+)
 *           /   \
 *          2    (*)
 *              /   \
 *             3     4      ← 因为 * 优先级比 + 高
 *
 * 本文件的文法（用 EBNF 风格描述）：
 *   program     -> stmt* EOF
 *   stmt        -> "int" ID "=" expr ";"          （变量声明）
 *                | ID "=" expr ";"                （赋值）
 *                | "print" expr ";"               （打印）
 *                | "if" "(" expr ")" block ("else" block)?
 *                | "while" "(" expr ")" block
 *   block       -> "{" stmt* "}"
 *   expr        -> equality
 *   equality    -> comparison ( ("=="|"!=") comparison )*
 *   comparison  -> additive ( (">"|"<") additive )*
 *   additive    -> multiplicative ( ("+"|"-") multiplicative )*
 *   multiplicative -> unary ( ("*"|"/") unary )*
 *   unary       -> "!" unary | "-" unary | primary
 *   primary     -> NUMBER | STRING | "true" | "false" | ID | "(" expr ")"
 *
 * 每个非终结符对应一个解析函数 —— 这就叫"递归下降"（Recursive Descent）。
 * 函数调用的层级天然体现了运算符优先级：越深的层级优先级越高。
 * ============================================================
 */

// ---------------- 1. AST 节点定义 ----------------
sealed class Expr {
    data class NumberLit(val value: Int) : Expr()
    data class StringLit(val value: String) : Expr()
    data class BoolLit(val value: Boolean) : Expr()
    data class Variable(val name: String) : Expr()
    data class Binary(val op: String, val left: Expr, val right: Expr) : Expr()
    data class Unary(val op: String, val operand: Expr) : Expr()
}

sealed class Stmt {
    data class VarDecl(val name: String, val init: Expr) : Stmt()
    data class Assign(val name: String, val value: Expr) : Stmt()
    data class Print(val value: Expr) : Stmt()
    data class If(val cond: Expr, val thenBlock: List<Stmt>, val elseBlock: List<Stmt>) : Stmt()
    data class While(val cond: Expr, val body: List<Stmt>) : Stmt()
}

// 语法错误
class ParseError(message: String) : Exception(message)

// ---------------- 2. 递归下降 Parser ----------------
class Parser(private val tokens: List<Token>) {

    private var pos = 0   // 当前读到第几个 Token

    // ===== 工具方法 =====
    private fun peek(): Token = tokens[pos]                          // 看当前 Token
    private fun previous(): Token = tokens[pos - 1]
    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
    private fun advance(): Token { if (!isAtEnd()) pos++; return previous() }
    private fun check(type: TokenType) = !isAtEnd() && peek().type == type

    // 如果当前 Token 是其中一种类型，就"吃掉"它并返回 true
    private fun match(vararg types: TokenType): Boolean {
        for (t in types) if (check(t)) { advance(); return true }
        return false
    }

    // 要求当前 Token 必须是指定类型，否则报错
    private fun expect(type: TokenType, what: String): Token {
        if (check(type)) return advance()
        val t = peek()
        throw ParseError("第${t.line}行: 期望 $what，但遇到了 '${t.value}' (${t.type})")
    }

    // ===== 入口：解析整个程序 =====
    fun parseProgram(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            stmts.add(parseStmt())
        }
        return stmts
    }

    // ===== 语句 =====
    private fun parseStmt(): Stmt = when {
        // int x = expr ;
        match(TokenType.KEYWORD) && previous().value == "int" -> {
            val name = expect(TokenType.IDENTIFIER, "变量名").value
            expect(TokenType.ASSIGN, "'='")
            val init = parseExpr()
            expect(TokenType.SEMI, "';'")
            Stmt.VarDecl(name, init)
        }
        // print expr ;
        check(TokenType.KEYWORD) && peek().value == "print" -> {
            advance()
            val value = parseExpr()
            expect(TokenType.SEMI, "';'")
            Stmt.Print(value)
        }
        // if (expr) { ... } else { ... }
        check(TokenType.KEYWORD) && peek().value == "if" -> {
            advance()
            expect(TokenType.LPAREN, "'('")
            val cond = parseExpr()
            expect(TokenType.RPAREN, "')'")
            val thenBlock = parseBlock()
            val elseBlock = if (check(TokenType.KEYWORD) && peek().value == "else") {
                advance(); parseBlock()
            } else emptyList()
            Stmt.If(cond, thenBlock, elseBlock)
        }
        // while (expr) { ... }
        check(TokenType.KEYWORD) && peek().value == "while" -> {
            advance()
            expect(TokenType.LPAREN, "'('")
            val cond = parseExpr()
            expect(TokenType.RPAREN, "')'")
            Stmt.While(cond, parseBlock())
        }
        // x = expr ;
        check(TokenType.IDENTIFIER) -> {
            val name = advance().value
            expect(TokenType.ASSIGN, "'='")
            val value = parseExpr()
            expect(TokenType.SEMI, "';'")
            Stmt.Assign(name, value)
        }
        else -> {
            val t = peek()
            throw ParseError("第${t.line}行: 无法理解语句的开头 '${t.value}'")
        }
    }

    private fun parseBlock(): List<Stmt> {
        expect(TokenType.LBRACE, "'{'")
        val stmts = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            stmts.add(parseStmt())
        }
        expect(TokenType.RBRACE, "'}'")
        return stmts
    }

    // ===== 表达式（优先级从低到高，一层一层往下调） =====

    // expr -> equality
    private fun parseExpr(): Expr = parseEquality()

    // equality -> comparison ( ("=="|"!=") comparison )*
    private fun parseEquality(): Expr {
        var left = parseComparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = previous().value
            val right = parseComparison()
            left = Expr.Binary(op, left, right)
        }
        return left
    }

    // comparison -> additive ( (">"|"<") additive )*
    private fun parseComparison(): Expr {
        var left = parseAdditive()
        while (match(TokenType.GT, TokenType.LT)) {
            val op = previous().value
            val right = parseAdditive()
            left = Expr.Binary(op, left, right)
        }
        return left
    }

    // additive -> multiplicative ( ("+"|"-") multiplicative )*
    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous().value
            val right = parseMultiplicative()
            left = Expr.Binary(op, left, right)
        }
        return left
    }

    // multiplicative -> unary ( ("*"|"/") unary )*
    private fun parseMultiplicative(): Expr {
        var left = parseUnary()
        while (match(TokenType.STAR, TokenType.SLASH)) {
            val op = previous().value
            val right = parseUnary()
            left = Expr.Binary(op, left, right)
        }
        return left
    }

    // unary -> ("!"|"-") unary | primary      （右递归，所以是右结合）
    private fun parseUnary(): Expr {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val op = previous().value
            val operand = parseUnary()
            return Expr.Unary(op, operand)
        }
        return parsePrimary()
    }

    // primary -> NUMBER | STRING | true | false | ID | "(" expr ")"
    private fun parsePrimary(): Expr = when {
        match(TokenType.NUMBER) -> Expr.NumberLit(previous().value.toInt())
        match(TokenType.STRING) -> Expr.StringLit(previous().value)
        match(TokenType.IDENTIFIER) -> Expr.Variable(previous().value)
        check(TokenType.KEYWORD) && peek().value == "true" -> { advance(); Expr.BoolLit(true) }
        check(TokenType.KEYWORD) && peek().value == "false" -> { advance(); Expr.BoolLit(false) }
        match(TokenType.LPAREN) -> {
            val inner = parseExpr()
            expect(TokenType.RPAREN, "')'")
            inner
        }
        else -> {
            val t = peek()
            throw ParseError("第${t.line}行: 这里应该是一个表达式，但遇到了 '${t.value}'")
        }
    }
}

// ---------------- 3. 把 AST 打印成树形（直观展示语法结构） ----------------
object AstPrinter {

    fun printProgram(stmts: List<Stmt>) {
        println("Program")
        stmts.forEach { printStmt(it, "  ") }
    }

    private fun printStmt(stmt: Stmt, indent: String) {
        when (stmt) {
            is Stmt.VarDecl -> {
                println("${indent}VarDecl ${stmt.name}")
                printExpr(stmt.init, "$indent  ")
            }
            is Stmt.Assign -> {
                println("${indent}Assign ${stmt.name}")
                printExpr(stmt.value, "$indent  ")
            }
            is Stmt.Print -> {
                println("${indent}Print")
                printExpr(stmt.value, "$indent  ")
            }
            is Stmt.If -> {
                println("${indent}If")
                println("$indent  cond:")
                printExpr(stmt.cond, "$indent    ")
                println("$indent  then:")
                stmt.thenBlock.forEach { printStmt(it, "$indent    ") }
                if (stmt.elseBlock.isNotEmpty()) {
                    println("$indent  else:")
                    stmt.elseBlock.forEach { printStmt(it, "$indent    ") }
                }
            }
            is Stmt.While -> {
                println("${indent}While")
                println("$indent  cond:")
                printExpr(stmt.cond, "$indent    ")
                println("$indent  body:")
                stmt.body.forEach { printStmt(it, "$indent    ") }
            }
        }
    }

    private fun printExpr(expr: Expr, indent: String) {
        when (expr) {
            is Expr.NumberLit -> println("${indent}Number(${expr.value})")
            is Expr.StringLit -> println("${indent}String(\"${expr.value}\")")
            is Expr.BoolLit -> println("${indent}Bool(${expr.value})")
            is Expr.Variable -> println("${indent}Var(${expr.name})")
            is Expr.Unary -> {
                println("${indent}Unary(${expr.op})")
                printExpr(expr.operand, "$indent  ")
            }
            is Expr.Binary -> {
                println("${indent}Binary(${expr.op})")
                printExpr(expr.left, "$indent  ")
                printExpr(expr.right, "$indent  ")
            }
        }
    }
}

// ---------------- 运行演示 ----------------
fun main() {
    // ---------- 演示1：运算符优先级 ----------
    println("========== 演示1: 优先级 —— 2 + 3 * 4 应该怎么组合？ ==========")
    val code1 = "print 2 + 3 * 4;"
    println("源代码: $code1\n")
    val tokens1 = Lexer(code1).tokenize()
    val ast1 = Parser(tokens1).parseProgram()
    AstPrinter.printProgram(ast1)
    println("↑ 注意：* 在树的下层（先算），+ 在上层（后算），优先级自动体现")

    // ---------- 演示2：括号改变结构 ----------
    println("\n========== 演示2: 括号 —— (2 + 3) * 4 ==========")
    val code2 = "print (2 + 3) * 4;"
    val ast2 = Parser(Lexer(code2).tokenize()).parseProgram()
    AstPrinter.printProgram(ast2)

    // ---------- 演示3：完整程序 ----------
    println("\n========== 演示3: 一个完整程序的 AST ==========")
    val code3 = """
        int x = 10;
        int y = x * 2 + 5;
        if (y > 20) {
            print "big";
        } else {
            print "small";
        }
        while (x > 0) {
            x = x - 3;
        }
    """.trimIndent()
    println(code3)
    println()
    val ast3 = Parser(Lexer(code3).tokenize()).parseProgram()
    AstPrinter.printProgram(ast3)

    // ---------- 演示4：语法错误 ----------
    println("\n========== 演示4: 语法错误（少了分号） ==========")
    try {
        Parser(Lexer("int x = 1").tokenize()).parseProgram()
    } catch (e: ParseError) {
        println("捕获到语法错误 -> ${e.message}")
    }
    try {
        Parser(Lexer("int = 1;").tokenize()).parseProgram()
    } catch (e: ParseError) {
        println("捕获到语法错误 -> ${e.message}")
    }
}
