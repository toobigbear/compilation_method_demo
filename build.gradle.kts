plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // 仅标准库即可，无任何第三方依赖
}

kotlin {
    jvmToolchain(26)
}

// 把 src 作为源码目录（兼容原来的 IntelliJ 工程结构）
sourceSets.main {
    java.srcDir("src")
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
}

tasks.withType<Test>().configureEach {
    systemProperty("file.encoding", "UTF-8")
}

// ============ 为每个示例注册一个运行任务 ============
// 用法: gradle runC01  (或 gradle runC01Lexer 等，见下)
// 每个任务对应课程里的一个 main 函数
tasks.register<JavaExec>("runC01Lexer") {
    group = "compiler-demo"; description = "第1课 手工词法分析器"
    mainClass.set("c01_lexer.LexerMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC02NfaDfa") {
    group = "compiler-demo"; description = "第2课 NFA/DFA 自动机"
    mainClass.set("c02_regex_automata.NfaDfaMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC02RegexLexer") {
    group = "compiler-demo"; description = "第2课补充 正则规则表驱动的词法分析器"
    mainClass.set("c02_regex_automata.RegexLexerMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC03Parser") {
    group = "compiler-demo"; description = "第3课 递归下降语法分析"
    mainClass.set("c03_parser.GrammarKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC03Ambiguity") {
    group = "compiler-demo"; description = "第3课补充 文法二义性"
    mainClass.set("c03_parser.AmbiguityMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC03FirstFollow") {
    group = "compiler-demo"; description = "第3课补充 FIRST集/预测分析表"
    mainClass.set("c03_parser.FirstFollowMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC04Semantic") {
    group = "compiler-demo"; description = "第4课 语义分析/符号表/类型检查"
    mainClass.set("c04_semantic.SemanticMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC05IrGen") {
    group = "compiler-demo"; description = "第5课 三地址码生成"
    mainClass.set("c05_ir_optimize.IrGenMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC05Optimize") {
    group = "compiler-demo"; description = "第6课 代码优化"
    mainClass.set("c05_ir_optimize.OptimizeMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC06Interpreter") {
    group = "compiler-demo"; description = "第7课 AST 解释器"
    mainClass.set("c06_runtime.InterpreterMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC06Codegen") {
    group = "compiler-demo"; description = "第8课 目标代码生成（伪汇编）"
    mainClass.set("c06_runtime.CodegenMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC06Vm") {
    group = "compiler-demo"; description = "第8课补充 三地址码虚拟机"
    mainClass.set("c06_runtime.VmMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("runC07Full") {
    group = "compiler-demo"; description = "第9课 完整编译流水线"
    mainClass.set("c07_full_pipeline.FullPipelineMainKt"); classpath = sourceSets.main.get().runtimeClasspath
}
