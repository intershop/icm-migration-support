#!/usr/bin/env -S kotlinc -script
// https://github.com/bernaferrari/GradleKotlinConverter/blob/master/gradlekotlinconverter.kts
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Predicate
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.system.exitProcess
import kotlin.text.RegexOption.*

// Bernardo Ferrari
// APACHE-2 License
val DEBUG = false

// from https://github.com/importre/crayon
fun String.bold() = "\u001b[1m${this}\u001b[0m"

fun String.cyan() = "\u001b[36m${this}\u001b[0m"
fun String.green() = "\u001b[32m${this}\u001b[0m"
fun String.magenta() = "\u001b[35m${this}\u001b[0m"
fun String.red() = "\u001b[31m${this}\u001b[0m"
fun String.yellow() = "\u001b[33m${this}\u001b[0m"

fun currentTimeFormatted(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

val intro = """
+---------------------------------------------------------------------------------------+
+                        ${"Welcome to Gradle Kotlin DSL converter!".yellow()}                        +
+---------------------------------------------------------------------------------------+
+ This is a helper tool, much like Android Studio's Java -> Kotlin converter.           +
+ It is not perfect and there will be things to be manually solved, but it helps A LOT. +
+---------------------------------------------------------------------------------------+
+ Usage:                                                                                +
+     Clipboard mode:                                                                   +
+         ${"./gradlekotlinconverter.kts".cyan()}                                                   +
+     Single file mode:                                                                 +
+         ${"./gradlekotlinconverter.kts <file>".cyan()}                                            +
+     Single build.gradle from directory mode:                                          +
+         ${"./gradlekotlinconverter.kts <directory> [deleteInputFile]".cyan()}                     +
+     Recursive build.gradle mode:                                                      +
+         ${"./gradlekotlinconverter.kts <rootDirectory> recursive [deleteInputFile]".cyan()}       +
+                                                                                       +
+---------------------------------------------------------------------------------------+
+        ${"Get started here: https://github.com/bernaferrari/GradleKotlinConverter".yellow()}        +
+---------------------------------------------------------------------------------------+
"""

if (!args.contains("skipintro")) {
    println(intro)
}

val operationMode = OperatingMode(args);

class OperatingMode(val commandLineArgs : Array<String>) {
    val fileFromArgs : File? by lazy {
        fileFromArgs(commandLineArgs)
    }
    val isRecursive : Boolean by lazy {
        isRecursiveMode(commandLineArgs, fileFromArgs)
    }
    val isDeleteInputFile : Boolean by lazy {
        isDeleteInputFile(commandLineArgs)
    }

    fun isClipboardMode() : Boolean {
        return commandLineArgs.isEmpty()
    }

    fun determineFiles(isRecursiveMode: Boolean = isRecursive, root : File? = fileFromArgs) : List<File> {
        if (root == null) {
            return listOf()
        }
        if (!isRecursiveMode) {
            return listOf(determineSingleFile(root))
        }
        return Files.walk(root.toPath()).filter {
            it.isRegularFile() &&  it.name.equals("build.gradle")
        }.map {
            it.toFile()
        }.toList()
    }

    private fun fileFromArgs(commandLineArgs : Array<String>) : File? {
        if (isClipboardMode()) {
            return null
        }
        val file = File(commandLineArgs.first())
        println("[${currentTimeFormatted()}] File/directory specified at command line: '$file'".green())
        if (!file.exists()) {
            fail("Didn't find a file in the path you specified.")
        }
        return file
    }

    private fun determineSingleFile(file : File) : File {
        var result = file
        if (result.isDirectory()) {
            result = File(file, "build.gradle")
            println("[${currentTimeFormatted()}] - File '$file' actually is a directory -> expanding to '$result'.")
        }
        return result
    }

    private fun isRecursiveMode(commandLineArgs : Array<String>, fileFromArgs : File?) : Boolean {
        if (isClipboardMode()) {
            return false
        }

        return fileFromArgs?.isDirectory()?: false && commandLineArgs.find { arg -> arg.equals("recursive", true) } != null
    }

    private fun isDeleteInputFile(commandLineArgs : Array<String>) : Boolean {
        if (isClipboardMode()) {
            return false
        }

        return commandLineArgs.find { arg -> arg.equals("deleteInputFile", true) } != null
    }
}

fun getClipboardContents(): String {

    print("[${currentTimeFormatted()}] - Trying to open clipboard.. ")

    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val contents = clipboard.getContents(null)
    val hasTransferableText = contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)

    val result = if (hasTransferableText) contents?.getTransferData(DataFlavor.stringFlavor) as? String ?: "" else ""

    println("Success!")
    return result
}

fun determineInputFileRaw(commandLineArgs: Array<String>): File? {

    if (commandLineArgs.isEmpty()) {
        return null
    }

    val file = File(args.first())
    println("File/directory specified at command line: '$file'")
    if (!file.exists()) {
        fail("Didn't find a file in the path you specified.")
    }

    return file
}

fun fail(cause: String) {
    println("[${currentTimeFormatted()}] $cause".red())
    exitProcess(1)
}

fun readFromFile(file: File): String {

    return file.readText()
}

// anything with ' ('1.0.0', 'kotlin-android', 'jitpack', etc)
// becomes
// anything with " ("1.0.0", "kotlin-android", "jitpack", etc)
// We do not replace '"45"' --> "\"45\"" becaues we cannot safely match start and end quote with regExp's
fun String.replaceApostrophes(): String = this.replace("'", "\"")

// def appcompat = "1.0.0"
// becomes
// val appcompat = "1.0.0"
fun String.replaceDefWithVal(): String = this.replace("(^|\\s)def ".toRegex()) { valReplacer ->
    // only convert when " def " or "def " (start of the line).
    // if a variable is named highdef, it won't be converted.
    valReplacer.value.replace("def", "val")
}


fun String.convertType(): String =
        when (this) {
            "byte" -> "Byte"
            "short" -> "Short"
            "int" -> "Int"
            "long" -> "Long"
            "float" -> "Float"
            "double" -> "Double"
            "char" -> "Char"
            "boolean" -> "Boolean"
            else -> this
        }

// final String<T> foo = "bar"
// becomes
// val foo: String<T> = "bar"
fun String.convertVariableDeclaration(): String {
    val varDeclExp = """(?:final\s+)?(\w+)(<.+>)? +(\w+)\s*=\s*(.+)""".toRegex()

    return this.replace(varDeclExp) {
        val (type, genericsType, id, value) = it.destructured
        if (type == "val") {
            it.value
        } else {
            "val $id: ${type.convertType()}${genericsType.orEmpty()} = $value"
        }
    }
}

// [appIcon: "@drawable/ic_launcher", appRoundIcon: "@null"]
// becomes
// mapOf(appIcon to "@drawable/ic_launcher", appRoundIcon to "@null")
// other example
// ['aKey': 'aValue', 'anotherKey': 'anotherValue']
// becomes
// mapOf("aKey" to "aValue", "anotherKey" to "anotherValue")
fun String.convertMapExpression(): String {
    val key = """(?:\w+|"[^"]*")"""
    val keyTrimmerRegEx = """"?([^"]*)"?""".toRegex()
    val value = """(?:[^,:\s\]]+|"[^"]*")"""
    val keyValueGroup = """\s*$key\s*:\s*$value\s*"""
    val mapRegExp = """\[($keyValueGroup(?:,$keyValueGroup)*)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val extractOneGroupRegExp = """^\s*($key)\s*:\s*($value)\s*(?:,(.*)|)$""".toRegex() // Matches key, value, the-rest after comma if any

    fun extractAllMatches(matchesInKotlinCode: MutableList<String>, remainingString: String) { // Extract the first key=value, and recurse on the postfix
        val innerMatch: MatchResult = extractOneGroupRegExp.find(remainingString) ?: return
        val innerGroups = innerMatch.groupValues
        val keyTrimmerMatch: MatchResult = keyTrimmerRegEx.find(innerGroups[1]) ?: return
        val trimmedKey = keyTrimmerMatch.groupValues
        matchesInKotlinCode += """"${trimmedKey[1]}" to ${innerGroups[2]}"""
        if (innerGroups[3].isNotEmpty()) {
            val withoutComma = innerGroups[3]//.substring(1)
            extractAllMatches(matchesInKotlinCode, withoutComma)
        }
    }

    return this.replace(mapRegExp) { lineMatch ->
        val matchesInKotlinCode = mutableListOf<String>()
        extractAllMatches(matchesInKotlinCode, lineMatch.groupValues[1])
        "mapOf(${matchesInKotlinCode.joinToString(", ")})"
    }
}

// Use new com.android.tools.build:gradle:4.1.0 syntax for manifestPlaceholders
// manifestPlaceholders = mapOf("appIcon" to "@drawable/ic_launcher")
// becomes
// manifestPlaceholders.putAll(mapOf("appIcon" to "@drawable/ic_launcher"))
fun String.convertManifestPlaceHoldersWithMap(): String {
    val regExp = """manifestPlaceholders = (mapOf\([^\)]*\))""".toRegex(RegexOption.DOT_MATCHES_ALL)
    return this.replace(regExp) {
        "manifestPlaceholders.putAll(${it.groupValues[1]})"
    }
}

// [1, 2]
// becomes
// listOf(1,2)
// but keep probablyMyArrayLookup[42]
fun String.convertArrayExpression(): String {
    val arrayExp = """\[([^\]]*?)\]""".toRegex(DOT_MATCHES_ALL)

    return this.replace(arrayExp) {
        if (it.groupValues[1].toIntOrNull() != null) {
            it.value // Its probably an array indexing, so keep original
        } else {
            "listOf(${it.groupValues[1]})"
        }
    }
}

fun String.convertVariantFilter(): String {
    val arrayExp = """variantFilter\s*\{\s*(\w+\s*->)""".toRegex(DOT_MATCHES_ALL)

    return this.replace(arrayExp) {
        "variantFilter { // ${it.groupValues[1]} - TODO Manually replace '${it.groupValues[1]}' variable with this, and setIgnore(true) with ignore = true\n"
    }
}


// apply plugin: "kotlin-android"
// becomes
// apply(plugin = "kotlin-android")
fun String.convertPlugins(): String {
    val pluginsExp = """apply plugin: (\S+)""".toRegex()

    return this.replace(pluginsExp) {
        val (pluginId) = it.destructured
        // it identifies the plugin id and rebuilds the line.
        "apply(plugin = $pluginId)"
    }
}

// apply from: "kotlin-android"
// becomes
// apply(from = "kotlin-android")
fun String.convertPluginsFrom(): String {
    val pluginsExp = """apply from: (\S+)""".toRegex()

    return this.replace(pluginsExp) {
        val (pluginId) = it.destructured
        "apply(from = $pluginId)"
    }
}

fun String.convertAndroidBuildConfigFunctions(): String {
    val outerExp = """(buildConfigField|resValue|flavorDimensions|exclude|java.srcDir)\s+(".*")""".toRegex()
    // packagingOptions > exclude
    // sourceSets > name("") > java.srcDir

    return this.replace(outerExp) {
        val groups = it.groupValues
        "${groups[1]}(${groups[2]})"
    }
}


// NEED TO RUN BEFORE [convertDependencies].
// compile ":epoxy-annotations"
// becomes
// implementation ":epoxy-annotations"
fun String.convertCompileToImplementation(): String {
    val outerExp = "(compile|testCompile)(?!O).*\".*\"".toRegex()

    return this.replace(outerExp) {
        if ("testCompile" in it.value) {
            it.value.replace("testCompile", "testImplementation")
        } else {
            it.value.replace("compile", "implementation")
        }
    }
}


// implementation ":epoxy-annotations"
// becomes
// implementation(":epoxy-annotations")
fun String.convertDependencies(): String {

    val testKeywords = "testImplementation|androidTestImplementation|debugImplementation|releaseImplementation|compileOnly|testCompileOnly|runtimeOnly|developmentOnly"
    val gradleKeywords = "\\s+($testKeywords|implementation|api|annotationProcessor|classpath|kaptTest|kaptAndroidTest|kapt|check|ksp|coreLibraryDesugaring|detektPlugins|lintPublish|lintCheck|cartridgeRuntime|cartridge[^\\S])".toRegex()


    // ignore cases like kapt { correctErrorTypes = true } and apply plugin: ('kotlin-kapt") but pass kapt("...")
    // ignore keyWord followed by a space and a { or a " and a )
    val validKeywords = "(?!$gradleKeywords\\s+(\\{|\\\"\\)|\\.))$gradleKeywords.*".toRegex()

    return this.replace(validKeywords) { substring ->
        // By pass sth like: implementation(":epoxy-annotations") { ... }
        if (substring.value.contains("""\)(\s*)\{""".toRegex())) return@replace substring.value

        // retrieve the comment [//this is a comment], if any
        val comment = "\\s*\\/\\/.*".toRegex().find(substring.value)?.value ?: ""

        // remove the comment from the string. It will be added again at the end.
        val processedSubstring = substring.value.replace(comment, "")

        // we want to know if it is a implementation, api, etc
        val gradleKeyword = gradleKeywords.find(processedSubstring)?.value

        // implementation ':epoxy-annotations' becomes 'epoxy-annotations'
        val isolated = processedSubstring.replaceFirst(gradleKeywords, "").trim()

        // can't be && for the kapt project(':epoxy-processor') scenario, where there is a ) on the last element.
        if (isolated != "" && (isolated.first() != '(' || isolated.last { it != ' ' } != ')')) {
            "$gradleKeyword($isolated)$comment"
        } else {
            "$gradleKeyword$isolated$comment"
        }
    }
}

// fileTree(dir: "libs", include: ["*.jar"])
// becomes
// fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))
fun String.convertFileTree(): String {
    val fileTreeString = """fileTree\(dir(\s*):(\s*)"libs"(\s*),(\s*)include(\s*):(\s*)\["\*.jar"\]\)""".toRegex()

    return this.replace(fileTreeString, """fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))""")
}


// signingConfig signingConfigs.release
// becomes
// signingConfig = signingConfigs.getByName("release")
fun String.convertSigningConfigBuildType(): String {
    val outerExp = "signingConfig.*signingConfigs.*".toRegex()

    return this.replace(outerExp) {
        // extracts release from signingConfig signingConfigs.release
        val release = it.value.replace("signingConfig.*signingConfigs.".toRegex(), "")
        "signingConfig = signingConfigs.getByName(\"$release\")"
    }
}


// buildTypes { release }
// becomes
// buildTypes { named("release") }
fun String.convertBuildTypes(): String = this.convertNestedTypes("buildTypes", "named")

// productFlavors { myName }
// becomes
// productFlavors { create("myName") }
fun String.convertProductFlavors(): String = this.convertNestedTypes("productFlavors", "create")


// sourceSets { test }
// becomes
// sourceSets { named("test") }
fun String.convertSourceSets(): String = this.convertNestedTypes("sourceSets", "named", setOf("resources"))


// signingConfigs { release }
// becomes
// signingConfigs { register("release") }
fun String.convertSigningConfigs(): String = this.convertNestedTypes("signingConfigs", "register")

fun String.convertNestedTypes(buildTypes: String, named: String, excludeKeywords: Set<String> = emptySet()): String {
    return this.getExpressionBlock("$buildTypes\\s*\\{".toRegex()) { substring ->
        substring.replace("\\S*\\s(?=\\{)".toRegex()) {
            val valueWithoutWhitespace = it.value.trim()
            if (valueWithoutWhitespace in excludeKeywords) {
                valueWithoutWhitespace
            } else {
                "$named(\"$valueWithoutWhitespace\")"
            }
        }
    }
}


fun String.getExpressionBlock(
        expression: Regex,
        modifyResult: ((String) -> (String))
): String {

    val stringSize = this.count()

    return expression.findAll(this)
            .toList()
            .foldRight(this) { matchResult, accString ->

                var rangeStart = matchResult.range.last
                var rangeEnd = stringSize
                var count = 0

                if (DEBUG) {
                    println("[DP] - range: ${matchResult.range} value: ${matchResult.value}")
                }

                for (item in rangeStart..stringSize) {
                    if (this[item] == '{') count += 1 else if (this[item] == '}') count -= 1
                    if (count == 0) {
                        rangeEnd = item
                        break
                    }
                }

                if (DEBUG) {
                    println("[DP] reading this block:\n${this.substring(rangeStart, rangeEnd)}")
                }

                val convertedStr = modifyResult.invoke(this.substring(rangeStart, rangeEnd))

                if (DEBUG) {
                    println("[DP] outputing this block:\n${convertedStr}")
                }

                accString.replaceRange(rangeStart, rangeEnd, convertedStr)
            }
}


// maven { url "https://maven.fabric.io/public" }
// becomes
// maven("https://maven.fabric.io/public")
fun String.convertMaven(): String {

    val mavenExp = "maven\\s*\\{\\s*url\\s*(.*?)\\s*?}".toRegex()

    return this.replace(mavenExp) {
        it.value.replace("(= *uri *\\()|\\)|(url)|( )".toRegex(), "")
                .replace("{", "(")
                .replace("}", ")")
    }
}

var showWarningGroovyVariables = false

// compileSdkVersion 28
// becomes
// compileSdkVersion(28)
fun String.addParentheses(): String {

    val sdkExp = "(compileSdkVersion|minSdkVersion|targetSdkVersion|consumerProguardFiles)\\s*([^\\s]*)(.*)".toRegex() // include any word, as it may be a variable

    return this.replace(sdkExp) {
        val groups = it.groupValues
        if (groups.size > 3) {
            if (groups[2].toIntOrNull() == null) showWarningGroovyVariables = true
            "${groups[1]}(${groups[2]})${groups[3]}" // group 3 for preserving comments
        } else {
            it.value
        }
    }
}


// id "io.gitlab.arturbosch.detekt" version "1.0.0.RC8"
// becomes
// id("io.gitlab.arturbosch.detekt") version "1.0.0.RC8"
fun String.addParenthesisToId(): String {
    
    // this will only catch id "..." version ..., should skip id("...")
    // should get the id "..."
    val idExp = "(id)\\s*\"(.*?)\"".toRegex()

    return this.replace(idExp) {
        // remove the "id " before the real id
        val (id, value) = it.destructured
        """$id("$value")"""
    }
}


// versionCode 4
// becomes
// versionCode = 4
fun String.addEquals(): String {

    val compileSdk = "compileSdk"
    val signing = "keyAlias|keyPassword|storeFile|storePassword"
    val other = "multiDexEnabled|correctErrorTypes|javaMaxHeapSize|jumboMode|dimension|useSupportLibrary|kotlinCompilerExtensionVersion|isCoreLibraryDesugaringEnabled"
    val databinding = "dataBinding|viewBinding"
    val defaultConfig = "applicationId|minSdk|targetSdk|versionCode|versionName|testInstrumentationRunner|namespace"
    val negativeLookAhead = "(?!\\{)[^Version\\s]" // Don't want '{' as next word character

    val versionExp = """($compileSdk|$defaultConfig|$signing|$other|$databinding)\s*${negativeLookAhead}.*""".toRegex()

    return this.replace(versionExp) {
        val split = it.value.split(" ")

        // if there is more than one whitespace, the last().toIntOrNull() will find.
        if (split.lastOrNull { it.isNotBlank() } != null) {
            "${split[0]} = ${split.last()}"
        } else {
            it.value
        }
    }
}


// proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
// becomes
// setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
fun String.convertProguardFiles(): String {

    val proguardExp = "proguardFiles .*".toRegex()

    return this.replace(proguardExp) {
        val isolatedArgs = it.value.replace("proguardFiles\\s*".toRegex(), "")
        "setProguardFiles(listOf($isolatedArgs))"
    }
}


// ext.enableCrashlytics = false
// becomes
// extra.set("enableCrashlytics", false)
fun String.convertExtToExtra(): String {

    // get ext... but not ext { ... }
    val outerExp = """ext\.(\w+)\s*=\s*(.*)""".toRegex()

    return this.replace(outerExp) {
        val (name, value) = it.destructured

        "extra[\"$name\"] = $value"
    }
}


// sourceCompatibility = "1.8" or sourceCompatibility JavaVersion.VERSION_1_8
// becomes
// sourceCompatibility = JavaVersion.VERSION_1_8
fun String.convertJavaCompatibility(): String {

    val compatibilityExp = "(sourceCompatibility|targetCompatibility).*".toRegex()

    return this.replace(compatibilityExp) {
        val split = it.value.replace("\"]*".toRegex(), "").split(" ")

        if (split.lastOrNull() != null) {
            if ("JavaVersion" in split.last()) {
                "${split[0]} = ${split.last()}"
            } else {
                "${split[0]} = JavaVersion.VERSION_${split.last().replace(".", "_")}"
            }
        } else {
            it.value
        }
    }
}


// converts the clean task, which is very common to find
fun String.convertCleanTask(): String {

    val cleanExp = "task clean\\(type: Delete\\)\\s*\\{[\\s\\S]*}".toRegex()
    val registerClean = "tasks.register<Delete>(\"clean\").configure {\n" +
            "    delete(rootProject.buildDir)\n }"

    return this.replace(cleanExp, registerClean)
}


// androidExtensions { experimental = true }
// becomes
// androidExtensions { isExperimental = true }
fun String.convertInternalBlocks(): String {
    return this.addIsToStr("androidExtensions", "experimental")
            .addIsToStr("dataBinding", "enabled")
            .addIsToStr("lintOptions", "abortOnError")
            .addIsToStr("buildTypes", "debuggable")
            .addIsToStr("buildTypes", "minifyEnabled")
            .addIsToStr("buildTypes", "shrinkResources")
            .addIsToStr("", "transitive")
}

fun String.addIsToStr(blockTitle: String, transform: String): String {

    val extensionsExp = "$blockTitle\\s*\\{[\\s\\S]*\\}".toRegex()

    if (!extensionsExp.containsMatchIn(this)) return this

    val typesExp = "$transform.*".toRegex()

    return this.replace(typesExp) {

        val split = it.value.split(" ")

        if (DEBUG) {
            println("[AS] split:\n${split}")
        }

        // if there is more than one whitespace, the last().toIntOrNull() will find.
        if (split.lastOrNull { it.isNotBlank() } != null) {
            "is${split[0].capitalize()} = ${split.last()}"
        } else {
            it.value
        }
    }
}


// include ":app", ":diffutils"
// becomes
// include(":app", ":diffutils")
fun String.convertInclude(): String {

    val expressionBase = "\\s*((\".*\"\\s*,)\\s*)*(\".*\")".toRegex()
    val includeExp = "include$expressionBase".toRegex()

    return this.replace(includeExp) { includeBlock ->
        if(includeBlock.value.contains("include\"")) return@replace includeBlock.value // exclude: "include" to

        // avoid cases where some lines at the start/end are blank
        val multiLine = includeBlock.value.split('\n').count { it.isNotBlank() } > 1

        val isolated = expressionBase.find(includeBlock.value)?.value ?: ""
        if (multiLine) "include(\n${isolated.trim()}\n)" else "include(${isolated.trim()})"
        // Possible visual improvement: when using multiline, the first line should have the same
        // margin/spacement as the others.
    }
}


// configurations.classpath.exclude group: 'com.android.tools.external.lombok'
// becomes
// configurations.classpath {
//    exclude(group = "com.android.tools.external.lombok")
// }
fun String.convertExcludeClasspath(): String {

    val fullLineExp = ".*configurations\\.classpath\\.exclude.*group:.*".toRegex()

    if (DEBUG) {
        println("[CEC] - reading this line: " + fullLineExp.find(this)?.value)
    }

    // this will extract "com.android.tools.external.lombok" from the string.
    val innerExp = "\\\".*\\\"".toRegex()

    return this.replace(fullLineExp) { isolatedLine ->
        val isolatedStr = innerExp.find(isolatedLine.value)?.value ?: ""
        "configurations.classpath {\n" +
                "    exclude(group = $isolatedStr)\n" +
                "}"
    }
}

// exclude module: 'module-id'
// becomes
// exclude(module = "module-id")
fun String.convertExcludeModules(): String {
    val fullLineExp = """exclude module:\s*(\S+)""".toRegex()

    return this.replace(fullLineExp) {
        val (moduleId) = it.destructured
        "exclude(module = $moduleId)"
    }
}

// exclude group: 'group-id'
// becomes
// exclude(group = "group-id")
fun String.convertExcludeGroups(): String {
    val fullLineExp = """exclude\s+group:\s*(\S+)\s*,\s*module:\s*(\S+)|exclude\s+group:\s*(\S+)""".toRegex()

    return this.replace(fullLineExp) {
        val destructured = it.destructured
        val group1Id = destructured.component1()
        val group2Id = destructured.component2()
        val group3Id = destructured.component3()

        if (group2Id.isNotBlank()) {
            "exclude(group = $group1Id, module = $group2Id)"
        }else {
            "exclude(group = $group3Id)"
        }
    }
}

// classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
// becomes
// classpath(kotlin("gradle-plugin", version = "$kotlin_version"))
//
// implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
// becomes
// implementation(kotlin("stdlib"))
fun String.convertJetBrainsKotlin(): String {

    // if string is implementation("..."), this will extract only the ...
    val fullLineExp = "\"org.jetbrains.kotlin:kotlin-.*(?=\\))".toRegex()

    val removeExp = "(?!org.jetbrains.kotlin:kotlin)-.*".toRegex()

    var shouldImportKotlinCompiler = false

    val newText = this.replace(fullLineExp) { isolatedLine ->

        // drop first "-" and remove last "
        val substring = (removeExp.find(isolatedLine.value)?.value ?: "").drop(1).replace("\"", "")

        val splittedSubstring = substring.split(":")

        if ("stdlib" in substring) {
            shouldImportKotlinCompiler = true
            "kotlin(\"stdlib\")"
        } else if (splittedSubstring.size == 2) {
            "kotlin(\"${splittedSubstring[0]}\", version = \"${splittedSubstring[1]}\")"
        } else {
            "kotlin(\"${splittedSubstring[0]}\")"
        }
    }

    return newText
}


// apply(plugin = "com.trello.victor")
// becomes within plugin{}
// id("com.trello.victor")
fun String.convertPluginsIntoOneBlock(): String {

    // group plugin expressions. There can't be any space or tabs on the start of the line, else the regex will fail.
    // ok example:
    // apply(...)
    // apply(...)
    //
    // not ok example:
    // apply(...)
    //    apply(...)
    val fullLineExp = "(apply\\(plugin\\s*=\\s*\".*\"\\)[\\s\\S]){2,}".toRegex()

    val isolatedId = "\".*\"(?=\\))".toRegex()

    return this.replace(fullLineExp) { isolatedLine ->
        // this will fold the ids into a single string
        val plugins = isolatedId.findAll(isolatedLine.value)?.fold("") { acc, matchResult ->
            acc + "    id(${matchResult.value})\n"
        }
        "plugins {\n$plugins}\n"
    }
}

fun String.convertJavaPluginReference(): String {

    val pluginsRegex = """(?s)(plugins\s*\{.+)(id\(\"java\"\))(.+\})""".toRegex()
    val javaRegex = """id\(\"java\"\)""".toRegex()

    return this.replace(pluginsRegex) {
        javaRegex.replace(it.value, "java")
    }
}

fun String.fixDependenciesSectionBraces(): String {

    val dependenciesRegex = """(?s)dependencies\s*\{(.+)\}""".toRegex()

    return this.replace(dependenciesRegex) {
        """dependencies {
        |    ${it.destructured.component1().trim()}
        |}
        """.trimMargin()
    }
}

// tasks.abc.dependsOn(tasks.xyz)
// becomes
// tasks.abc.configure {
//     dependsOn(tasks.xyz)
// }
fun String.convertTaskDependencies(): String {

    val taskDependencyRegex = """tasks\.(\w+)\.dependsOn\(tasks\.(\w+)\)""".toRegex()

    return this.replace(taskDependencyRegex) {
        val destructured = it.destructured
        """
            |tasks.${destructured.component1()}.configure {
            |    dependsOn(tasks.${destructured.component2()})
            |}""".trimMargin()
    }
}

// from 'src/main/resources'
// becomes
// from("src/main/resources")
fun String.convertFrom(): String {

    val expressionBase = "\\s*((\".*\"\\s*,)\\s*)*(\".*\")".toRegex()
    val fromExp = "from$expressionBase".toRegex()

    return this.replace(fromExp) { fromBlock ->
        if(fromBlock.value.contains("from\"")) return@replace fromBlock.value // exclude: "from" to

        // avoid cases where some lines at the start/end are blank
        val multiLine = fromBlock.value.split('\n').count { it.isNotBlank() } > 1

        val isolated = expressionBase.find(fromBlock.value)?.value ?: ""
        if (multiLine) "from(\n${isolated.trim()}\n)" else "from(${isolated.trim()})"
        // Possible visual improvement: when using multiline, the first line should have the same
        // margin/spacing as the others.
    }
}

// into new File(project.buildDir, 'target_resources')
// becomes
// into(layout.buildDirectory.dir("target_resources"))
//
// srcDir new File(project.buildDir, 'target_resources')
// becomes
// srcDir(layout.buildDirectory.dir("target_resources"))
fun String.convertIntoSrcDir(): String {
    val genericNewFileExp = """(into|srcDir)\s+new\s+File\s*\(\s*(project\.)?buildDir\s*,\s*['"](.+)['"]\s*\)""".toRegex()

    return this.replace(genericNewFileExp) { matchResult ->
        val (command, projectPrefix, targetPath) = matchResult.destructured
        "$command(layout.buildDirectory.dir(\"$targetPath\"))"
    }
}

// expand(version: project.version)
// becomes
// expand(mapOf("version" to project.version))
fun String.convertExpand(): String {
    val expandExp = """expand\((\w+):\s*([^,\)]+)(?:,\s*(\w+):\s*([^,\)]+))*\)""".toRegex()

    return this.replace(expandExp) { matchResult ->
        val content = matchResult.value.substring(7, matchResult.value.length - 1) // Entferne "expand(" und ")"

        // Teile den Inhalt in einzelne Paare auf und formatiere sie um
        val pairs = content.split(",")
            .map { it.trim() }.joinToString(", ") {
                val keyValue = it.split(":")
                if (keyValue.size == 2) {
                    "\"${keyValue[0].trim()}\" to ${keyValue[1].trim()}"
                } else {
                    it
                }
            }

        "expand(mapOf($pairs))"
    }
}

// task custom_task(type: Copy)
// becomes
// tasks.register<Copy>("custom_task")
fun String.convertCopyTask(): String {
    val copyExp = Regex("""task\s+(\w+)\s*\(type:\s*Copy\)""")
    return this.replace(copyExp) { matchResult ->
        "tasks.register<Copy>(\"${matchResult.groupValues[1]}\")"
    }
}

// tasks.withType(Copy)
// becomes
// tasks.withType<Copy>
fun String.convertTasksWithType(): String {
    val copyExp = Regex("""tasks.withType\s+\((.*)\)""")
    return this.replace(copyExp) { matchResult ->
        """tasks.withType<${matchResult.groupValues[1]}>"""
    }
}

// tasks.compileJava.dependsOn custom_task
// becomes
// tasks.named("compileJava") { dependsOn("custom_task") }
// and if it's compileJava, also add:
// tasks.getByName("sourcesJar") { dependsOn("custom_task") }
// tasks.getByName("processResources") { dependsOn("custom_task") }
fun String.convertDynamicTaskDependencies(): String {
    val taskDependencyExp = """tasks\.(\w+)\.dependsOn\s+(\w+)""".toRegex()

    return this.replace(taskDependencyExp) { matchResult ->
        val (taskName, dependsOnTask) = matchResult.destructured
        val convertedLine = """
            |tasks.named("$taskName") {
            |    dependsOn("$dependsOnTask")
            |}""".trimMargin()

        // add additional dependencies if the dependency was compileJava
        if (taskName == "compileJava") {
            """$convertedLine
               |tasks.named("sourcesJar") {
               |    dependsOn("$dependsOnTask")
               |}
               |tasks.named("processResources") {
               |    dependsOn("$dependsOnTask")
               |}""".trimMargin()
        } else {
            convertedLine
        }
    }
}

// rename { String varName -> ... }
// becomes
// rename { varName: String -> ... }
fun String.convertRenameNotation(): String {
    val renameExp = """rename\s*\{\s*String\s+(\w+)\s*->""".toRegex()

    return this.replace(renameExp) { matchResult ->
        val variableName = matchResult.groupValues[1]
        "rename { $variableName: String ->"
    }
}


// testImplementation(group: "junit", name: "junit", version: "4.12")
// becomes
// testImplementation(group = "junit", name = "junit", version = "4.12")
fun String.replaceColonWithEquals(): String {

    // this get "group:"
    val expression = "\\w*:\\s*\".*?\"".toRegex()

    return this.replace(expression) {
        it.value.replace(":", " =")
    }
}

// coreLibraryDesugaringEnabled = true
// becomes
// isCoreLibraryDesugaringEnabled = true
fun String.replaceCoreLibraryDesugaringEnabled(): String = this.replace(
    oldValue = "coreLibraryDesugaringEnabled", newValue = "isCoreLibraryDesugaringEnabled"
)

// compose true
// dataBinding false
// becomes
// compose = true
// dataBinding = false
fun String.convertBuildFeatures(): String {
    val buildFeatures = "(dataBinding|viewBinding|aidl|buildConfig|prefab|renderScript|resValues|shaders|compose)"
    val state = "(false|true)"

    return this.replace("$buildFeatures\\s$state".toRegex()) { result ->
    result.value.replace(" ", " = ")
    }
}

fun String.applyConversions() : String {
    return this
            .replaceApostrophes()
            .replaceDefWithVal()
            .convertMapExpression() // Run before array / regex adapted to allow quotes in keys and values
            .convertFileTree()
            .convertArrayExpression()
            .convertManifestPlaceHoldersWithMap() // Run after convertMapExpression
            .convertVariableDeclaration()
            .convertPlugins()
            .convertPluginsIntoOneBlock()
            .convertPluginsFrom()
            .convertVariantFilter()
            .convertAndroidBuildConfigFunctions()
            .convertCompileToImplementation()
            .replaceCoreLibraryDesugaringEnabled()
            .convertDependencies() // custom keywords, regex fixed: space around keyword required
            .convertMaven()
            .addParentheses()
            .addEquals()
            .convertJavaCompatibility()
            .convertCleanTask()
            .convertProguardFiles()
            .convertInternalBlocks()
            .convertInclude()
            .convertBuildTypes()
            .convertProductFlavors()
            .convertSourceSets()
            .convertSigningConfigs()
            .convertExcludeClasspath()
            .convertExcludeModules() // fixed: optional spaces after colon
            .convertExcludeGroups() // enhanced
            .convertJetBrainsKotlin()
            .convertSigningConfigBuildType()
            .convertExtToExtra()
            .addParenthesisToId()
            .replaceColonWithEquals()
            .convertBuildFeatures()
            .convertJavaPluginReference() // custom
            .fixDependenciesSectionBraces() // custom
            .convertTaskDependencies() // custom
            .convertFrom() // custom
            .convertRenameNotation() // custom
            .convertExpand() // custom
            .convertIntoSrcDir() // custom
            .convertCopyTask() // custom
            .convertTasksWithType() // custom
            .convertDynamicTaskDependencies() // custom
}

fun writeToClipboard(content : String) {
    val selection = StringSelection(content)
    val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()

    print("[${currentTimeFormatted()}] --- Saving to clipboard.. ")

    clipboard.setContents(selection, selection)
}


fun writeToFile(file : File, content : String) {
    // if build.gradle -> build.gradle.kts
    // if build.gradle.kts -> build.gradle.kts (override)
    val fileIsAlreadyKts = file.path.takeLast(4) == ".kts"

    if (fileIsAlreadyKts) {
        println("\n### ### ### Warning! The script will overwrite ${file.path}, since it ends with \".kts\"".red() +
                "\n### ### ### Gradle might get crazy and all red, so you might want to \"gradle build\"\n".red())
    }

    val newFilePath = if (fileIsAlreadyKts) file.path else "${file.path}.kts"

    val newFile = File(newFilePath)
    newFile.createNewFile()
    newFile.writeText(content)
    println("[${currentTimeFormatted()}] - Successfully converted '$file' to '$newFilePath'".green())
}


println("[${currentTimeFormatted()}] -- Starting conversion.. ")

if (operationMode.isClipboardMode()){
    // Clipboard
    System.setProperty("java.awt.headless", "false")
    writeToClipboard(getClipboardContents().applyConversions())
}else{
    operationMode.determineFiles().forEach { file ->
        println("[${currentTimeFormatted()}] - Processing file '$file'.")
        val content = readFromFile(file)
        writeToFile(file, content.applyConversions())
        if (operationMode.isDeleteInputFile) {
            file.delete().apply {
                if (this) {
                    println("[${currentTimeFormatted()}] - Deleted input file '$file'.".green())
                } else {
                    println("[${currentTimeFormatted()}] - Failed to delete input file '$file'.".yellow())
                }
            }
        }
    }
}

println("Success!".green())



if (showWarningGroovyVariables) {
    println("\nWarning: We detected non-integer values for compileSdkVersion | minSdkVersion | targetSdkVersion\n- Groovy ext-variables are not supported, see buildSrc instead: https://proandroiddev.com/converting-your-android-gradle-scripts-to-kotlin-1172f1069880")
}

println("""
Info on manual work:
  If you have custom flavor implementation dependencies, use quotes like "myCustomFlavorImplementation"("a.lib")
  --- see https://github.com/jnizet/gradle-kotlin-dsl-migration-guide/blob/master/README.adoc#custom-configurations""")


println("\n\n          Thanks for using this script!\n")

exitProcess(0)
