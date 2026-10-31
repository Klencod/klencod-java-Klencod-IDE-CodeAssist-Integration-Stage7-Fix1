package com.flutteride.codweva

import java.util.Locale
import java.util.regex.Pattern

/**
 * CompletionContext — محلل سياقي يحدد ما يجب اقتراحه بناءً على موضع المؤشر.
 */
object CompletionContext {

    private const val LOOKBACK_LIMIT = 800

    private class WidgetRule(val widgetName: String, val properties: Array<String>)

    private val WIDGET_RULES: List<WidgetRule> = listOf(
        WidgetRule("Column", arrayOf(
            "children", "mainAxisAlignment", "crossAxisAlignment",
            "mainAxisSize", "textDirection", "verticalDirection"
        )),
        WidgetRule("Row", arrayOf(
            "children", "mainAxisAlignment", "crossAxisAlignment",
            "mainAxisSize", "textDirection", "verticalDirection"
        )),
        WidgetRule("Container", arrayOf(
            "child", "padding", "margin", "color", "width", "height",
            "alignment", "decoration", "constraints", "transform", "clipBehavior"
        )),
        WidgetRule("Text", arrayOf(
            "style", "textAlign", "maxLines", "overflow", "softWrap",
            "textDirection", "textScaleFactor"
        )),
        WidgetRule("Scaffold", arrayOf(
            "appBar", "body", "floatingActionButton", "drawer", "endDrawer",
            "bottomNavigationBar", "bottomSheet", "backgroundColor",
            "resizeToAvoidBottomInset", "extendBody"
        )),
        WidgetRule("AppBar", arrayOf(
            "title", "actions", "leading", "centerTitle", "elevation",
            "toolbarHeight", "flexibleSpace", "bottom", "backgroundColor"
        )),
        WidgetRule("ListView", arrayOf(
            "children", "scrollDirection", "padding", "physics",
            "shrinkWrap", "reverse", "controller", "primary"
        )),
        WidgetRule("Stack", arrayOf(
            "children", "alignment", "fit", "clipBehavior"
        ))
    )

    private class PropertyValuesRule(val property: String, val values: Array<String>)

    private val PROPERTY_RULES: List<PropertyValuesRule> = listOf(
        PropertyValuesRule("mainAxisAlignment", arrayOf(
            "MainAxisAlignment.start", "MainAxisAlignment.center",
            "MainAxisAlignment.end", "MainAxisAlignment.spaceBetween",
            "MainAxisAlignment.spaceAround", "MainAxisAlignment.spaceEvenly"
        )),
        PropertyValuesRule("crossAxisAlignment", arrayOf(
            "CrossAxisAlignment.start", "CrossAxisAlignment.center",
            "CrossAxisAlignment.end", "CrossAxisAlignment.stretch",
            "CrossAxisAlignment.baseline"
        )),
        PropertyValuesRule("mainAxisSize", arrayOf(
            "MainAxisSize.min", "MainAxisSize.max"
        )),
        PropertyValuesRule("textAlign", arrayOf(
            "TextAlign.left", "TextAlign.right", "TextAlign.center",
            "TextAlign.justify", "TextAlign.start", "TextAlign.end"
        )),
        PropertyValuesRule("overflow", arrayOf(
            "TextOverflow.clip", "TextOverflow.fade",
            "TextOverflow.ellipsis", "TextOverflow.visible"
        )),
        PropertyValuesRule("fontWeight", arrayOf(
            "FontWeight.w100", "FontWeight.w200", "FontWeight.w300",
            "FontWeight.w400", "FontWeight.w500", "FontWeight.w600",
            "FontWeight.w700", "FontWeight.w800", "FontWeight.w900",
            "FontWeight.bold", "FontWeight.normal"
        )),
        PropertyValuesRule("fit", arrayOf(
            "BoxFit.fill", "BoxFit.contain", "BoxFit.cover",
            "BoxFit.fitWidth", "BoxFit.fitHeight", "BoxFit.none",
            "BoxFit.scaleDown"
        )),
        PropertyValuesRule("alignment", arrayOf(
            "Alignment.topLeft", "Alignment.topCenter", "Alignment.topRight",
            "Alignment.centerLeft", "Alignment.center", "Alignment.centerRight",
            "Alignment.bottomLeft", "Alignment.bottomCenter", "Alignment.bottomRight"
        ))
    )

    private val AFTER_STYLE_VALUES = arrayOf("TextStyle(")
    private val AFTER_DECORATION_VALUES = arrayOf("BoxDecoration(", "InputDecoration(")
    private val AFTER_INSETS_VALUES = arrayOf(
        "EdgeInsets.all(", "EdgeInsets.symmetric(", "EdgeInsets.only(",
        "EdgeInsets.fromLTRB(", "EdgeInsets.zero"
    )
    private val AFTER_BORDER_RADIUS = arrayOf(
        "BorderRadius.circular(", "BorderRadius.all(", "BorderRadius.only(",
        "BorderRadius.vertical(", "BorderRadius.horizontal("
    )
    private val AFTER_COLOR = arrayOf("Colors.")
    private val AFTER_CONTROLLER = arrayOf(
        "TextEditingController(", "ScrollController(",
        "PageController(", "AnimationController("
    )
    private val AFTER_PHYSICS = arrayOf(
        "BouncingScrollPhysics(", "ClampingScrollPhysics(",
        "AlwaysScrollableScrollPhysics(", "NeverScrollableScrollPhysics("
    )

    private val IMPORT_PATTERN = Pattern.compile("import\\s+['\"]([^'\"]*)$")
    private val STATE_METHOD_PATTERN = Pattern.compile("class\\s+\\w+\\s+extends\\s+State<")

    class AnalysisResult {
        var prefix: String = ""
        var currentWidget: String? = null
        var afterProperty: String? = null
        var insideImport: Boolean = false
        var insideState: Boolean = false
        val customSuggestions: MutableList<String> = mutableListOf()
        var useFilteredMode: Boolean = false
    }

    @JvmStatic
    fun analyze(text: String?, cursorPos: Int): AnalysisResult {
        val r = AnalysisResult()
        if (text == null || cursorPos < 0 || cursorPos > text.length) return r

        r.prefix = extractPrefix(text, cursorPos)

        val start = maxOf(0, cursorPos - LOOKBACK_LIMIT)
        val lookback = text.substring(start, cursorPos)

        // import
        val importMatcher = IMPORT_PATTERN.matcher(lookback)
        if (importMatcher.find()) {
            r.insideImport = true
            r.useFilteredMode = true
            return r
        }

        // State class
        if (STATE_METHOD_PATTERN.matcher(lookback).find()) {
            r.insideState = true
        }

        val lastNewline = lookback.lastIndexOf('\n')
        val lastLine = if (lastNewline >= 0) lookback.substring(lastNewline + 1) else lookback

        val afterProp = extractAfterProperty(lastLine)
        if (afterProp != null && r.prefix.isEmpty()) {
            r.afterProperty = afterProp
            r.useFilteredMode = true
            addPropertyValues(r.customSuggestions, afterProp)
            return r
        }

        val insideValueProp = extractAfterPropertyWithPrefix(lastLine, r.prefix)
        if (insideValueProp != null) {
            r.afterProperty = insideValueProp
            r.useFilteredMode = true
            addPropertyValues(r.customSuggestions, insideValueProp)
            return r
        }

        val widget = findEnclosingWidget(lookback)
        if (widget != null) {
            r.currentWidget = widget
            for (rule in WIDGET_RULES) {
                if (rule.widgetName == widget) {
                    r.customSuggestions.addAll(rule.properties)
                    break
                }
            }
        }

        return r
    }

    private fun extractPrefix(text: String, cursorPos: Int): String {
        if (cursorPos <= 0) return ""
        var start = cursorPos - 1
        while (start > 0) {
            val c = text[start - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '$') start--
            else break
        }
        return text.substring(start, cursorPos)
    }

    private fun extractAfterProperty(lastLine: String?): String? {
        if (lastLine == null) return null
        val trimmed = lastLine.trim()
        val m = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*\$").matcher(trimmed)
        if (m.find()) return m.group(1)
        return null
    }

    private fun extractAfterPropertyWithPrefix(lastLine: String?, prefix: String?): String? {
        if (lastLine == null || prefix.isNullOrEmpty()) return null
        val trimmed = lastLine.trim()
        if (!trimmed.endsWith(prefix)) return null

        val before = trimmed.substring(0, trimmed.length - prefix.length)
        val m = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*\$").matcher(before)
        if (m.find()) return m.group(1)
        return null
    }

    private fun findEnclosingWidget(lookback: String): String? {
        val start = maxOf(0, lookback.length - 400)
        val region = lookback.substring(start)

        var best: String? = null
        var bestPos = -1

        for (rule in WIDGET_RULES) {
            val needle = rule.widgetName + "("
            val pos = region.lastIndexOf(needle)
            if (pos < 0) continue
            if (isBracketOpen(region, pos + needle.length - 1)) {
                if (pos > bestPos) {
                    best = rule.widgetName
                    bestPos = pos
                }
            }
        }
        return best
    }

    private fun isBracketOpen(text: String, openPos: Int): Boolean {
        if (openPos < 0 || openPos >= text.length) return false
        if (text[openPos] != '(') return false

        var depth = 1
        for (i in openPos + 1 until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return false
                }
            }
        }
        return true
    }

    private fun addPropertyValues(out: MutableList<String>, property: String?) {
        if (property == null) return

        for (rule in PROPERTY_RULES) {
            if (rule.property == property) {
                out.addAll(rule.values)
                return
            }
        }

        when (property) {
            "style" -> out.addAll(AFTER_STYLE_VALUES)
            "decoration" -> out.addAll(AFTER_DECORATION_VALUES)
            "padding", "margin", "contentPadding" -> out.addAll(AFTER_INSETS_VALUES)
            "borderRadius" -> out.addAll(AFTER_BORDER_RADIUS)
            "color", "backgroundColor", "foregroundColor" -> out.addAll(AFTER_COLOR)
            "controller" -> out.addAll(AFTER_CONTROLLER)
            "physics" -> out.addAll(AFTER_PHYSICS)
        }
    }
}