package c01_lexer

/**
 * 第1课运行入口：词法分析演示
 * ============================================================
 * 运行后你会看到：
 *   1. 一段源代码被切成 Token 列表（每个 Token 带类型、内容、行列号）
 *   2. 最长匹配演示："a == b" 中的 == 被识别为一个 Token，而不是两个 =
 *   3. 注释被跳过
 *   4. 词法错误演示：不完整的字符串、非法字符，报错会带行列号
 * ============================================================
 */
fun main() {
    // ---------- 演示1：正常源代码分词 ----------
    val sourceCode = """
        // 这是一个迷你语言的源代码
        int x = 42;
        int y = x + 8;
        bool ok = x == 50 && y != 0;
        if (x > 10) {
            print "big";
        } else {
            print "small";
        }
    """.trimIndent()

    println("========== 源代码 ==========")
    println(sourceCode)
    println("\n========== 分词结果 ==========")

    try {
        val tokens = Lexer(sourceCode).tokenize()
        tokens.forEach { println(it) }
    } catch (e: LexError) {
        println("词法错误: ${e.message}")
    }

    // ---------- 演示2：最长匹配 ----------
    // 经典问题：'=' 和 '==' 都是合法 Token，"a == b" 应该怎么切？
    // 答案：最长匹配（Maximal Munch）——能匹配长的就不匹配短的
    println("\n========== 演示2: 最长匹配 ==========")
    println("源代码:  a == b;   应该切成 [IDENT:a] [==] [IDENT:b] [;]，而不是两个 =")
    Lexer("a == b;").tokenize().forEach { println(it) }
    println()
    println("源代码:  a = b;    单个 = 是赋值")
    Lexer("a = b;").tokenize().forEach { println(it) }

    // ---------- 演示3：词法错误 ----------
    println("\n========== 演示3: 词法错误（报错带行列号） ==========")
    try {
        Lexer("""int x = "没结尾的字符串;""").tokenize()
    } catch (e: LexError) {
        println("捕获到词法错误 -> ${e.message}")
    }
    try {
        Lexer("int x = 10 @ 20;").tokenize()
    } catch (e: LexError) {
        println("捕获到词法错误 -> ${e.message}")
    }
}
