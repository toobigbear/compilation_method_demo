package lexer
// 程序入口，我们测试词法分析用的
fun main() {
    // 准备我们要编译的源代码，这里是一个简单的例子
    val sourceCode = """
        print "Hello, World!"
        int x = 42
    """.trimIndent()

    //我们准备一个错误的源代码看看运行结果
    //val sourceCode = """    print "这是个没结尾的字符串""".trimIndent()

    // trimIndent()是Kotlin的工具，去掉多余缩进，让我们写多行字符串更干净
    println("=== 源代码 ===")
    println(sourceCode)
    println("\n=== 分词结果 ===")

    // 创建我们写的词法分析器实例，把源代码传进去
    val lexer = SimpleLexer(sourceCode)
    // 调用tokenize()方法，开始分词，得到结果
    val tokens = lexer.tokenize()
    // 遍历每个Token，打印出来看结果
    tokens.forEach { token ->
        println("${token.type}: '${token.value}' | 行: ${token.line}, 列: ${token.column}")
    }
}