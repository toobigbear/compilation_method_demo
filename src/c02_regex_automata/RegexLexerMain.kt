package c02_regex_automata

import c01_lexer.Token
import c01_lexer.TokenType

/**
 * 第2课补充：用"正则规则表"实现一个词法分析器
 * ============================================================
 * 知识点：
 *   第1课的 Lexer 是手工 if-else 写的。
 *   真正的词法分析器生成器（lex/flex/JFlex）只需要你给出：
 *
 *      Token类型  +  对应的正则表达式
 *
 *   它内部自动把正则编译成 DFA。本文件用 Kotlin 自带的正则库模拟这个过程，
 *   让你体会"声明式"写法：只描述"长什么样"，不用写"怎么扫描"。
 *
 * 两个重要原则（lex 的核心规则）：
 *   1. 最长匹配：同一位置能匹配多条规则时，取匹配文本最长的
 *   2. 规则优先级：长度相同时，排在前面的规则赢（所以关键字要写在标识符前面！）
 * ============================================================
 */

// 一条词法规则：Token类型 + 正则
data class LexRule(val type: TokenType, val pattern: Regex, val skip: Boolean = false)

class RegexLexer(private val source: String) {

    // 规则表 —— 顺序即优先级！关键字必须排在标识符前面
    private val rules = listOf(
        LexRule(TokenType.KEYWORD,    Regex("""\b(int|bool|if|else|while|print|true|false)\b""")),
        LexRule(TokenType.NUMBER,     Regex("""\d+""")),
        LexRule(TokenType.IDENTIFIER, Regex("""[a-zA-Z_][a-zA-Z0-9_]*""")),
        LexRule(TokenType.STRING,     Regex("\"[^\"]*\"")),
        LexRule(TokenType.EQ,         Regex("""==""")),
        LexRule(TokenType.NEQ,        Regex("""!=""")),
        LexRule(TokenType.ASSIGN,     Regex("""=""")),
        LexRule(TokenType.LT,         Regex("""<""")),
        LexRule(TokenType.GT,         Regex(""">""")),
        LexRule(TokenType.PLUS,       Regex("""\+""")),
        LexRule(TokenType.MINUS,      Regex("""-""")),
        LexRule(TokenType.STAR,       Regex("""\*""")),
        LexRule(TokenType.SLASH,      Regex("""/""")),
        LexRule(TokenType.SEMI,       Regex(""";""")),
        LexRule(TokenType.LPAREN,     Regex("""\(""")),
        LexRule(TokenType.RPAREN,     Regex("""\)""")),
        LexRule(TokenType.LBRACE,     Regex("""\{""")),
        LexRule(TokenType.RBRACE,     Regex("""\}""")),
        // 空白和注释：匹配但不产生 Token（skip = true）
        LexRule(TokenType.KEYWORD,    Regex("""//[^\n]*"""), skip = true),
        LexRule(TokenType.KEYWORD,    Regex("""\s+"""), skip = true),
    )

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var line = 1
        var lineStart = 0   // 当前行起始下标，用于算列号

        while (pos < source.length) {
            val rest = source.substring(pos)

            // 尝试所有规则，按"最长匹配 + 规则顺序"选最优
            var best: Pair<LexRule, String>? = null
            for (rule in rules) {
                val m = rule.pattern.find(rest) ?: continue
                if (m.range.first != 0) continue                // 必须从头匹配
                val text = m.value
                if (best == null || text.length > best!!.second.length) {
                    best = rule to text
                }
            }
            if (best == null) {
                throw Exception("第${line}行: 无法识别的字符 '${source[pos]}'")
            }

            val (rule, text) = best
            if (!rule.skip) {
                // 字符串去掉首尾引号
                val value = if (rule.type == TokenType.STRING) text.substring(1, text.length - 1) else text
                tokens.add(Token(rule.type, value, line, pos - lineStart + 1))
            }
            // 更新行列号
            for (ch in text) {
                if (ch == '\n') { line++; lineStart = pos + text.indexOf(ch) + 1 }
            }
            pos += text.length
        }
        tokens.add(Token(TokenType.EOF, "", line, 0))
        return tokens
    }
}

fun main() {
    val code = """
        int count = 10;   // 正则版词法分析器
        if (count == 10) {
            print "ok";
        }
    """.trimIndent()

    println("========== 源代码 ==========")
    println(code)
    println("\n========== 正则规则表驱动的分词结果 ==========")
    RegexLexer(code).tokenize().forEach { println(it) }

    println("\n========== 对比手工 Lexer（第1课） ==========")
    c01_lexer.Lexer(code).tokenize().forEach { println(it) }

    println("\n========== 关键结论 ==========")
    println("1. 两种写法结果一样：手工扫描 与 正则规则表 是等价的")
    println("2. 正则写法更声明式：加一种 Token 只需加一行规则")
    println("3. 规则顺序很重要：'==' 要写在 '=' 前面，关键字要写在标识符前面")
}
