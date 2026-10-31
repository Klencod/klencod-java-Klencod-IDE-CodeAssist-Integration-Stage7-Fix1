package com.flutteride.codweva

import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * مدير الملفات الداخلي — الإصدار الكامل.
 *
 * v2.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 */
class FileManagerActivity : AppCompatActivity() {

    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()

    private lateinit var rootLayout: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var breadcrumbBar: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerLayoutManager: LinearLayoutManager
    private lateinit var adapter: FileAdapter

    private lateinit var btnBack: Button
    private lateinit var btnSelect: Button
    private lateinit var btnMore: Button
    private lateinit var btnUp: Button
    private lateinit var btnImport: Button
    private lateinit var tvSelectionCount: TextView
    private lateinit var topBarTitle: TextView

    private lateinit var rootsContainer: LinearLayout

    private var currentDirectory: File? = null
    private var isAtRootsScreen = true
    private val currentNodes = mutableListOf<FileSystemNode>()

    private val selectedNodes = LinkedHashSet<FileSystemNode>()

    private var selectionMode = false

    private var targetDirectory: File? = null

    private var showHidden = false

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        val targetPath = intent.getStringExtra(EXTRA_TARGET_DIR)
        if (!targetPath.isNullOrEmpty()) {
            targetDirectory = File(targetPath)
        }

        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            Toast.makeText(this, "الرجاء منح إذن التخزين أولاً", Toast.LENGTH_LONG).show()
            StoragePermissionHelper.requestStoragePermission(
                this, StoragePermissionHelper.REQUEST_CODE_STORAGE
            )
            finish()
            return
        }

        buildLayout()
        showRootsScreen()
    }

    /**
     * ⭐ v2.0 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
     */
    private fun setupWindowEdgeToEdge() {
        // 1) Edge-to-Edge إجباري
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2) دعم Display Cutout (النوتش) — من API 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 3) ألوان شريط الحالة والتنقل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = colorBg
            window.navigationBarColor = colorBg
        }

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    private fun buildLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        buildTopBar()
        rootLayout.addView(topBar)

        buildBreadcrumbBar()
        rootLayout.addView(breadcrumbBar)

        buildContentArea()
        rootLayout.addView(contentContainer)

        buildBottomBar()
        rootLayout.addView(bottomBar)

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v2.0: تطبيق الـ insets على الـ root layout
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v2.0 — تطبيق WindowInsets على الـ root layout.
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(systemBars.bottom, cutout.bottom)

            view.setPadding(left, top, right, bottom)
            insets
        }

        ViewCompat.requestApplyInsets(rootLayout)
    }

    private fun buildTopBar() {
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(colorSurface)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
        }

        btnBack = makeTopButton("←").apply {
            setOnClickListener { onBackPressed() }
        }
        topBar.addView(btnBack, LinearLayout.LayoutParams(dp(44), dp(44)))

        topBarTitle = TextView(this).apply {
            text = "مدير الملفات"
            setTextColor(colorText)
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(8), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(topBarTitle, LinearLayout.LayoutParams(0, dp(44), 1f))

        btnSelect = makeTopButton("تحديد").apply {
            setTextSize(12f)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { toggleSelectionMode() }
        }
        val selectParams = LinearLayout.LayoutParams(-2, dp(38)).apply {
            marginEnd = dp(6)
        }
        topBar.addView(btnSelect, selectParams)

        btnMore = makeTopButton("⋮").apply {
            setTextSize(18f)
            setOnClickListener { showMoreMenu() }
        }
        topBar.addView(btnMore, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun makeTopButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(colorText)
            setTextSize(14f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = UiStyle.rounded(
                this@FileManagerActivity, colorSurfaceAlt, 10, colorBorder, 1
            )
        }
    }

    private fun buildBreadcrumbBar() {
        breadcrumbBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(colorBg)
        }

        breadcrumbScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        breadcrumbContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        breadcrumbScroll.addView(
            breadcrumbContainer,
            FrameLayout.LayoutParams(-2, -2)
        )

        breadcrumbBar.addView(
            breadcrumbScroll,
            LinearLayout.LayoutParams(0, dp(36), 1f)
        )
    }

    private fun buildContentArea() {
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        recyclerView = RecyclerView(this).apply {
            setBackgroundColor(colorBg)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        recyclerLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = recyclerLayoutManager
        recyclerView.itemAnimator = null
        adapter = FileAdapter()
        recyclerView.adapter = adapter
        contentContainer.addView(recyclerView)

        rootsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(colorBg)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        contentContainer.addView(rootsContainer)
    }

    private fun buildBottomBar() {
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(colorSurface)
            layoutParams = LinearLayout.LayoutParams(-1, dp(64))
        }

        tvSelectionCount = TextView(this).apply {
            setTextColor(colorPrimaryLight)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        bottomBar.addView(tvSelectionCount, LinearLayout.LayoutParams(0, -2, 1f))

        btnUp = makeBottomButton("رفع مستوى", false).apply {
            setOnClickListener { navigateUp() }
        }
        val upParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(6)
        }
        bottomBar.addView(btnUp, upParams)

        btnImport = makeBottomButton("استيراد", true).apply {
            setOnClickListener { performImport() }
        }
        val importParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(6)
        }
        bottomBar.addView(btnImport, importParams)
    }

    private fun makeBottomButton(text: String, primary: Boolean): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else colorText)
            setTextSize(13f)
            isAllCaps = false
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            background = UiStyle.rounded(
                this@FileManagerActivity,
                if (primary) colorPrimary else colorSurfaceAlt,
                12,
                if (primary) colorPrimaryLight else colorBorder,
                1
            )
        }
    }

    private fun showRootsScreen() {
        isAtRootsScreen = true
        currentDirectory = null
        clearSelection()
        selectionMode = false

        recyclerView.visibility = View.GONE
        rootsContainer.visibility = View.VISIBLE
        rootsContainer.removeAllViews()

        topBarTitle.text = "اختر قرصاً"
        breadcrumbBar.visibility = View.GONE

        btnUp.isEnabled = false
        btnUp.alpha = 0.5f

        val roots = FileSystemRoot.detectRoots(this)

        if (roots.isEmpty()) {
            val empty = TextView(this).apply {
                text = "لا توجد أقراص متاحة للقراءة.\nتأكد من منح إذن التخزين."
                setTextColor(colorMuted)
                setTextSize(14f)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(80), dp(24), dp(80))
            }
            rootsContainer.addView(empty)
            return
        }

        val header = TextView(this).apply {
            text = "الأقراص المتاحة"
            setTextColor(colorText)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        rootsContainer.addView(header)

        for (root in roots) {
            rootsContainer.addView(buildRootCard(root))
        }
    }

    private fun buildRootCard(root: FileSystemRoot): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiStyle.rounded(
                this@FileManagerActivity, colorSurface, 14, colorBorder, 1
            )
            isClickable = true
            isFocusable = true

            if (!root.available) alpha = 0.5f

            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_tree_folder)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(12)
            }
        }
        topRow.addView(icon)

        val name = TextView(this).apply {
            text = root.displayName
            setTextColor(colorText)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        topRow.addView(name, LinearLayout.LayoutParams(0, -2, 1f))

        val arrow = TextView(this).apply {
            text = "‹"
            setTextColor(colorMuted)
            setTextSize(20f)
        }
        topRow.addView(arrow)

        card.addView(topRow)

        val spaceInfo = TextView(this).apply {
            text = root.spaceInfo
            setTextColor(colorMuted)
            setTextSize(11f)
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(spaceInfo)

        val usageBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = (root.usedRatio * 100).toInt()
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(usageBar, LinearLayout.LayoutParams(-1, dp(4)))

        card.setOnClickListener {
            if (!root.available) {
                Toast.makeText(
                    this,
                    "هذا القرص غير متاح للقراءة",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            root.directory?.let { openDirectory(it) }
        }

        return card
    }

    private fun openDirectory(dir: File?) {
        if (dir == null || !dir.isDirectory) {
            Toast.makeText(this, "المجلد غير موجود", Toast.LENGTH_SHORT).show()
            return
        }
        if (!dir.canRead()) {
            Toast.makeText(this, "لا يمكن قراءة هذا المجلد", Toast.LENGTH_SHORT).show()
            return
        }

        isAtRootsScreen = false
        currentDirectory = dir

        rootsContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        topBarTitle.text = if (dir.name.isEmpty()) "الجذر" else dir.name
        breadcrumbBar.visibility = View.VISIBLE

        val hasParent = dir.parentFile != null
        btnUp.isEnabled = hasParent
        btnUp.alpha = if (hasParent) 1f else 0.5f

        refreshDirectoryContents()
        updateBreadcrumb()

        recyclerView.scrollToPosition(0)
    }

    private fun refreshDirectoryContents() {
        currentNodes.clear()

        currentDirectory?.let { dir ->
            val children = dir.listFiles()

            if (children != null) {
                val dirs = mutableListOf<FileSystemNode>()
                val files = mutableListOf<FileSystemNode>()

                for (child in children) {
                    val node = FileSystemNode.fromFile(child) ?: continue

                    if (node.isHidden && !showHidden) continue

                    if (node.isDirectory) dirs.add(node)
                    else files.add(node)
                }

                dirs.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                currentNodes.addAll(dirs)
                currentNodes.addAll(files)
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun updateBreadcrumb() {
        breadcrumbContainer.removeAllViews()

        if (isAtRootsScreen || currentDirectory == null) {
            breadcrumbBar.visibility = View.GONE
            return
        }

        breadcrumbBar.visibility = View.VISIBLE

        val chain = mutableListOf<File>()
        var cur: File? = currentDirectory
        while (cur != null) {
            chain.add(0, cur)
            val parent = cur.parentFile
            if (parent == null || parent.absolutePath == cur.absolutePath) break
            if (parent.absolutePath == "/") {
                chain.add(0, File("/"))
                break
            }
            cur = parent
            if (chain.size > 30) break
        }

        for (i in chain.indices) {
            val segment = chain[i]
            val isLast = (i == chain.size - 1)

            val crumb = TextView(this).apply {
                var label = segment.name
                if (label.isEmpty()) label = segment.absolutePath
                text = label
                setTextColor(if (isLast) colorPrimaryLight else colorMuted)
                setTextSize(12f)
                setTypeface(
                    Typeface.DEFAULT,
                    if (isLast) Typeface.BOLD else Typeface.NORMAL
                )
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(140)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = UiStyle.rounded(
                    this@FileManagerActivity, 0x00000000, 6, 0x00000000, 0
                )

                if (!isLast) {
                    isClickable = true
                    setOnClickListener { openDirectory(segment) }
                }
            }

            breadcrumbContainer.addView(crumb)

            if (i < chain.size - 1) {
                val sep = TextView(this).apply {
                    text = " › "
                    setTextColor(colorMuted)
                    setTextSize(12f)
                    setPadding(dp(2), 0, dp(2), 0)
                }
                breadcrumbContainer.addView(sep)
            }
        }

        breadcrumbScroll.post {
            breadcrumbScroll.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun navigateUp() {
        if (isAtRootsScreen) return
        val cur = currentDirectory ?: return

        val parent = cur.parentFile
        if (parent == null || !parent.canRead()) {
            showRootsScreen()
            return
        }
        openDirectory(parent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        if (isAtRootsScreen) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            navigateUp()
        }
    }

    private fun showMoreMenu() {
        val items = mutableListOf<String>()
        items.add(if (showHidden) "إخفاء الملفات المخفية" else "إظهار الملفات المخفية")
        items.add("اختيار ملفات من المدير الخارجي")
        items.add("تحديد الكل")

        val arr = items.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("خيارات")
            .setAdapter(UiStyle.dialogListAdapter(this, arr)) { _, which ->
                when (which) {
                    0 -> toggleHidden()
                    1 -> openExternalFileManager()
                    2 -> selectAll()
                }
            }
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun toggleHidden() {
        showHidden = !showHidden
        Toast.makeText(
            this,
            if (showHidden) "إظهار الملفات المخفية" else "إخفاء الملفات المخفية",
            Toast.LENGTH_SHORT
        ).show()
        if (!isAtRootsScreen) refreshDirectoryContents()
    }

    private fun openExternalFileManager() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_PICK_EXTERNAL_FILES)
        } catch (_: Exception) {
            Toast.makeText(this, "لا يوجد مدير ملفات مثبّت", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_EXTERNAL_FILES &&
            resultCode == RESULT_OK && data != null
        ) {
            val selectedUris = mutableListOf<Uri>()

            data.data?.let { selectedUris.add(it) }

            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    if (uri != null) selectedUris.add(uri)
                }
            }

            if (selectedUris.isEmpty()) return

            var destinationDir: File? = if (isAtRootsScreen) null else currentDirectory
            if (destinationDir == null || !destinationDir.canWrite()) {
                destinationDir = targetDirectory
            }
            if (destinationDir == null || !destinationDir.canWrite()) {
                Toast.makeText(
                    this,
                    "لا يمكن الكتابة في المجلد الحالي",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            importExternalFiles(selectedUris, destinationDir)
        }
    }

    private fun importExternalFiles(uris: List<Uri>, destinationDir: File) {
        if (uris.isEmpty()) return
        if (!destinationDir.canWrite()) {
            Toast.makeText(this, "لا يمكن الكتابة في المجلد الحالي", Toast.LENGTH_LONG).show()
            return
        }

        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.CENTER
            }
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("جارٍ الاستيراد...")
            .setView(progress)
            .setCancelable(false)
            .create()
        progressDialog.show()
        UiStyle.styleDialog(progressDialog)

        Thread {
            var success = 0
            var renamed = 0
            var failed = 0
            val errors = mutableListOf<String>()

            for (uri in uris) {
                try {
                    var fileName = getFileNameFromUri(uri)
                    if (fileName.isNullOrEmpty()) {
                        fileName = "imported_${System.currentTimeMillis()}"
                    }

                    var destination = File(destinationDir, fileName)

                    if (destination.exists()) {
                        destination = findAvailableName(destinationDir, fileName)
                        renamed++
                    }

                    val input = contentResolver.openInputStream(uri)
                    if (input == null) {
                        failed++
                        errors.add("$fileName: تعذر فتح الملف")
                        continue
                    }

                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                    input.close()
                    success++
                } catch (e: Exception) {
                    failed++
                    val name = getFileNameFromUri(uri)
                    errors.add("${name ?: "ملف"}: ${e.message}")
                }
            }

            val finalSuccess = success
            val finalRenamed = renamed
            val finalFailed = failed

            runOnUiThread {
                progressDialog.dismiss()

                if (!isAtRootsScreen && currentDirectory == destinationDir) {
                    refreshDirectoryContents()
                }

                val msg = StringBuilder()
                msg.append("✅ نجح: $finalSuccess")
                if (finalRenamed > 0) msg.append("\n🔄 أُعيدت تسميته: $finalRenamed")
                if (finalFailed > 0) msg.append("\n❌ فشل: $finalFailed")
                if (errors.isNotEmpty()) {
                    msg.append("\n\nالتفاصيل:\n")
                    val max = minOf(5, errors.size)
                    for (i in 0 until max) {
                        msg.append("• ").append(errors[i]).append("\n")
                    }
                    if (errors.size > max) {
                        msg.append("... و ").append(errors.size - max).append(" أخرى")
                    }
                }

                val resultDialog = AlertDialog.Builder(this)
                    .setTitle("نتيجة الاستيراد")
                    .setMessage(msg.toString())
                    .setPositiveButton("حسناً", null)
                    .create()
                resultDialog.show()
                UiStyle.styleDialog(resultDialog)
            }
        }.start()
    }

    private fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) return null

        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
        }

        val path = uri.path
        if (path != null) {
            val slash = path.lastIndexOf('/')
            if (slash in 0 until path.length - 1) {
                return path.substring(slash + 1)
            }
        }

        return null
    }

    private fun toggleSelectionMode() {
        if (selectionMode) exitSelectionMode() else enterSelectionMode()
    }

    private fun enterSelectionMode() {
        selectionMode = true
        btnSelect.text = "إلغاء"
        btnSelect.background = UiStyle.rounded(
            this, colorPrimary, 10, colorPrimaryLight, 1
        )
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        btnSelect.text = "تحديد"
        btnSelect.background = UiStyle.rounded(
            this, colorSurfaceAlt, 10, colorBorder, 1
        )
        clearSelection()
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun clearSelection() {
        selectedNodes.clear()
        updateSelectionUI()
    }

    private fun toggleSelection(node: FileSystemNode) {
        val existing = selectedNodes.firstOrNull { it == node }
        if (existing != null) {
            selectedNodes.remove(existing)
        } else {
            selectedNodes.add(node)
        }

        if (selectionMode && selectedNodes.isEmpty()) {
            exitSelectionMode()
            return
        }

        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun isSelected(node: FileSystemNode?): Boolean {
        if (node == null) return false
        return selectedNodes.any { it == node }
    }

    private fun selectAll() {
        if (isAtRootsScreen) {
            Toast.makeText(this, "لا يمكن تحديد الكل في شاشة الجذور", Toast.LENGTH_SHORT).show()
            return
        }
        if (!selectionMode) enterSelectionMode()
        for (node in currentNodes) {
            if (!isSelected(node)) selectedNodes.add(node)
        }
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun updateSelectionUI() {
        if (selectedNodes.isEmpty()) {
            tvSelectionCount.visibility = View.GONE
            btnImport.isEnabled = false
            btnImport.alpha = 0.5f
        } else {
            tvSelectionCount.visibility = View.VISIBLE
            tvSelectionCount.text = "محدد: ${selectedNodes.size}"
            btnImport.isEnabled = true
            btnImport.alpha = 1f
        }
    }

    private inner class FileAdapter : RecyclerView.Adapter<FileAdapter.VH>() {

        @NonNull
        override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(this@FileManagerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(colorBg)
                layoutParams = RecyclerView.LayoutParams(-1, dp(62))
                isClickable = true
                isFocusable = true
            }

            val check = CheckBox(this@FileManagerActivity).apply {
                isClickable = false
                isFocusable = false
                visibility = View.GONE
            }
            val checkParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(4)
            }
            row.addView(check, checkParams)

            val icon = ImageView(this@FileManagerActivity)
            val iconParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(12)
            }
            row.addView(icon, iconParams)

            val textCol = LinearLayout(this@FileManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

            val name = TextView(this@FileManagerActivity).apply {
                setTextColor(colorText)
                setTextSize(14f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            textCol.addView(name)

            val meta = TextView(this@FileManagerActivity).apply {
                setTextColor(colorMuted)
                setTextSize(11f)
                isSingleLine = true
            }
            val metaParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(2)
            }
            textCol.addView(meta, metaParams)

            val arrow = TextView(this@FileManagerActivity).apply {
                text = "‹"
                setTextColor(colorMuted)
                setTextSize(20f)
                gravity = Gravity.CENTER
            }
            row.addView(arrow, LinearLayout.LayoutParams(dp(24), dp(24)))

            return VH(row, check, icon, name, meta, arrow)
        }

        override fun onBindViewHolder(@NonNull holder: VH, position: Int) {
            if (position < 0 || position >= currentNodes.size) return
            val node = currentNodes[position]

            holder.name.text = node.name

            if (node.isDirectory) {
                holder.icon.setImageResource(R.drawable.ic_tree_folder)
            } else {
                holder.icon.setImageResource(R.drawable.ic_tree_file)
            }

            val meta = if (node.isDirectory) {
                "${node.childCount} عنصر"
            } else {
                node.readableSize
            }
            holder.meta.text = meta

            holder.arrow.visibility = if (node.isDirectory) View.VISIBLE else View.GONE

            if (selectionMode) {
                holder.check.visibility = View.VISIBLE
                holder.check.isChecked = isSelected(node)
            } else {
                holder.check.visibility = View.GONE
            }

            holder.itemView.setBackgroundColor(
                if (isSelected(node)) 0xFF1F2D3F.toInt() else colorBg
            )

            holder.itemView.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(node)
                } else {
                    if (node.isDirectory) {
                        openDirectory(node.file)
                    } else {
                        showFilePreview(node)
                    }
                }
            }

            holder.itemView.setOnLongClickListener {
                if (!selectionMode) enterSelectionMode()
                toggleSelection(node)
                true
            }
        }

        override fun getItemCount(): Int = currentNodes.size

        inner class VH(
            itemView: View,
            val check: CheckBox,
            val icon: ImageView,
            val name: TextView,
            val meta: TextView,
            val arrow: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    private fun showFilePreview(node: FileSystemNode) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        addInfoRow(content, "الاسم", node.name)
        addInfoRow(content, "المسار", node.absolutePath)
        addInfoRow(content, "الحجم", node.readableSize)
        addInfoRow(content, "التاريخ", node.readableDate)
        addInfoRow(
            content,
            "النوع",
            if (node.extension.isEmpty()) "بدون امتداد" else ".${node.extension}"
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("معلومات الملف")
            .setView(content)
            .setPositiveButton("تحديد") { _, _ ->
                if (!selectionMode) enterSelectionMode()
                toggleSelection(node)
            }
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun addInfoRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(colorMuted)
            setTextSize(11f)
        }
        row.addView(tvLabel)

        val tvValue = TextView(this).apply {
            text = value
            setTextColor(colorText)
            setTextSize(13f)
            setTextIsSelectable(true)
        }
        row.addView(tvValue)

        parent.addView(row)
    }

    private fun performImport() {
        val target = targetDirectory
        if (target == null) {
            Toast.makeText(
                this,
                "لم يتم تحديد مجلد الوجهة. الرجاء العودة والمحاولة من المشروع.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!target.isDirectory || !target.canWrite()) {
            Toast.makeText(this, "مجلد الوجهة غير قابل للكتابة", Toast.LENGTH_LONG).show()
            return
        }

        if (selectedNodes.isEmpty()) {
            Toast.makeText(this, "لم تحدد أي عنصر", Toast.LENGTH_SHORT).show()
            return
        }

        val total = selectedNodes.size
        AlertDialog.Builder(this)
            .setTitle("تأكيد الاستيراد")
            .setMessage(
                "سيتم استيراد $total عنصر إلى:\n\n${target.name}" +
                        "\n\nالملفات المتعارضة ستُعاد تسميتها تلقائياً."
            )
            .setPositiveButton("استيراد") { _, _ -> doImport(target) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun doImport(target: File) {
        val snapshot = selectedNodes.toList()

        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.CENTER
            }
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("جارٍ الاستيراد...")
            .setView(progress)
            .setCancelable(false)
            .create()
        progressDialog.show()
        UiStyle.styleDialog(progressDialog)

        Thread {
            val result = ImportResult()

            for (node in snapshot) {
                try {
                    copyRecursive(node.file, target, result)
                } catch (e: Exception) {
                    result.errors.add("${node.name}: ${e.message}")
                }
            }

            runOnUiThread {
                progressDialog.dismiss()
                showImportResult(result)
                exitSelectionMode()
            }
        }.start()
    }

    private fun showImportResult(result: ImportResult) {
        val sb = StringBuilder()
        sb.append("✅ نجح: ${result.success}")
        if (result.renamed > 0) sb.append("\n🔄 أُعيدت تسميته: ${result.renamed}")
        if (result.skipped > 0) sb.append("\n⏭️ تُخطّي: ${result.skipped}")
        if (result.errors.isNotEmpty()) {
            sb.append("\n\n❌ أخطاء (${result.errors.size}):\n")
            val max = minOf(5, result.errors.size)
            for (i in 0 until max) {
                sb.append("• ").append(result.errors[i]).append("\n")
            }
            if (result.errors.size > max) {
                sb.append("... و ").append(result.errors.size - max).append(" أخرى")
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("نتيجة الاستيراد")
            .setMessage(sb.toString())
            .setPositiveButton("حسناً", null)
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private class ImportResult {
        var success = 0
        var renamed = 0
        var skipped = 0
        val errors = mutableListOf<String>()
    }

    @Throws(IOException::class)
    private fun copyRecursive(source: File, targetDir: File, result: ImportResult) {
        val baseName = source.name
        if (baseName.isEmpty()) {
            result.skipped++
            return
        }

        var destination = File(targetDir, baseName)

        if (destination.exists()) {
            destination = findAvailableName(targetDir, baseName)
            result.renamed++
        }

        if (source.isDirectory) {
            if (!destination.mkdirs() && !destination.isDirectory) {
                throw IOException("تعذر إنشاء المجلد: ${destination.name}")
            }
            result.success++

            source.listFiles()?.forEach { child ->
                copyRecursive(child, destination, result)
            }
        } else {
            if (!copyFile(source, destination)) {
                throw IOException("تعذر نسخ الملف: ${source.name}")
            }
            result.success++
        }
    }

    private fun findAvailableName(parentDir: File, originalName: String): File {
        var baseName = originalName
        var extension = ""

        val dotIndex = originalName.lastIndexOf('.')
        if (dotIndex > 0 && dotIndex < originalName.length - 1) {
            baseName = originalName.substring(0, dotIndex)
            extension = originalName.substring(dotIndex)
        }

        var counter = 1
        while (counter < 9999) {
            val candidateName = "$baseName ($counter)$extension"
            val candidate = File(parentDir, candidateName)
            if (!candidate.exists()) return candidate
            counter++
        }

        return File(parentDir, "${baseName}_${System.currentTimeMillis()}$extension")
    }

    private fun copyFile(source: File, target: File): Boolean {
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            true
        } catch (_: Exception) {
            try {
                if (target.exists()) target.delete()
            } catch (_: Exception) {
            }
            false
        }
    }

    companion object {
        const val EXTRA_TARGET_DIR = "target_dir"
        private const val REQUEST_PICK_EXTERNAL_FILES = 9002
    }
}