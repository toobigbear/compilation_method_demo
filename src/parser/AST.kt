package parser
//定义抽象语法树节点
// 密封类：所有AST节点都继承这个，限定只能在这里定义子类
sealed class ASTNode {
    // print 语句：输出一个字符串
    data class PrintStatement(val value: String) : ASTNode()
    // int 变量赋值：定义变量并赋值
    data class Assignment(val name: String, val value: Int) : ASTNode()
    // 整个程序：包含多个语句
    data class Program(val statements: List<ASTNode>) : ASTNode()
}