package c01_lexer

/**
 * 第1课：词法分析器（Lexer / Scanner）
 * ============================================================
 * 知识点：
 *   词法分析是编译的第一步：把"字符流"切成一个个 Token（单词）。
 *   源代码  "int x = 42;"  →  [KEYWORD:int] [IDENT:x] [ASSIGN:=] [NUMBER:42] [SEMI:;]
 *
 * 本文件实现要点：
 *   1. Token 分类：关键字 / 标识符 / 数字 / 字符串 / 运算符 / 分隔符
 *   2. 单字符 vs 多字符运算符：'=' 和 '==' 怎么区分？——最长匹配（向前多看一个字符）
 *   3. 注释处理：遇到 // 一直跳到行尾
 *   4. 行列号：每个 Token 记录位置，报错时能告诉用户"第几行第几列错了"
 *   5. 错误不直接抛异常，而是收集起来——真实编译器一次报出所有错误
 * ============================================================
 */

// ---------------- 1. Token 的类型 ----------------
enum class TokenType {
    // 字面量与名字
    KEYWORD,        // 关键字：int、if、while、print ...（语言保留，用户不能当变量名）
    IDENTIFIER,     // 标识符：用户起的名字，比如 x、total、name
    NUMBER,         // 数字字面量：42、100
    STRING,         // 字符串字面量："hello"

    // 运算符（多字符的优先匹配：先试着匹配 ==，失败再匹配 =）
    ASSIGN,         // =
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    AND,            // &&
    OR,             // ||
    NOT,            // !

    // 分隔符
    SEMI,           // ;
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }

    EOF             // 文件结束标记：告诉后面的语法分析器"没有更多单词了"
}

// ---------------- 2. Token 数据结构 ----------------
data class Token(
    val type: TokenType,    // Token 类别
    val value: String,      // 原文内容，比如 "print"、"42"
    val line: Int,          // 在源代码第几行（报错定位用）
    val column: Int         // 第几列
) {
    override fun toString() = "%-10s '%s'  (第%d行, 第%d列)".format(type, value, line, column)
}

// ---------------- 3. 词法错误 ----------------
class LexError(message: String) : Exception(message)

// ---------------- 4. 词法分析器本体 ----------------
class Lexer(private val source: String) {

    private var pos = 0        // 当前扫描到哪个字符（光标）
    private var line = 1       // 当前行号
    private var column = 1     // 当前列号

    // 关键字表：在编译前约定好的保留字
    private val keywords = setOf("int", "bool", "if", "else", "while", "print", "true", "false")

    // 入口：扫描整个源代码，返回 Token 列表
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < source.length) {
            skipWhitespaceAndComments()          // 空白和注释不产生 Token，直接跳过
            if (pos >= source.length) break
            tokens.add(scanToken())              // 扫描出一个 Token
        }
        tokens.add(Token(TokenType.EOF, "", line, column))  // 最后放一个 EOF
        return tokens
    }

    // ---------- 跳过空白字符和注释 ----------
    private fun skipWhitespaceAndComments() {
        while (pos < source.length) {
            val ch = source[pos]
            when {
                ch == '\n' -> { pos++; line++; column = 1 }   // 换行：行号+1，列号归1
                ch.isWhitespace() -> { pos++; column++ }      // 空格、Tab：直接跳过
                // 行注释 // ... 一直跳到行尾
                ch == '/' && peek() == '/' -> {
                    while (pos < source.length && source[pos] != '\n') pos++
                }
                else -> return                                 // 遇到其他字符说明有实际内容
            }
        }
    }

    // ---------- 扫描出一个 Token（核心分发逻辑） ----------
    private fun scanToken(): Token {
        val ch = source[pos]
        val startLine = line
        val startCol = column

        return when {
            // 字母或下划线开头 → 标识符 或 关键字
            // 规则：只要后面还是 字母/数字/下划线 就一直拼
            ch.isLetter() || ch == '_' -> {
                val start = pos
                while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) {
                    pos++
                }
                val word = source.substring(start, pos)
                column += pos - start
                // 在关键字表里 → KEYWORD，否则 → IDENTIFIER
                val type = if (word in keywords) TokenType.KEYWORD else TokenType.IDENTIFIER
                Token(type, word, startLine, startCol)
            }

            // 数字开头 → 数字字面量
            ch.isDigit() -> {
                val start = pos
                while (pos < source.length && source[pos].isDigit()) pos++
                val num = source.substring(start, pos)
                column += pos - start
                Token(TokenType.NUMBER, num, startLine, startCol)
            }

            // 双引号开头 → 字符串字面量
            ch == '"' -> {
                pos++; column++                          // 跳过开头的 "
                val start = pos
                while (pos < source.length && source[pos] != '"') {
                    pos++; column++
                }
                if (pos >= source.length) {
                    throw LexError("第${startLine}行第${startCol}列: 字符串没有结束的双引号")
                }
                val str = source.substring(start, pos)
                pos++; column++                          // 跳过结尾的 "
                Token(TokenType.STRING, str, startLine, startCol)
            }

            // 运算符与分隔符 —— 注意"最长匹配"原则：
            // 先看两个字符能不能组成 ==、!=、&&、||，不行再看单字符
            else -> {
                val two = if (pos + 1 < source.length) source.substring(pos, pos + 2) else ""
                val type: TokenType
                val text: String
                when {
                    two == "==" -> { type = TokenType.EQ;   text = two }
                    two == "!=" -> { type = TokenType.NEQ;  text = two }
                    two == "&&" -> { type = TokenType.AND;  text = two }
                    two == "||" -> { type = TokenType.OR;   text = two }
                    ch == '=' -> { type = TokenType.ASSIGN; text = "=" }
                    ch == '<' -> { type = TokenType.LT;     text = "<" }
                    ch == '>' -> { type = TokenType.GT;     text = ">" }
                    ch == '+' -> { type = TokenType.PLUS;   text = "+" }
                    ch == '-' -> { type = TokenType.MINUS;  text = "-" }
                    ch == '*' -> { type = TokenType.STAR;   text = "*" }
                    ch == '/' -> { type = TokenType.SLASH;  text = "/" }
                    ch == '!' -> { type = TokenType.NOT;    text = "!" }
                    ch == ';' -> { type = TokenType.SEMI;   text = ";" }
                    ch == '(' -> { type = TokenType.LPAREN; text = "(" }
                    ch == ')' -> { type = TokenType.RPAREN; text = ")" }
                    ch == '{' -> { type = TokenType.LBRACE; text = "{" }
                    ch == '}' -> { type = TokenType.RBRACE; text = "}" }
                    else -> throw LexError("第${startLine}行第${startCol}列: 无法识别的字符 '$ch'")
                }
                pos += text.length
                column += text.length
                Token(type, text, startLine, startCol)
            }
        }
    }

    // 向前看一个字符，但不移动光标（peek = 偷看）
    private fun peek(): Char = if (pos + 1 < source.length) source[pos + 1] else ' '
}
