package com.flutteride.codweva

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Locale

/**
 * قالب "مدير المهام" — To-Do List احترافي.
 *
 * v2.0 — إضافة أيقونة Flutter المخصصة (XML):
 *   - كل مشروع Flutter جديد يحصل على أيقونة F زرقاء
 *   - مكتوبة برمجياً بنص XML (لا تحتاج res/raw)
 *   - تُحفظ في android/app/src/main/res/drawable/ic_launcher.xml
 *
 * محدّث لـ:
 *   - Flutter 3.47.2
 *   - Java 17
 *   - AGP 8.11.1
 *   - Kotlin 2.2.20
 *   - Gradle Wrapper 8.14
 */
object TodoTemplate {

    @JvmStatic
    fun write(
        context: Context?,
        projectDir: File,
        packageName: String,
        appName: String,
        apiLevel: String
    ): Boolean {
        return try {
            val packagePath = packageName.replace('.', '/')

            val libDir = File(projectDir, "lib")
            val androidDir = File(projectDir, "android")
            val appDir = File(androidDir, "app")
            val srcDir = File(appDir, "src")
            val mainDir = File(srcDir, "main")
            val resDir = File(mainDir, "res")
            val valuesDir = File(mainDir, "res/values")
            val kotlinDir = File(mainDir, "kotlin/$packagePath")
            val githubDir = File(projectDir, ".github")
            val workflowsDir = File(githubDir, "workflows")

            if (!libDir.mkdirs()) return false
            if (!resDir.mkdirs()) return false
            if (!valuesDir.mkdirs()) return false
            if (!kotlinDir.mkdirs()) return false
            if (!workflowsDir.mkdirs()) return false

            val appNameSnakeCase = toSnakeCase(appName)

            writeFile(
                File(projectDir, "pubspec.yaml"),
                buildPubspecYaml(appNameSnakeCase)
            )
            writeFile(File(libDir, "main.dart"), buildMainDart(appName))
            writeFile(File(projectDir, ".gitignore"), buildGitignore())

            writeFile(File(workflowsDir, "main.yml"), buildWorkflowYml())

            writeFile(File(androidDir, "build.gradle"), buildRootBuildGradle())
            writeFile(File(androidDir, "settings.gradle"), buildSettingsGradle())
            writeFile(File(androidDir, "gradle.properties"), buildGradleProperties())
            writeFile(File(androidDir, "local.properties"), "")
            writeFile(
                File(appDir, "build.gradle"),
                buildAppBuildGradle(packageName, apiLevel)
            )

            writeFile(File(valuesDir, "styles.xml"), buildStylesXml())
            writeFile(
                File(mainDir, "AndroidManifest.xml"),
                buildAndroidManifest(appName)
            )
            writeFile(
                File(kotlinDir, "MainActivity.kt"),
                buildMainActivityKt(packageName)
            )

            // ⭐ v2.0: نسخ أيقونة Flutter XML
            copyFlutterIcon(mainDir)

            GradleWrapperWriter.ensureGradleWrapper(context, projectDir)

            true
        } catch (_: Exception) {
            false
        }
    }

    // ============================================================
    // ⭐ v2.0: أيقونة Flutter المخصصة (XML)
    // ============================================================
    /**
     * ينسخ أيقونة Flutter (XML) إلى مشروع Flutter الجديد.
     *
     * الهدف: كل مشروع Flutter جديد يحصل على أيقونة F زرقاء
     *        بدل أيقونة Klencod الافتراضية.
     *
     * الخطوات:
     *   1. أنشئ مجلد drawable
     *   2. اكتب ic_launcher.xml
     *   3. احذف ic_launcher.png القديم (إن وُجد)
     */
    private fun copyFlutterIcon(mainDir: File) {
        try {
            // 1) أنشئ مجلد drawable
            val drawableDir = File(mainDir, "res/drawable")
            if (!drawableDir.exists() && !drawableDir.mkdirs()) return

            // 2) اكتب ic_launcher.xml
            val targetFile = File(drawableDir, "ic_launcher.xml")
            FileWriter(targetFile).use { writer ->
                writer.write(FLUTTER_ICON_XML)
                writer.flush()
            }

            // 3) احذف ic_launcher.png القديم (إن وُجد)
            //    لأن Android يفضّل PNG على XML عند وجود الاثنين
            val oldPng = File(drawableDir, "ic_launcher.png")
            if (oldPng.exists()) oldPng.delete()
        } catch (_: Exception) {
        }
    }

    /**
     * محتوى أيقونة Flutter (XML Vector Drawable).
     *
     * التصميم:
     *   - خلفية: تدرج أزرق Flutter (42A5F5 → 1976D2)
     *   - زوايا دائرية 22%
     *   - حرف F أبيض في المنتصف
     */
    private const val FLUTTER_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- ============================================================ -->
    <!-- 1) الخلفية: مربع بزوايا دائرية + تدرج أزرق Flutter            -->
    <!-- ============================================================ -->
    <path
        android:pathData="M24,0 L84,0 A24,24 0 0 1 108,24 L108,84 A24,24 0 0 1 84,108 L24,108 A24,24 0 0 1 0,84 L0,24 A24,24 0 0 1 24,0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:type="linear">
                <item android:offset="0" android:color="#FF42A5F5" />
                <item android:offset="1" android:color="#FF1976D2" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- ============================================================ -->
    <!-- 2) حرف F أبيض بخط عريض                                       -->
    <!-- ============================================================ -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M38,30 L74,30 L74,42 L52,42 L52,50 L70,50 L70,62 L52,62 L52,80 L38,80 Z" />

</vector>
"""

    // ============================================================
    // الأدوات
    // ============================================================

    private fun toSnakeCase(value: String?): String {
        if (value.isNullOrEmpty()) return "flutter_app"
        var result = value.lowercase(Locale.US)
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9_]"), "")
        if (result.isEmpty() || result[0].isDigit()) {
            result = "flutter_app"
        }
        return result
    }

    @Throws(IOException::class)
    private fun writeFile(file: File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    // ============================================================
    // pubspec.yaml
    // ============================================================
    private fun buildPubspecYaml(appNameSnakeCase: String): String {
        return """
            |name: $appNameSnakeCase
            |description: A professional task manager built with Flutter.
            |publish_to: 'none'
            |version: 1.0.0+1
            |
            |environment:
            |  sdk: '>=3.0.0 <4.0.0'
            |
            |dependencies:
            |  flutter:
            |    sdk: flutter
            |  shared_preferences: ^2.2.2
            |
            |flutter:
            |  uses-material-design: true
        """.trimMargin() + "\n"
    }

    // ============================================================
    // lib/main.dart
    // ============================================================
    private fun buildMainDart(appName: String?): String {
        val safeTitle = (appName ?: "مهامي").replace("'", "\\'")

        return """
            |import 'dart:convert';
            |import 'package:flutter/material.dart';
            |import 'package:flutter/services.dart';
            |import 'package:shared_preferences/shared_preferences.dart';
            |
            |void main() async {
            |  WidgetsFlutterBinding.ensureInitialized();
            |  runApp(const TaskMasterApp());
            |}
            |
            |class AppTheme {
            |  static const Color background = Color(0xFF0D1117);
            |  static const Color surface = Color(0xFF161B22);
            |  static const Color surfaceAlt = Color(0xFF21262D);
            |  static const Color border = Color(0xFF30363D);
            |  static const Color primary = Color(0xFF1F6FEB);
            |  static const Color primaryLight = Color(0xFF58A6FF);
            |  static const Color success = Color(0xFF2EA043);
            |  static const Color danger = Color(0xFFCF222E);
            |  static const Color warning = Color(0xFFD29922);
            |  static const Color textPrimary = Color(0xFFE6EDF3);
            |  static const Color textMuted = Color(0xFF8B949E);
            |
            |  static ThemeData get dark => ThemeData(
            |        useMaterial3: true,
            |        brightness: Brightness.dark,
            |        scaffoldBackgroundColor: background,
            |        colorScheme: const ColorScheme.dark(
            |          primary: primary,
            |          secondary: primaryLight,
            |          surface: surface,
            |          error: danger,
            |          onPrimary: Colors.white,
            |          onSurface: textPrimary,
            |        ),
            |        appBarTheme: const AppBarTheme(
            |          backgroundColor: background,
            |          elevation: 0,
            |          centerTitle: true,
            |          titleTextStyle: TextStyle(
            |            color: textPrimary,
            |            fontSize: 20,
            |            fontWeight: FontWeight.bold,
            |          ),
            |        ),
            |        floatingActionButtonTheme: const FloatingActionButtonThemeData(
            |          backgroundColor: primary,
            |          foregroundColor: Colors.white,
            |        ),
            |        inputDecorationTheme: InputDecorationTheme(
            |          filled: true,
            |          fillColor: surfaceAlt,
            |          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            |          border: OutlineInputBorder(
            |            borderRadius: BorderRadius.circular(14),
            |            borderSide: const BorderSide(color: border),
            |          ),
            |          enabledBorder: OutlineInputBorder(
            |            borderRadius: BorderRadius.circular(14),
            |            borderSide: const BorderSide(color: border),
            |          ),
            |          focusedBorder: OutlineInputBorder(
            |            borderRadius: BorderRadius.circular(14),
            |            borderSide: const BorderSide(color: primaryLight, width: 1.5),
            |          ),
            |        ),
            |      );
            |}
            |
            |class Task {
            |  final String id;
            |  String title;
            |  bool done;
            |  final DateTime createdAt;
            |
            |  Task({
            |    required this.id,
            |    required this.title,
            |    this.done = false,
            |    required this.createdAt,
            |  });
            |
            |  Map<String, dynamic> toJson() => {
            |        'id': id,
            |        'title': title,
            |        'done': done,
            |        'createdAt': createdAt.toIso8601String(),
            |      };
            |
            |  factory Task.fromJson(Map<String, dynamic> json) => Task(
            |        id: json['id'] as String,
            |        title: json['title'] as String,
            |        done: json['done'] as bool? ?? false,
            |        createdAt: DateTime.parse(json['createdAt'] as String),
            |      );
            |}
            |
            |class TaskMasterApp extends StatelessWidget {
            |  const TaskMasterApp({super.key});
            |
            |  @override
            |  Widget build(BuildContext context) {
            |    return MaterialApp(
            |      title: '$safeTitle',
            |      debugShowCheckedModeBanner: false,
            |      theme: AppTheme.dark,
            |      home: const TaskHomePage(),
            |    );
            |  }
            |}
            |
            |enum TaskFilter { all, active, completed }
            |
            |class TaskHomePage extends StatefulWidget {
            |  const TaskHomePage({super.key});
            |
            |  @override
            |  State<TaskHomePage> createState() => _TaskHomePageState();
            |}
            |
            |class _TaskHomePageState extends State<TaskHomePage>
            |    with SingleTickerProviderStateMixin {
            |  static const String _storageKey = 'klencod_tasks_v1';
            |
            |  final List<Task> _tasks = [];
            |  final TextEditingController _searchController = TextEditingController();
            |  TaskFilter _filter = TaskFilter.all;
            |  String _searchQuery = '';
            |  bool _loading = true;
            |  late AnimationController _fadeController;
            |
            |  @override
            |  void initState() {
            |    super.initState();
            |    _fadeController = AnimationController(
            |      vsync: this,
            |      duration: const Duration(milliseconds: 300),
            |    );
            |    _loadTasks();
            |  }
            |
            |  @override
            |  void dispose() {
            |    _fadeController.dispose();
            |    _searchController.dispose();
            |    super.dispose();
            |  }
            |
            |  Future<void> _loadTasks() async {
            |    try {
            |      final prefs = await SharedPreferences.getInstance();
            |      final data = prefs.getString(_storageKey);
            |      if (data != null && data.isNotEmpty) {
            |        final list = jsonDecode(data) as List<dynamic>;
            |        _tasks.clear();
            |        _tasks.addAll(list.map((e) => Task.fromJson(e as Map<String, dynamic>)));
            |      }
            |    } catch (_) {}
            |    setState(() => _loading = false);
            |    _fadeController.forward();
            |  }
            |
            |  Future<void> _saveTasks() async {
            |    try {
            |      final prefs = await SharedPreferences.getInstance();
            |      final data = jsonEncode(_tasks.map((t) => t.toJson()).toList());
            |      await prefs.setString(_storageKey, data);
            |    } catch (_) {}
            |  }
            |
            |  void _addTask(String title) {
            |    final trimmed = title.trim();
            |    if (trimmed.isEmpty) return;
            |    setState(() {
            |      _tasks.insert(
            |        0,
            |        Task(
            |          id: DateTime.now().microsecondsSinceEpoch.toString(),
            |          title: trimmed,
            |          createdAt: DateTime.now(),
            |        ),
            |      );
            |    });
            |    _saveTasks();
            |    HapticFeedback.lightImpact();
            |    _showSnack('تمت إضافة المهمة', success: true);
            |  }
            |
            |  void _toggleTask(Task task) {
            |    setState(() => task.done = !task.done);
            |    _saveTasks();
            |    HapticFeedback.selectionClick();
            |  }
            |
            |  void _deleteTask(Task task) {
            |    final index = _tasks.indexOf(task);
            |    if (index == -1) return;
            |    setState(() => _tasks.removeAt(index));
            |    _saveTasks();
            |    _showSnack(
            |      'تم حذف المهمة',
            |      actionLabel: 'تراجع',
            |      onAction: () {
            |        setState(() => _tasks.insert(index, task));
            |        _saveTasks();
            |      },
            |    );
            |  }
            |
            |  void _clearCompleted() {
            |    final completed = _tasks.where((t) => t.done).toList();
            |    if (completed.isEmpty) {
            |      _showSnack('لا توجد مهام مكتملة');
            |      return;
            |    }
            |    showDialog(
            |      context: context,
            |      builder: (ctx) => _buildConfirmDialog(
            |        ctx,
            |        title: 'حذف المهام المكتملة',
            |        message: 'سيتم حذف ${'$'}{completed.length} مهمة مكتملة. لا يمكن التراجع.',
            |        onConfirm: () {
            |          setState(() => _tasks.removeWhere((t) => t.done));
            |          _saveTasks();
            |          Navigator.pop(ctx);
            |          _showSnack('تم حذف ${'$'}{completed.length} مهمة', success: true);
            |        },
            |      ),
            |    );
            |  }
            |
            |  void _showSnack(String message,
            |      {String? actionLabel, VoidCallback? onAction, bool success = false}) {
            |    if (!mounted) return;
            |    ScaffoldMessenger.of(context).clearSnackBars();
            |    ScaffoldMessenger.of(context).showSnackBar(
            |      SnackBar(
            |        content: Row(
            |          children: [
            |            Icon(
            |              success ? Icons.check_circle_rounded : Icons.info_outline_rounded,
            |              color: success ? AppTheme.success : AppTheme.textPrimary,
            |              size: 20,
            |            ),
            |            const SizedBox(width: 10),
            |            Expanded(
            |              child: Text(
            |                message,
            |                style: const TextStyle(color: AppTheme.textPrimary),
            |              ),
            |            ),
            |          ],
            |        ),
            |        backgroundColor: AppTheme.surfaceAlt,
            |        behavior: SnackBarBehavior.floating,
            |        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            |        margin: const EdgeInsets.all(16),
            |        duration: const Duration(seconds: 3),
            |        action: actionLabel != null
            |            ? SnackBarAction(
            |                label: actionLabel,
            |                textColor: AppTheme.primaryLight,
            |                onPressed: onAction ?? () {},
            |              )
            |            : null,
            |      ),
            |    );
            |  }
            |
            |  Widget _buildConfirmDialog(
            |    BuildContext ctx, {
            |    required String title,
            |    required String message,
            |    required VoidCallback onConfirm,
            |  }) {
            |    return AlertDialog(
            |      backgroundColor: AppTheme.surface,
            |      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
            |      title: Text(title, style: const TextStyle(color: AppTheme.textPrimary)),
            |      content: Text(message, style: const TextStyle(color: AppTheme.textMuted)),
            |      actions: [
            |        TextButton(
            |          onPressed: () => Navigator.pop(ctx),
            |          child: const Text('إلغاء',
            |              style: TextStyle(color: AppTheme.textMuted)),
            |        ),
            |        TextButton(
            |          onPressed: onConfirm,
            |          child: const Text('تأكيد',
            |              style: TextStyle(
            |                  color: AppTheme.danger, fontWeight: FontWeight.bold)),
            |        ),
            |      ],
            |    );
            |  }
            |
            |  void _openAddDialog() {
            |    final controller = TextEditingController();
            |    showDialog(
            |      context: context,
            |      builder: (ctx) => AlertDialog(
            |        backgroundColor: AppTheme.surface,
            |        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
            |        title: Row(
            |          children: const [
            |            Icon(Icons.add_task_rounded, color: AppTheme.primaryLight),
            |            SizedBox(width: 10),
            |            Text('مهمة جديدة',
            |                style: TextStyle(color: AppTheme.textPrimary)),
            |          ],
            |        ),
            |        content: TextField(
            |          controller: controller,
            |          autofocus: true,
            |          textInputAction: TextInputAction.done,
            |          onSubmitted: (v) {
            |            _addTask(v);
            |            Navigator.pop(ctx);
            |          },
            |          decoration: const InputDecoration(
            |            hintText: 'اكتب المهمة...',
            |            prefixIcon: Icon(Icons.edit_rounded),
            |          ),
            |          style: const TextStyle(color: AppTheme.textPrimary),
            |        ),
            |        actions: [
            |          TextButton(
            |            onPressed: () => Navigator.pop(ctx),
            |            child: const Text('إلغاء',
            |                style: TextStyle(color: AppTheme.textMuted)),
            |          ),
            |          FilledButton(
            |            onPressed: () {
            |              _addTask(controller.text);
            |              Navigator.pop(ctx);
            |            },
            |            style: FilledButton.styleFrom(backgroundColor: AppTheme.primary),
            |            child: const Text('إضافة'),
            |          ),
            |        ],
            |      ),
            |    );
            |  }
            |
            |  List<Task> get _visibleTasks {
            |    Iterable<Task> result = _tasks;
            |    if (_filter == TaskFilter.active) {
            |      result = result.where((t) => !t.done);
            |    } else if (_filter == TaskFilter.completed) {
            |      result = result.where((t) => t.done);
            |    }
            |    if (_searchQuery.isNotEmpty) {
            |      final q = _searchQuery.toLowerCase();
            |      result = result.where((t) => t.title.toLowerCase().contains(q));
            |    }
            |    return result.toList();
            |  }
            |
            |  @override
            |  Widget build(BuildContext context) {
            |    if (_loading) {
            |      return const Scaffold(
            |        body: Center(
            |          child: CircularProgressIndicator(color: AppTheme.primaryLight),
            |        ),
            |      );
            |    }
            |
            |    final visible = _visibleTasks;
            |    final total = _tasks.length;
            |    final completed = _tasks.where((t) => t.done).length;
            |    final active = total - completed;
            |    final progress = total == 0 ? 0.0 : completed / total;
            |
            |    return Scaffold(
            |      appBar: AppBar(
            |        title: const Text('$safeTitle'),
            |        actions: [
            |          if (completed > 0)
            |            IconButton(
            |              tooltip: 'حذف المكتملة',
            |              icon: const Icon(Icons.delete_sweep_rounded),
            |              onPressed: _clearCompleted,
            |            ),
            |        ],
            |      ),
            |      floatingActionButton: FloatingActionButton.extended(
            |        onPressed: _openAddDialog,
            |        icon: const Icon(Icons.add_rounded),
            |        label: const Text('مهمة جديدة',
            |            style: TextStyle(fontWeight: FontWeight.bold)),
            |      ),
            |      body: Column(
            |        children: [
            |          _buildHeader(total, completed, active, progress),
            |          _buildSearchBar(),
            |          _buildFilters(total, active, completed),
            |          Expanded(
            |            child: visible.isEmpty
            |                ? _buildEmptyState()
            |                : _buildTaskList(visible),
            |          ),
            |        ],
            |      ),
            |    );
            |  }
            |
            |  Widget _buildHeader(int total, int completed, int active, double progress) {
            |    return Padding(
            |      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            |      child: Container(
            |        padding: const EdgeInsets.all(18),
            |        decoration: BoxDecoration(
            |          gradient: const LinearGradient(
            |            colors: [Color(0xFF1F6FEB), Color(0xFF0D419D)],
            |            begin: Alignment.topLeft,
            |            end: Alignment.bottomRight,
            |          ),
            |          borderRadius: BorderRadius.circular(20),
            |          boxShadow: [
            |            BoxShadow(
            |              color: AppTheme.primary.withOpacity(0.3),
            |              blurRadius: 16,
            |              offset: const Offset(0, 6),
            |            ),
            |          ],
            |        ),
            |        child: Column(
            |          crossAxisAlignment: CrossAxisAlignment.start,
            |          children: [
            |            Row(
            |              children: [
            |                const Icon(Icons.task_alt_rounded,
            |                    color: Colors.white, size: 28),
            |                const SizedBox(width: 10),
            |                Text(
            |                  total == 0 ? 'لا توجد مهام بعد' : 'مهامك',
            |                  style: const TextStyle(
            |                    color: Colors.white,
            |                    fontSize: 18,
            |                    fontWeight: FontWeight.bold,
            |                  ),
            |                ),
            |              ],
            |            ),
            |            const SizedBox(height: 14),
            |            Row(
            |              mainAxisAlignment: MainAxisAlignment.spaceBetween,
            |              children: [
            |                _statChip('الإجمالي', total, Colors.white),
            |                _statChip('نشطة', active, AppTheme.warning),
            |                _statChip('مكتملة', completed, AppTheme.success),
            |              ],
            |            ),
            |            const SizedBox(height: 16),
            |            ClipRRect(
            |              borderRadius: BorderRadius.circular(8),
            |              child: TweenAnimationBuilder<double>(
            |                tween: Tween(begin: 0, end: progress),
            |                duration: const Duration(milliseconds: 600),
            |                builder: (_, value, __) => LinearProgressIndicator(
            |                  value: value,
            |                  minHeight: 8,
            |                  backgroundColor: Colors.white24,
            |                  valueColor:
            |                      const AlwaysStoppedAnimation<Color>(Colors.white),
            |                ),
            |              ),
            |            ),
            |            const SizedBox(height: 6),
            |            Text(
            |              '${'$'}{(progress * 100).toStringAsFixed(0)}% إنجاز',
            |              style: const TextStyle(color: Colors.white70, fontSize: 12),
            |            ),
            |          ],
            |        ),
            |      ),
            |    );
            |  }
            |
            |  Widget _statChip(String label, int value, Color color) {
            |    return Container(
            |      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            |      decoration: BoxDecoration(
            |        color: Colors.white.withOpacity(0.15),
            |        borderRadius: BorderRadius.circular(10),
            |      ),
            |      child: Column(
            |        children: [
            |          Text(
            |            '${'$'}value',
            |            style: TextStyle(
            |              color: color,
            |              fontSize: 18,
            |              fontWeight: FontWeight.bold,
            |            ),
            |          ),
            |          Text(
            |            label,
            |            style: const TextStyle(color: Colors.white70, fontSize: 11),
            |          ),
            |        ],
            |      ),
            |    );
            |  }
            |
            |  Widget _buildSearchBar() {
            |    return Padding(
            |      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            |      child: TextField(
            |        controller: _searchController,
            |        onChanged: (v) => setState(() => _searchQuery = v),
            |        style: const TextStyle(color: AppTheme.textPrimary),
            |        decoration: InputDecoration(
            |          hintText: 'بحث في المهام...',
            |          prefixIcon: const Icon(Icons.search_rounded,
            |              color: AppTheme.textMuted),
            |          suffixIcon: _searchQuery.isNotEmpty
            |              ? IconButton(
            |                  icon: const Icon(Icons.clear_rounded,
            |                      color: AppTheme.textMuted),
            |                  onPressed: () {
            |                    _searchController.clear();
            |                    setState(() => _searchQuery = '');
            |                  },
            |                )
            |              : null,
            |        ),
            |      ),
            |    );
            |  }
            |
            |  Widget _buildFilters(int total, int active, int completed) {
            |    return Padding(
            |      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            |      child: Row(
            |        children: [
            |          _filterChip('الكل', TaskFilter.all, total),
            |          const SizedBox(width: 8),
            |          _filterChip('نشطة', TaskFilter.active, active),
            |          const SizedBox(width: 8),
            |          _filterChip('مكتملة', TaskFilter.completed, completed),
            |        ],
            |      ),
            |    );
            |  }
            |
            |  Widget _filterChip(String label, TaskFilter filter, int count) {
            |    final selected = _filter == filter;
            |    return Expanded(
            |      child: GestureDetector(
            |        onTap: () => setState(() => _filter = filter),
            |        child: AnimatedContainer(
            |          duration: const Duration(milliseconds: 200),
            |          padding: const EdgeInsets.symmetric(vertical: 10),
            |          decoration: BoxDecoration(
            |            color: selected ? AppTheme.primary : AppTheme.surfaceAlt,
            |            borderRadius: BorderRadius.circular(12),
            |            border: Border.all(
            |              color: selected ? AppTheme.primaryLight : AppTheme.border,
            |            ),
            |          ),
            |          child: Column(
            |            children: [
            |              Text(
            |                '${'$'}count',
            |                style: TextStyle(
            |                  color: selected ? Colors.white : AppTheme.textPrimary,
            |                  fontSize: 16,
            |                  fontWeight: FontWeight.bold,
            |                ),
            |              ),
            |              Text(
            |                label,
            |                style: TextStyle(
            |                  color: selected ? Colors.white70 : AppTheme.textMuted,
            |                  fontSize: 11,
            |                ),
            |              ),
            |            ],
            |          ),
            |        ),
            |      ),
            |    );
            |  }
            |
            |  Widget _buildTaskList(List<Task> visible) {
            |    return FadeTransition(
            |      opacity: _fadeController,
            |      child: ListView.builder(
            |        padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),
            |        itemCount: visible.length,
            |        itemBuilder: (context, index) {
            |          final task = visible[index];
            |          return _buildTaskItem(task);
            |        },
            |      ),
            |    );
            |  }
            |
            |  Widget _buildTaskItem(Task task) {
            |    return Dismissible(
            |      key: ValueKey(task.id),
            |      direction: DismissDirection.endToStart,
            |      background: Container(
            |        margin: const EdgeInsets.symmetric(vertical: 4),
            |        padding: const EdgeInsets.symmetric(horizontal: 20),
            |        decoration: BoxDecoration(
            |          color: AppTheme.danger,
            |          borderRadius: BorderRadius.circular(14),
            |        ),
            |        alignment: Alignment.centerRight,
            |        child: const Icon(Icons.delete_rounded,
            |            color: Colors.white, size: 26),
            |      ),
            |      confirmDismiss: (_) async {
            |        _deleteTask(task);
            |        return false;
            |      },
            |      child: AnimatedContainer(
            |        duration: const Duration(milliseconds: 250),
            |        margin: const EdgeInsets.symmetric(vertical: 4),
            |        decoration: BoxDecoration(
            |          color: AppTheme.surface,
            |          borderRadius: BorderRadius.circular(14),
            |          border: Border.all(
            |            color: task.done
            |                ? AppTheme.success.withOpacity(0.4)
            |                : AppTheme.border,
            |          ),
            |        ),
            |        child: Material(
            |          color: Colors.transparent,
            |          child: InkWell(
            |            borderRadius: BorderRadius.circular(14),
            |            onTap: () => _toggleTask(task),
            |            child: Padding(
            |              padding: const EdgeInsets.all(14),
            |              child: Row(
            |                children: [
            |                  AnimatedContainer(
            |                    duration: const Duration(milliseconds: 250),
            |                    width: 26,
            |                    height: 26,
            |                    decoration: BoxDecoration(
            |                      color: task.done
            |                          ? AppTheme.success
            |                          : Colors.transparent,
            |                      borderRadius: BorderRadius.circular(8),
            |                      border: Border.all(
            |                        color: task.done
            |                            ? AppTheme.success
            |                            : AppTheme.textMuted,
            |                        width: 2,
            |                      ),
            |                    ),
            |                    child: task.done
            |                        ? const Icon(Icons.check_rounded,
            |                            color: Colors.white, size: 18)
            |                        : null,
            |                  ),
            |                  const SizedBox(width: 14),
            |                  Expanded(
            |                    child: Column(
            |                      crossAxisAlignment: CrossAxisAlignment.start,
            |                      children: [
            |                        Text(
            |                          task.title,
            |                          style: TextStyle(
            |                            color: task.done
            |                                ? AppTheme.textMuted
            |                                : AppTheme.textPrimary,
            |                            fontSize: 15,
            |                            decoration: task.done
            |                                ? TextDecoration.lineThrough
            |                                : null,
            |                            decorationColor: AppTheme.textMuted,
            |                          ),
            |                        ),
            |                        const SizedBox(height: 4),
            |                        Text(
            |                          _formatDate(task.createdAt),
            |                          style: const TextStyle(
            |                            color: AppTheme.textMuted,
            |                            fontSize: 11,
            |                          ),
            |                        ),
            |                      ],
            |                    ),
            |                  ),
            |                  IconButton(
            |                    tooltip: 'حذف',
            |                    icon: const Icon(Icons.delete_outline_rounded,
            |                        color: AppTheme.textMuted),
            |                    onPressed: () => _deleteTask(task),
            |                  ),
            |                ],
            |              ),
            |            ),
            |          ),
            |        ),
            |      ),
            |    );
            |  }
            |
            |  String _formatDate(DateTime date) {
            |    final now = DateTime.now();
            |    final diff = now.difference(date);
            |    if (diff.inMinutes < 1) return 'الآن';
            |    if (diff.inHours < 1) return 'منذ ${'$'}{diff.inMinutes} دقيقة';
            |    if (diff.inDays < 1) return 'منذ ${'$'}{diff.inHours} ساعة';
            |    if (diff.inDays == 1) return 'أمس';
            |    return 'منذ ${'$'}{diff.inDays} يوم';
            |  }
            |
            |  Widget _buildEmptyState() {
            |    final String message;
            |    final IconData icon;
            |    if (_searchQuery.isNotEmpty) {
            |      message = 'لا توجد نتائج للبحث';
            |      icon = Icons.search_off_rounded;
            |    } else if (_filter == TaskFilter.completed) {
            |      message = 'لا توجد مهام مكتملة بعد';
            |      icon = Icons.check_circle_outline_rounded;
            |    } else if (_filter == TaskFilter.active) {
            |      message = 'كل المهام مكتملة!';
            |      icon = Icons.celebration_rounded;
            |    } else {
            |      message = 'ابدأ بإضافة مهمتك الأولى';
            |      icon = Icons.playlist_add_rounded;
            |    }
            |
            |    return Center(
            |      child: Column(
            |        mainAxisAlignment: MainAxisAlignment.center,
            |        children: [
            |          Container(
            |            padding: const EdgeInsets.all(24),
            |            decoration: BoxDecoration(
            |              color: AppTheme.surfaceAlt,
            |              shape: BoxShape.circle,
            |              border: Border.all(color: AppTheme.border),
            |            ),
            |            child: Icon(icon, size: 56, color: AppTheme.primaryLight),
            |          ),
            |          const SizedBox(height: 20),
            |          Text(
            |            message,
            |            style: const TextStyle(
            |              color: AppTheme.textMuted,
            |              fontSize: 15,
            |            ),
            |          ),
            |          const SizedBox(height: 6),
            |          const Text(
            |            'استخدم الزر أدناه للبدء',
            |            style: TextStyle(color: AppTheme.textMuted, fontSize: 12),
            |          ),
            |        ],
            |      ),
            |    );
            |  }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // .gitignore
    // ============================================================
    private fun buildGitignore(): String {
        return """
            |*.iml
            |.dart_tool/
            |.idea/
            |.packages
            |.pub/
            |build/
            |*.swp
            |.flutter-plugins
            |.flutter-plugins-dependencies
        """.trimMargin() + "\n"
    }

    // ============================================================
    // .github/workflows/main.yml
    // ============================================================
    private fun buildWorkflowYml(): String {
        return """
            |name: Build Flutter APK
            |
            |on:
            |  workflow_dispatch:
            |    inputs:
            |      flutter_version:
            |        description: 'Flutter version'
            |        required: true
            |        default: '3.47.2'
            |
            |jobs:
            |  build:
            |    runs-on: ubuntu-24.04
            |    steps:
            |      - name: Checkout
            |        uses: actions/checkout@v5
            |
            |      - name: Setup Gradle Cache
            |        uses: actions/cache@v5
            |        with:
            |          path: |
            |            ~/.gradle/caches
            |            ~/.gradle/wrapper
            |          key: ${'$'}{{ runner.os }}-gradle-${'$'}{{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
            |          restore-keys: |
            |            ${'$'}{{ runner.os }}-gradle-
            |
            |      - name: Setup Java 17
            |        uses: actions/setup-java@v5
            |        with:
            |          distribution: temurin
            |          java-version: '17'
            |
            |      - name: Setup Flutter
            |        uses: subosito/flutter-action@v2
            |        with:
            |          flutter-version: ${'$'}{{ github.event.inputs.flutter_version }}
            |          cache: true
            |          channel: stable
            |
            |      - name: Get dependencies
            |        run: flutter pub get
            |
            |      - name: Build APK
            |        run: flutter build apk --release
            |
            |      - name: Upload APK
            |        uses: actions/upload-artifact@v4
            |        with:
            |          name: app-release
            |          path: build/app/outputs/flutter-apk/app-release.apk
            |          if-no-files-found: error
            |          retention-days: 7
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/build.gradle
    // ============================================================
    private fun buildRootBuildGradle(): String {
        return """
            |allprojects {
            |    repositories {
            |        google()
            |        mavenCentral()
            |    }
            |}
            |
            |rootProject.buildDir = "../build"
            |subprojects {
            |    project.buildDir = "${'$'}{rootProject.buildDir}/${'$'}{project.name}"
            |}
            |subprojects {
            |    project.evaluationDependsOn(":app")
            |}
            |
            |tasks.register("clean", Delete) {
            |    delete rootProject.buildDir
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/settings.gradle
    // ============================================================
    private fun buildSettingsGradle(): String {
        return """
            |pluginManagement {
            |    def flutterSdkPath = {
            |        def properties = new Properties()
            |        file("local.properties").withInputStream { properties.load(it) }
            |        def flutterSdkPath = properties.getProperty("flutter.sdk")
            |        assert flutterSdkPath != null, "flutter.sdk not set in local.properties"
            |        return flutterSdkPath
            |    }()
            |
            |    includeBuild("${'$'}{flutterSdkPath}/packages/flutter_tools/gradle")
            |
            |    repositories {
            |        google()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |
            |plugins {
            |    id "dev.flutter.flutter-plugin-loader" version "1.0.0"
            |    id "com.android.application" version "8.11.1" apply false
            |    id "org.jetbrains.kotlin.android" version "2.2.20" apply false
            |}
            |
            |include ":app"
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/gradle.properties
    // ============================================================
    private fun buildGradleProperties(): String {
        return """
            |org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
            |android.useAndroidX=true
            |android.enableJetifier=true
            |android.builtInKotlin=true
            |android.newDsl=false
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/app/build.gradle
    // ============================================================
    private fun buildAppBuildGradle(packageName: String, apiLevel: String): String {
        return """
            |plugins {
            |    id "com.android.application"
            |    id "kotlin-android"
            |    id "dev.flutter.flutter-gradle-plugin"
            |}
            |
            |def localProperties = new Properties()
            |def localPropertiesFile = rootProject.file("local.properties")
            |if (localPropertiesFile.exists()) {
            |    localPropertiesFile.withReader("UTF-8") { reader ->
            |        localProperties.load(reader)
            |    }
            |}
            |
            |def flutterVersionCode = localProperties.getProperty("flutter.versionCode")
            |if (flutterVersionCode == null) {
            |    flutterVersionCode = "1"
            |}
            |
            |def flutterVersionName = localProperties.getProperty("flutter.versionName")
            |if (flutterVersionName == null) {
            |    flutterVersionName = "1.0"
            |}
            |
            |android {
            |    namespace = "$packageName"
            |    compileSdk = 36
            |    ndkVersion = flutter.ndkVersion
            |
            |    compileOptions {
            |        sourceCompatibility = JavaVersion.VERSION_17
            |        targetCompatibility = JavaVersion.VERSION_17
            |    }
            |
            |    kotlinOptions {
            |        jvmTarget = "17"
            |    }
            |
            |    defaultConfig {
            |        applicationId = "$packageName"
            |        minSdk = 24
            |        targetSdk = $apiLevel
            |        versionCode = flutterVersionCode.toInteger()
            |        versionName = flutterVersionName
            |    }
            |
            |    buildTypes {
            |        release {
            |            signingConfig = signingConfigs.debug
            |        }
            |    }
            |}
            |
            |flutter {
            |    source = "../.."
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // styles.xml
    // ============================================================
    private fun buildStylesXml(): String {
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <style name="NormalTheme" parent="android:Theme.Material.Light.NoActionBar">
            |        <item name="android:windowBackground">@android:color/white</item>
            |    </style>
            |</resources>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // AndroidManifest.xml
    // ============================================================
    private fun buildAndroidManifest(appName: String?): String {
        val safeLabel = (appName ?: "App").replace("\"", "\\\"")
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |
            |    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
            |    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
            |
            |    <application
            |        android:requestLegacyExternalStorage="true"
            |        android:label="$safeLabel"
            |        android:icon="@drawable/ic_launcher"
            |        android:theme="@style/NormalTheme">
            |        <activity
            |            android:name=".MainActivity"
            |            android:exported="true"
            |            android:launchMode="singleTop"
            |            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
            |            android:hardwareAccelerated="true"
            |            android:windowSoftInputMode="adjustResize">
            |            <intent-filter>
            |                <action android:name="android.intent.action.MAIN"/>
            |                <category android:name="android.intent.category.LAUNCHER"/>
            |            </intent-filter>
            |        </activity>
            |        <meta-data
            |            android:name="flutterEmbedding"
            |            android:value="2"/>
            |    </application>
            |</manifest>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // MainActivity.kt
    // ============================================================
    private fun buildMainActivityKt(packageName: String): String {
        return """
            |package $packageName
            |
            |import io.flutter.embedding.android.FlutterActivity
            |
            |class MainActivity: FlutterActivity() {
            |}
        """.trimMargin() + "\n"
    }
}