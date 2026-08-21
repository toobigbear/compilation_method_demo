package lexer

// 1. 定义Token类型：就是把我们要识别的单词分个类
enum class TokenType {
    KEYWORD,        // 关键字：比如 print、int、if 这些语言保留的词
    IDENTIFIER,     // 标识符：就是变量名，比如 x、answer 这些用户定义的名字
    NUMBER,         // 数字：字面量数字，比如 42、100
    OPERATOR,       // 运算符：比如 = + - * / 这些符号
    STRING,         // 字符串：双引号包起来的文本
    EOF             // 文本结束：告诉语法分析器，我已经分完所有词了
}
// 2. Token数据结构：每个分词都要存这些信息
data class Token(
    val type: TokenType,    // 刚才说的类型（关键字/数字/字符串...）
    val value: String,      // 分词的实际内容，比如 "print"、"42"、"Hello"
    val line: Int,          // 这个词在源代码第几行（报错的时候可以告诉用户哪错了）
    val column: Int         // 在第几列，也是用来报错定位
)

// 3. 最简单的词法分析器
class SimpleLexer(private val source: String) {
    private var pos = 0  // 当前处理到了哪个位置（光标位置）
    private var line = 1    // 当前在第几行（用来记录位置）
    private var column = 0
    private val keywords = setOf("print", "if", "while", "int", "string")  // 我们先规定好哪些是关键字
    // 对外接口：把整个源代码分词，返回所有Token的列表
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()

        while (pos < source.length) {
            val ch = source[pos]

            when {
                // ---------- 情况1：空白字符（空格、换行、Tab）---------
                ch.isWhitespace() -> {
                    if (ch == '\n') {
                        line++
                        column = 0
                    }
                    pos++
                    column++
                }

                // ---------- 情况2：字母开头 → 是标识符/关键字 ----------
                ch.isLetter() -> {
                    val start = pos// 记下开始位置
                    // 只要后面还是字母/数字，就一直拼
                    while (pos < source.length && source[pos].isLetterOrDigit()) {
                        pos++
                    }
                    // 从开始到结束，截取出完整的单词
                    val value = source.substring(start, pos)
                    // 判断：如果是我们预定义的关键字，就标记为KEYWORD，否则是标识符
                    val type = if (value in keywords) TokenType.KEYWORD else TokenType.IDENTIFIER
                    // 加入结果列表
                    tokens.add(Token(type, value, line, column))
                    // 更新列位置
                    column += (pos - start)
                }

                //--------- 情况3：数字开头 → 是数字字面量 --------
                ch.isDigit() -> {
                    val start = pos
                    while (pos < source.length && source[pos].isDigit()) {
                        pos++
                    }
                    val value = source.substring(start, pos)
                    tokens.add(Token(TokenType.NUMBER, value, line, column))
                    column += (pos - start)
                }

                //--------- 情况4：双引号开头 → 是字符串 -----
                ch == '"' -> {
                    val start = pos
                    pos++ // 跳过开头的引号
                    // 一直读到下一个引号结束
                    while (pos < source.length && source[pos] != '"') {
                        pos++
                    }
                    if (pos >= source.length) throw Exception("Unterminated string at line $line")
                    val value = source.substring(start + 1, pos)
                    tokens.add(Token(TokenType.STRING, value, line, column))
                    pos++ // 跳过结尾的引号
                    column += (pos - start)
                }

                // ---------- 情况5：剩下的都是单个符号（运算符）----------
                else -> {
                    tokens.add(Token(TokenType.OPERATOR, ch.toString(), line, column))
                    pos++
                    column++
                }
            }
        }
        // 所有字符处理完，加一个EOF标记，表示"没词了
        tokens.add(Token(TokenType.EOF, "", line, column))
        return tokens
    }
}