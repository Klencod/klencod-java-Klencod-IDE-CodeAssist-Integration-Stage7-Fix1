package com.flutteride.codweva.editor

import java.util.Locale

/**
 * LanguageRules — قواعد كل لغة للتلوين.
 *
 * v1.0 — 14 لغة مدعومة:
 *   Dart, Java, Kotlin, TypeScript, JavaScript,
 *   XML, HTML, CSS, YAML, Shell, Groovy,
 *   JSON, Markdown, SQL
 */
class LanguageRules private constructor(
    val name: String,
    val keywords: Set<String>,
    val types: Set<String>,
    val constants: Set<String>,
    val supportsHashComment: Boolean = false,
    val supportsBacktick: Boolean = false,
    val supportsAnnotations: Boolean = false,
    val isMarkupLanguage: Boolean = false
) {

    companion object {

        private val cache = hashMapOf<String, LanguageRules>()

        @JvmStatic
        fun forLanguage(language: String?): LanguageRules {
            val key = language?.lowercase(Locale.US) ?: "text"
            cache[key]?.let { return it }

            val rules = when (key) {
                "dart" -> createDart()
                "java" -> createJava()
                "kotlin", "kt", "kts" -> createKotlin()
                "typescript", "ts" -> createTypeScript()
                "javascript", "js", "mjs", "cjs" -> createJavaScript()
                "xml" -> createXml()
                "html", "htm" -> createHtml()
                "css", "scss", "sass", "less" -> createCss()
                "yaml", "yml" -> createYaml()
                "shell", "sh", "bash", "zsh", "fish" -> createShell()
                "groovy", "gradle", "gvy" -> createGroovy()
                "json" -> createJson()
                "markdown", "md" -> createMarkdown()
                "sql" -> createSql()
                else -> createPlain()
            }

            cache[key] = rules
            return rules
        }

        // ============================================================
        // DART
        // ============================================================
        private fun createDart(): LanguageRules {
            val keywords = setOf(
                "abstract", "as", "assert", "async", "await",
                "break", "case", "catch", "class", "const",
                "continue", "covariant", "default", "deferred",
                "do", "dynamic", "else", "enum", "export",
                "extends", "extension", "external", "factory",
                "false", "final", "finally", "for", "get",
                "hide", "if", "implements", "import", "in",
                "interface", "is", "late", "library", "mixin",
                "new", "null", "on", "operator", "part",
                "required", "rethrow", "return", "set", "show",
                "static", "super", "switch", "sync", "this",
                "throw", "true", "try", "typedef", "var",
                "void", "when", "while", "with", "yield"
            )
            val types = setOf(
                "int", "double", "num", "String", "bool",
                "List", "Set", "Map", "Iterable", "Iterator",
                "Future", "Stream", "StreamController", "StreamSubscription",
                "Duration", "DateTime", "Object", "Null", "Function",
                "Widget", "StatelessWidget", "StatefulWidget", "State",
                "BuildContext", "Key", "GlobalKey", "ValueKey", "UniqueKey",
                "MaterialApp", "CupertinoApp", "Scaffold", "AppBar",
                "ThemeData", "Theme", "TextStyle", "TextSpan", "Color", "Colors",
                "IconData", "Icons", "Image", "ImageProvider", "AssetImage",
                "NetworkImage", "FileImage", "MemoryImage",
                "Border", "BorderRadius", "BoxDecoration", "BoxShadow", "BoxFit",
                "EdgeInsets", "Alignment", "Axis", "MainAxisAlignment",
                "CrossAxisAlignment", "MainAxisSize", "TextAlign", "TextDirection",
                "FontWeight", "FontStyle", "TextOverflow", "TextBaseline",
                "TextDecoration", "PaintingStyle", "Path", "Canvas", "Paint",
                "Offset", "Size", "Rect", "Radius", "CustomPainter",
                "CustomPaint", "AnimationController", "Animation", "Tween",
                "Curve", "Curves", "TweenAnimationBuilder",
                "ChangeNotifier", "ValueNotifier", "ValueListenable",
                "Listenable", "Route", "MaterialPageRoute", "Navigator",
                "Router", "MediaQuery", "MediaQueryData",
                "InheritedWidget", "InheritedModel", "FocusNode",
                "ScrollController", "TabController", "PageController",
                "TextEditingController", "FutureBuilder", "StreamBuilder",
                "ConnectionState", "AsyncSnapshot", "TextInputType",
                "TextInputAction", "TextInputFormatter", "InputDecoration",
                "ThemeMode", "Brightness", "Orientation", "Locale",
                "AppLifecycleState", "MaterialColor", "MaterialAccentColor",
                "ColorScheme", "TextTheme", "IconThemeData",
                "ButtonThemeData", "InputDecorationTheme",
                "SafeArea", "SliverList", "SliverGrid", "SliverAppBar",
                "CustomScrollView", "PageStorageKey", "WidgetState"
            )
            val constants = setOf(
                "true", "false", "null",
                "Colors", "Icons", "Curves", "Duration", "DateTime"
            )
            return LanguageRules(
                name = "dart",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = true,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // JAVA
        // ============================================================
        private fun createJava(): LanguageRules {
            val keywords = setOf(
                "abstract", "assert", "boolean", "break", "byte",
                "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else",
                "enum", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import",
                "instanceof", "int", "interface", "long", "native",
                "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super",
                "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while",
                "true", "false", "null", "var", "record",
                "yield", "sealed", "permits", "non-sealed"
            )
            val types = setOf(
                "String", "Integer", "Long", "Double", "Float",
                "Boolean", "Character", "Byte", "Short",
                "Object", "Number", "List", "ArrayList", "LinkedList",
                "Map", "HashMap", "TreeMap", "LinkedHashMap",
                "Set", "HashSet", "TreeSet", "LinkedHashSet",
                "Queue", "Deque", "Stack", "PriorityQueue",
                "Collection", "Collections", "Arrays", "Iterator",
                "Iterable", "Comparable", "Comparator",
                "Exception", "RuntimeException", "Error", "Throwable",
                "NullPointerException", "IllegalArgumentException",
                "IllegalStateException", "ClassCastException",
                "IndexOutOfBoundsException",
                "File", "FileInputStream", "FileOutputStream",
                "BufferedReader", "BufferedWriter", "InputStream",
                "OutputStream", "Reader", "Writer",
                "Thread", "Runnable", "Callable", "Future",
                "Executor", "ExecutorService", "Optional", "Stream",
                "Math", "System", "Objects", "StringBuilder",
                "StringBuffer", "Pattern", "Matcher", "Regex"
            )
            val constants = setOf("true", "false", "null")
            return LanguageRules(
                name = "java",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = true,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // KOTLIN
        // ============================================================
        private fun createKotlin(): LanguageRules {
            val keywords = setOf(
                "abstract", "actual", "annotation", "as", "break",
                "by", "catch", "class", "companion", "const",
                "constructor", "continue", "crossinline", "data",
                "do", "dynamic", "else", "enum", "expect",
                "external", "field", "final", "finally", "for",
                "fun", "get", "if", "import", "in", "infix",
                "init", "inline", "inner", "interface", "internal",
                "is", "lateinit", "noinline", "object", "open",
                "operator", "out", "override", "package", "private",
                "protected", "public", "reified", "return", "sealed",
                "set", "super", "suspend", "tailrec", "this",
                "throw", "try", "typealias", "val", "var", "vararg",
                "when", "where", "while", "true", "false", "null"
            )
            val types = setOf(
                "String", "Int", "Long", "Double", "Float", "Boolean",
                "Char", "Byte", "Short", "Any", "Unit", "Nothing",
                "List", "MutableList", "ArrayList", "Map", "MutableMap",
                "HashMap", "LinkedHashMap", "Set", "MutableSet", "HashSet",
                "Pair", "Triple", "Array", "IntArray", "Sequence",
                "Iterable", "Iterator", "Range", "IntRange",
                "Throwable", "Exception", "RuntimeException", "Error",
                "IllegalArgumentException", "IllegalStateException",
                "NullPointerException", "File", "StringBuilder", "Regex",
                "Thread", "Runnable", "Comparable", "Comparator",
                "Function", "Supplier", "Consumer", "Predicate",
                "Result", "Lazy", "Deferred", "Job", "Flow",
                "CoroutineScope", "Dispatchers"
            )
            val constants = setOf("true", "false", "null")
            return LanguageRules(
                name = "kotlin",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = true,
                supportsAnnotations = true,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // TYPESCRIPT
        // ============================================================
        private fun createTypeScript(): LanguageRules {
            val keywords = setOf(
                "abstract", "any", "as", "asserts", "async", "await",
                "bigint", "boolean", "break", "case", "catch",
                "class", "const", "constructor", "continue", "debugger",
                "declare", "default", "delete", "do", "else",
                "enum", "export", "extends", "false", "finally",
                "for", "from", "function", "get", "if", "implements",
                "import", "in", "instanceof", "interface", "is",
                "keyof", "let", "module", "namespace", "never",
                "new", "null", "number", "object", "of", "package",
                "private", "protected", "public", "readonly",
                "return", "set", "static", "string", "super",
                "switch", "symbol", "this", "throw", "true",
                "try", "type", "typeof", "undefined", "unique",
                "unknown", "var", "void", "while", "with", "yield"
            )
            val types = setOf(
                "String", "Number", "Boolean", "Symbol", "BigInt",
                "Object", "Function", "Array", "ReadonlyArray",
                "Map", "Set", "WeakMap", "WeakSet", "Promise",
                "Date", "RegExp", "Error", "TypeError",
                "ReferenceError", "SyntaxError", "RangeError",
                "JSON", "Math", "Infinity", "NaN",
                "Partial", "Required", "Readonly", "Pick", "Omit",
                "Record", "Exclude", "Extract", "NonNullable",
                "ReturnType", "Parameters", "Awaited",
                "React", "Component", "ReactNode", "FC"
            )
            val constants = setOf("true", "false", "null", "undefined", "NaN", "Infinity")
            return LanguageRules(
                name = "typescript",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = true,
                supportsAnnotations = true,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // JAVASCRIPT
        // ============================================================
        private fun createJavaScript(): LanguageRules {
            val keywords = setOf(
                "async", "await", "break", "case", "catch",
                "class", "const", "continue", "debugger", "default",
                "delete", "do", "else", "export", "extends",
                "false", "finally", "for", "function", "get",
                "if", "import", "in", "instanceof", "let",
                "new", "null", "of", "return", "set", "static",
                "super", "switch", "this", "throw", "true",
                "try", "typeof", "undefined", "var", "void",
                "while", "with", "yield"
            )
            val types = setOf(
                "String", "Number", "Boolean", "Symbol", "BigInt",
                "Object", "Function", "Array", "Map", "Set",
                "WeakMap", "WeakSet", "Promise", "Date", "RegExp",
                "Error", "TypeError", "ReferenceError", "SyntaxError",
                "RangeError", "JSON", "Math", "Infinity", "NaN",
                "document", "window", "console", "localStorage", "sessionStorage"
            )
            val constants = setOf("true", "false", "null", "undefined", "NaN", "Infinity")
            return LanguageRules(
                name = "javascript",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = true,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // XML
        // ============================================================
        private fun createXml(): LanguageRules {
            val keywords = setOf(
                "xml", "manifest", "application", "activity", "service",
                "receiver", "provider", "intent-filter", "action",
                "category", "data", "meta-data", "uses-permission",
                "uses-feature", "permission", "uses-sdk"
            )
            val types = setOf(
                "resources", "string", "color", "dimen", "style",
                "bool", "integer", "item", "attr", "declare-styleable",
                "LinearLayout", "RelativeLayout", "FrameLayout",
                "ConstraintLayout", "TextView", "EditText", "Button",
                "ImageView", "RecyclerView", "ScrollView", "CardView",
                "Toolbar", "ViewPager2", "SwipeRefreshLayout",
                "FloatingActionButton", "Switch", "CheckBox",
                "ProgressBar", "include", "merge", "fragment"
            )
            val constants = setOf(
                "match_parent", "wrap_content", "vertical", "horizontal",
                "visible", "invisible", "gone", "center", "start", "end",
                "center_horizontal", "center_vertical"
            )
            return LanguageRules(
                name = "xml",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = true
            )
        }

        // ============================================================
        // HTML
        // ============================================================
        private fun createHtml(): LanguageRules {
            val keywords = setOf(
                "html", "head", "body", "title", "meta", "link",
                "script", "style", "base", "header", "footer",
                "nav", "main", "aside", "section", "article",
                "figure", "figcaption", "details", "summary",
                "mark", "time", "h1", "h2", "h3", "h4", "h5", "h6",
                "p", "span", "div", "br", "hr", "strong", "em",
                "b", "i", "u", "small", "sub", "sup", "blockquote",
                "code", "pre", "q", "abbr", "a", "img", "picture",
                "video", "audio", "source", "iframe", "canvas", "svg",
                "ul", "ol", "li", "dl", "dt", "dd", "table",
                "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                "form", "input", "label", "textarea", "select",
                "option", "optgroup", "button", "fieldset", "legend",
                "datalist"
            )
            val types = setOf(
                "class", "id", "style", "href", "src", "alt", "title",
                "width", "height", "placeholder", "value", "disabled",
                "required", "readonly", "lang", "dir", "type", "name",
                "method", "action", "target", "rel", "content", "charset"
            )
            val constants = emptySet<String>()
            return LanguageRules(
                name = "html",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = true
            )
        }

        // ============================================================
        // CSS
        // ============================================================
        private fun createCss(): LanguageRules {
            val keywords = setOf(
                "display", "position", "top", "right", "bottom", "left",
                "width", "height", "max-width", "max-height",
                "min-width", "min-height", "margin", "padding",
                "box-sizing", "overflow", "z-index", "float", "clear",
                "flex", "flex-direction", "justify-content",
                "align-items", "gap", "grid", "color", "background",
                "font", "font-family", "font-size", "font-weight",
                "line-height", "text-align", "text-decoration",
                "border", "border-radius", "box-shadow", "opacity",
                "transform", "transition", "animation"
            )
            val types = setOf(
                "px", "em", "rem", "vh", "vw", "vmin", "vmax",
                "%", "auto", "inherit", "initial", "unset",
                "block", "inline", "inline-block", "flex", "grid",
                "absolute", "relative", "fixed", "sticky",
                "solid", "dashed", "dotted", "double"
            )
            val constants = setOf(
                "center", "start", "end", "space-between", "space-around",
                "space-evenly", "none", "bold", "normal"
            )
            return LanguageRules(
                name = "css",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // YAML
        // ============================================================
        private fun createYaml(): LanguageRules {
            val keywords = setOf(
                "name", "on", "jobs", "steps", "runs-on", "uses",
                "with", "run", "env", "if", "needs", "outputs",
                "strategy", "matrix", "permissions", "concurrency",
                "timeout-minutes", "container", "services"
            )
            val types = setOf(
                "ubuntu-latest", "ubuntu-22.04", "ubuntu-24.04",
                "windows-latest", "macos-latest",
                "actions/checkout", "actions/setup-java",
                "actions/setup-node", "actions/setup-python",
                "actions/cache", "actions/upload-artifact",
                "actions/download-artifact", "gradle/actions"
            )
            val constants = setOf("true", "false", "null")
            return LanguageRules(
                name = "yaml",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = true,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // SHELL
        // ============================================================
        private fun createShell(): LanguageRules {
            val keywords = setOf(
                "if", "then", "else", "elif", "fi", "for", "while",
                "until", "do", "done", "case", "esac", "function",
                "return", "exit", "break", "continue", "local",
                "export", "readonly", "declare", "unset", "shift",
                "eval", "exec", "source", "alias", "trap",
                "set", "test", "echo", "printf", "read",
                "ls", "cd", "pwd", "mkdir", "rm", "cp", "mv",
                "touch", "cat", "head", "tail", "grep", "sed",
                "awk", "cut", "sort", "uniq", "wc", "find",
                "xargs", "chmod", "chown", "basename", "dirname",
                "realpath", "tar", "curl", "wget", "ssh", "scp",
                "ps", "kill", "sleep", "date", "env", "which"
            )
            val types = setOf(
                "-e", "-u", "-x", "-o", "-r", "-w", "-f", "-d",
                "-z", "-n", "-eq", "-ne", "-gt", "-lt", "-ge", "-le"
            )
            val constants = setOf("true", "false")
            return LanguageRules(
                name = "shell",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = true,
                supportsBacktick = true,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // GROOVY (Gradle)
        // ============================================================
        private fun createGroovy(): LanguageRules {
            val keywords = setOf(
                "plugins", "dependencies", "repositories", "allprojects",
                "subprojects", "buildscript", "android", "defaultConfig",
                "buildTypes", "compileOptions", "kotlinOptions",
                "sourceSets", "signingConfigs", "flavorDimensions",
                "productFlavors", "packagingOptions", "lint",
                "compileSdk", "minSdk", "targetSdk", "versionCode",
                "versionName", "applicationId", "namespace",
                "implementation", "api", "compileOnly", "runtimeOnly",
                "testImplementation", "androidTestImplementation",
                "debugImplementation", "releaseImplementation",
                "classpath", "fileTree", "def", "if", "else", "for",
                "while", "switch", "try", "catch", "finally", "return",
                "class", "interface", "enum", "trait", "extends",
                "implements", "new", "this", "super", "true", "false",
                "null", "task", "tasks", "project", "ext"
            )
            val types = setOf(
                "String", "int", "boolean", "long", "double", "float",
                "Object", "List", "Map", "Set", "File", "URL",
                "AndroidConfig", "BuildType", "ProductFlavor",
                "SigningConfig", "JavaVersion", "Wrapper"
            )
            val constants = setOf("true", "false", "null")
            return LanguageRules(
                name = "groovy",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = true,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // JSON
        // ============================================================
        private fun createJson(): LanguageRules {
            val keywords = setOf("true", "false", "null")
            val types = emptySet<String>()
            val constants = setOf("true", "false", "null")
            return LanguageRules(
                name = "json",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // MARKDOWN
        // ============================================================
        private fun createMarkdown(): LanguageRules {
            val keywords = setOf(
                "#", "##", "###", "####", "#####", "######",
                "**", "__", "*", "_", "~~", "`", "```",
                ">", "-", "+", "1.", "2.", "3."
            )
            return LanguageRules(
                name = "markdown",
                keywords = keywords,
                types = emptySet(),
                constants = emptySet(),
                supportsHashComment = false,
                supportsBacktick = true,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // SQL
        // ============================================================
        private fun createSql(): LanguageRules {
            val keywords = setOf(
                "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES",
                "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP",
                "ALTER", "INDEX", "VIEW", "JOIN", "INNER", "LEFT",
                "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY",
                "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION",
                "ALL", "DISTINCT", "AS", "AND", "OR", "NOT",
                "NULL", "IS", "IN", "BETWEEN", "LIKE", "EXISTS",
                "CASE", "WHEN", "THEN", "ELSE", "END", "PRIMARY",
                "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK",
                "DEFAULT", "AUTO_INCREMENT", "SERIAL", "TRIGGER",
                "FUNCTION", "PROCEDURE", "BEGIN", "COMMIT", "ROLLBACK",
                "select", "from", "where", "insert", "into", "values",
                "update", "set", "delete", "create", "table", "drop",
                "alter", "index", "view", "join", "inner", "left",
                "right", "full", "outer", "on", "group", "by",
                "order", "having", "limit", "offset", "union",
                "all", "distinct", "as", "and", "or", "not"
            )
            val types = setOf(
                "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
                "DECIMAL", "NUMERIC", "FLOAT", "REAL", "DOUBLE",
                "CHAR", "VARCHAR", "TEXT", "BLOB", "DATE", "TIME",
                "DATETIME", "TIMESTAMP", "BOOLEAN", "JSON"
            )
            val constants = setOf("TRUE", "FALSE", "NULL", "true", "false", "null")
            return LanguageRules(
                name = "sql",
                keywords = keywords,
                types = types,
                constants = constants,
                supportsHashComment = true,
                supportsBacktick = true,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }

        // ============================================================
        // PLAIN (افتراضي)
        // ============================================================
        private fun createPlain(): LanguageRules {
            return LanguageRules(
                name = "text",
                keywords = emptySet(),
                types = emptySet(),
                constants = emptySet(),
                supportsHashComment = false,
                supportsBacktick = false,
                supportsAnnotations = false,
                isMarkupLanguage = false
            )
        }
    }
}