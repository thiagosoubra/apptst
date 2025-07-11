package com.agospace.bokob

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.animation.ArgbEvaluator
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import android.provider.OpenableColumns
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import java.util.regex.Pattern
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import android.view.MotionEvent
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide

// CORRIGIDO: MainActivity agora implementa PlayerController.OibleDataUpdateCallback
class MainActivity : AppCompatActivity(), PlayerController.PlayerReadyCallback, PlayerController.OibleDataUpdateCallback {

    private lateinit var recyclerView: RecyclerView
    private lateinit var uploadButton: TextView
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var oibleContentLayout: FrameLayout
    private lateinit var partTextContentLayout: ScrollView
    private lateinit var playerControlsView: View
    private lateinit var contentLayout: ConstraintLayout
    private lateinit var rootConstraintLayout: FrameLayout

    // Views dentro de oibleContentLayout
    private lateinit var bookTitleTextView: TextView
    private lateinit var bookAboutTextView: TextView
    private lateinit var bookTotalTimeTextView: TextView
    private lateinit var partsContainer: LinearLayout

    // View dentro de partTextContentLayout
    private lateinit var partTextView: TextView

    private val extractedBooks = mutableListOf<OibleBook>()
    private val listItems = mutableListOf<OibleListItem>()

    private val adapter by lazy {
        MixedAdapter(listItems) { book ->
            showOibleContent(book)
        }
    }

    // Variáveis para o serviço de player
    private var playerService: PlayerService? = null
    private var isPlayerServiceBound = false
    private lateinit var currentOibleBook: OibleBook

    // Removidas variáveis redundantes conforme discutido
    // private lateinit var currentOibleParts: List<OiblePart>
    // private lateinit var currentBookTitle: String
    // private lateinit var currentBookAuthor: String
    // private lateinit var currentCoverPath: String
    // private var currentTitleImagePath: String? = null
    // private var currentYear: Int = 0

    private var initialPlayerY: Float = 0f
    private var initialTouchY: Float = 0f
    private var playerHeight: Int = 0
    private var isPlayerExpanded = false
    private var isDraggingPlayer = false

    private var statusBarHeight: Int = 0
    private val hotZoneHeightDp = 56f
    private var hotZoneHeightPx: Float = 0f

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            playerService = (service as PlayerService.LocalBinder).getService()
            isPlayerServiceBound = true
            PlayerController.setReadyCallback(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            isPlayerServiceBound = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        loadBooksCache()
        processBooksIntoListItems()
        setContentView(R.layout.activity_main)

        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        mainContentLayout = findViewById(R.id.mainLayout)
        recyclerView = findViewById(R.id.oibleRecyclerView)
        uploadButton = findViewById(R.id.uploadButton)
        playerControlsView = findViewById(R.id.player)
        oibleContentLayout = findViewById(R.id.windowOible)
        partTextContentLayout = findViewById(R.id.windowPartText)
        contentLayout = findViewById(R.id.content)

        bookTitleTextView = oibleContentLayout.findViewById(R.id.bookTitle)
        bookAboutTextView = oibleContentLayout.findViewById(R.id.bookAbout)
        bookTotalTimeTextView = oibleContentLayout.findViewById(R.id.bookTotalTime)
        partsContainer = oibleContentLayout.findViewById(R.id.partsContainer)
        partTextView = partTextContentLayout.findViewById(R.id.partTextView)


        val layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    0 /* VIEW_TYPE_HEADER */ -> 2
                    2 /* VIEW_TYPE_GROUP */ -> 2
                    3 /* VIEW_TYPE_PAIR */ -> 2
                    else -> 1
                }
            }
        }
        recyclerView.layoutManager = layoutManager

        recyclerView.adapter = adapter

        val spacingPx = dpToPx(16f).toInt()
        val edgePx = dpToPx(20f).toInt()
        recyclerView.addItemDecoration(SpacingItemDecoration(2, spacingPx, edgePx))

        uploadButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        oibleContentLayout.visibility = View.GONE
        partTextContentLayout.visibility = View.GONE

        playerControlsView.visibility = View.VISIBLE

        // CORRIGIDO: Lógica de onBackPressedDispatcher para usar currentOibleBook
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    partTextContentLayout.visibility == View.VISIBLE -> {
                        hidePartTextContent()
                        if (::currentOibleBook.isInitialized) {
                            showOibleContent(currentOibleBook)
                        } else {
                            showMainContent()
                        }
                    }
                    oibleContentLayout.visibility == View.VISIBLE -> {
                        hideOibleContent()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        val serviceIntent = Intent(this, PlayerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        PlayerController.setDataUpdateCallback(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
            statusBarHeight = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
        } else {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                statusBarHeight = resources.getDimensionPixelSize(resourceId)
            }
        }
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpace)

        statusBarSpacer.post {
            val params = statusBarSpacer.layoutParams
            params.height = statusBarHeight
            statusBarSpacer.layoutParams = params
        }

        hotZoneHeightPx = dpToPx(hotZoneHeightDp)

        playerControlsView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                playerHeight = playerControlsView.height
                playerControlsView.translationY = -playerHeight.toFloat()
                initialPlayerY = -playerHeight.toFloat()

                updateContentMargin()

                playerControlsView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        val swipeHandle = findViewById<LinearLayout>(R.id.swipe_player)

        swipeHandle.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val playerView = playerControlsView

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)

                        initialTouchY = event.rawY
                        initialPlayerY = playerView.translationY
                        isDraggingPlayer = true
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!isDraggingPlayer) return false

                        val deltaY = event.rawY - initialTouchY
                        var newPlayerY = initialPlayerY + deltaY
                        newPlayerY = newPlayerY.coerceIn(-playerHeight.toFloat(), 0f)

                        playerView.translationY = newPlayerY


                        updateContentMargin()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDraggingPlayer) return false

                        val currentTranslationY = playerView.translationY
                        val halfPlayerHeight = -playerHeight / 2f

                        if (currentTranslationY > halfPlayerHeight) {
                            animatePlayer(0f)
                            isPlayerExpanded = true
                        } else {
                            animatePlayer(-playerHeight.toFloat())
                            isPlayerExpanded = false
                        }
                        isDraggingPlayer = false
                        return true
                    }
                }

                return false
            }
        })
    }

    private fun animatePlayer(targetY: Float) {
        val playerView = playerControlsView

        ValueAnimator.ofFloat(playerView.translationY, targetY).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                playerView.translationY = animatedValue
                updateContentMargin()
            }
            start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onPlayerReady() {
        PlayerController.setPlayerView(playerControlsView, this@MainActivity)
    }

    private fun View.fadeIn(duration: Long = 300) {
        this.alpha = 0f
        this.visibility = View.VISIBLE
        this.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
    }

    private fun View.fadeOut(duration: Long = 300, onEnd: () -> Unit = {}) {
        this.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                this.visibility = View.GONE
                onEnd()
            }
    }

    private fun showMainContent() {
        mainContentLayout.fadeIn()
        oibleContentLayout.fadeOut()
        partTextContentLayout.fadeOut()
    }

    private fun showOibleContent(book: OibleBook) {
        mainContentLayout.fadeOut()
        partTextContentLayout.fadeOut()
        oibleContentLayout.fadeIn()

        currentOibleBook = book

        bookTitleTextView.text = book.title
        bookAboutTextView.text = book.about
        bookTotalTimeTextView.text = "Tempo total: ${OiblePart("", book.totalDurationSec, "", "").formattedTime()}"

        val coverImageView = oibleContentLayout.findViewById<ImageView>(R.id.oibleImgCover)
        val titleImageView = oibleContentLayout.findViewById<ImageView>(R.id.oibleImgTitle)
        val yearTextView = oibleContentLayout.findViewById<TextView>(R.id.oibleYear)
        val byTextView = oibleContentLayout.findViewById<TextView>(R.id.oibleBy)

        Glide.with(this).load(File(book.coverImagePath)).into(coverImageView)

        book.titleImagePath?.let { path ->
            Glide.with(this).load(File(path)).into(titleImageView)
            titleImageView.visibility = View.VISIBLE
        } ?: run {
            titleImageView.visibility = View.GONE
        }

        yearTextView.text = if (book.year > 0) book.year.toString() else "Ano desconhecido"
        byTextView.text = book.by.uppercase()

        setupPlayButtons(book)
    }

    // CORRIGIDO: Removida a sobrecarga redundante de showOibleContent
    // private fun showOibleContent(bookTitle: String, bookBy: String, coverPath: String, parts: List<OiblePart>) { /* ... */ }

    private fun hideOibleContent() {
        oibleContentLayout.fadeOut { showMainContent() }
    }

    fun displayPartTextContent(textFilePath: String, partTitle: String) {
        oibleContentLayout.fadeOut()
        mainContentLayout.fadeOut()

        partTextContentLayout.fadeIn()

        supportActionBar?.title = partTitle ?: "Texto da Parte"

        if (textFilePath.isNotEmpty()) {
            try {
                val textFile = File(textFilePath)
                val rawText = textFile.readText()
                val formattedText = formatText(rawText)
                partTextView.text = formattedText
            } catch (e: IOException) {
                partTextView.text = "Erro ao carregar o texto: ${e.message}"
                e.printStackTrace()
            }
        } else {
            partTextView.text = "Nenhum caminho de arquivo de texto fornecido."
        }
    }

    private fun hidePartTextContent() {
        partTextContentLayout.fadeOut {
            supportActionBar?.title = getString(R.string.app_name)
        }
    }

    private fun setupPlayButtons(book: OibleBook) {
        partsContainer.removeAllViews()

        book.parts.forEachIndexed { index, part ->
            val itemView = layoutInflater.inflate(R.layout.line_part, partsContainer, false)

            itemView.findViewById<TextView>(R.id.oiblePartIndex).text = "${index + 1}".padStart(2, '0')
            itemView.findViewById<TextView>(R.id.oibletitle).text = part.title
            itemView.findViewById<TextView>(R.id.oiblePartTime).text = part.formattedTime()

            val oiblePartComplete = itemView.findViewById<TextView>(R.id.oiblePartComplete)
            val oiblePartTime = itemView.findViewById<TextView>(R.id.oiblePartTime)

            when (part.status) {
                "complete" -> {
                    oiblePartComplete.visibility = View.VISIBLE
                    oiblePartTime.visibility = View.GONE
                }
                "incomplete" -> {
                    oiblePartComplete.visibility = View.GONE
                    oiblePartTime.text = if (part.currentTime > 0) {
                        OiblePart("", part.currentTime.toInt(), "", "").formattedTime()
                    } else {
                        part.formattedTime()
                    }
                    oiblePartTime.visibility = View.VISIBLE
                }
                "unplayed" -> {
                    oiblePartComplete.visibility = View.GONE
                    oiblePartTime.visibility = View.VISIBLE
                }
            }

            itemView.setOnClickListener {
                if (isPlayerServiceBound) {
                    PlayerController.loadPlaylist(book, index)
                    animatePlayer(0f)
                    isPlayerExpanded = true
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Aguarde, o player está sendo inicializado...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            partsContainer.addView(itemView)
        }

        val playButton = Button(this@MainActivity).apply {
            text = "Reproduzir"
            setOnClickListener {
                if (!isPlayerServiceBound) {
                    Toast.makeText(
                        this@MainActivity,
                        "Aguarde, o player está sendo inicializado...",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                PlayerController.loadPlaylist(book, 0)
                animatePlayer(0f)
                isPlayerExpanded = true
            }
        }
        partsContainer.addView(playButton)
    }

    private fun formatText(rawText: String): CharSequence {
        val spannableStringBuilder = SpannableStringBuilder(rawText)

        val italicPattern = Pattern.compile("_([^_]+)_")
        val italicMatcher = italicPattern.matcher(spannableStringBuilder)
        val italicMatches = mutableListOf<Triple<Int, Int, String>>()

        while (italicMatcher.find()) {
            italicMatches.add(Triple(italicMatcher.start(), italicMatcher.end(), italicMatcher.group(1)!!))
        }

        italicMatches.reversed().forEach { (start, end, innerText) ->
            spannableStringBuilder.replace(start, end, innerText)
            spannableStringBuilder.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                start + innerText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val boldPattern = Pattern.compile("\\*([^*]+)\\*")
        val boldMatcher = boldPattern.matcher(spannableStringBuilder)
        val boldMatches = mutableListOf<Triple<Int, Int, String>>()

        while (boldMatcher.find()) {
            boldMatches.add(Triple(boldMatcher.start(), boldMatcher.end(), boldMatcher.group(1)!!))
        }

        boldMatches.reversed().forEach { (start, end, innerText) ->
            spannableStringBuilder.replace(start, end, innerText)
            spannableStringBuilder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + innerText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannableStringBuilder
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isPlayerServiceBound) {
            unbindService(serviceConnection)
            isPlayerServiceBound = false
        }
        PlayerController.hidePlayerOverlay()
    }

    private fun checkAndRequestPermissions() {
        val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 124)
            }
        }

        val neededStoragePermissions = storagePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededStoragePermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededStoragePermissions.toTypedArray(), 123)
        } else {
            openFilePicker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            123 -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    openFilePicker()
                } else {
                    Toast.makeText(this, "Permissões de arquivo negadas", Toast.LENGTH_SHORT).show()
                }
            }

            124 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permissão de notificação concedida
                } else {
                    Toast.makeText(this, "Notificações desativadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.forEach { uri ->
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            handleOibleFile(uri)
        }
        processBooksIntoListItems()
    }

    private fun handleOibleFile(uri: Uri) {
        val fileName = getFileName(uri).removeSuffix(".oible")
        val outputDir = File(filesDir, "Oibles/$fileName")

        if (outputDir.exists()) {
            Log.d("OibleFileHandler", "Oible already extracted: ${outputDir.absolutePath}")
            return
        }

        outputDir.mkdirs()

        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val outFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            zipStream.copyTo(out)
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }

        val dataJsonFile = File(outputDir, "data.json")
        if (!dataJsonFile.exists()) {
            Log.e("OibleFileHandler", "data.json not found in extracted oible: ${outputDir.absolutePath}")
            return
        }

        val json = JSONObject(dataJsonFile.readText())
        val title = json.getString("title")
        val by = json.optString("by", "")
        val about = json.optString("about", "")
        val year = json.optInt("year", 0)
        val coverImagePath = File(outputDir, "image/cover.jpg").absolutePath
        val titleImageFile = File(outputDir, "image/title.png")
        val titleImagePath = if (titleImageFile.exists()) titleImageFile.absolutePath else null

        val series = if (json.has("series")) {
            val s = json.getJSONObject("series")
            SeriesInfo(
                s.getString("title"),
                s.getString("by"),
                s.getString("index")
            )
        } else null

        val partFolder = File(outputDir, "part")
        val partListFiles = partFolder.listFiles()?.sortedBy { it.name.toIntOrNull() } ?: emptyList()

        var calculatedTotalDurationSec = 0
        val oibleParts = partListFiles.mapNotNull { folder ->
            val txtFile = folder.listFiles()?.firstOrNull { it.name.endsWith(".txt") }
            val mp3File = folder.listFiles()?.firstOrNull { it.name.endsWith(".mp3") }

            if (txtFile != null && mp3File != null) {
                val rawTitle = txtFile.nameWithoutExtension
                val formattedTitle = rawTitle.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                val duration = mp3File.nameWithoutExtension.toIntOrNull() ?: 0
                calculatedTotalDurationSec += duration
                OiblePart(
                    title = formattedTitle,
                    durationSec = duration,
                    filePath = mp3File.absolutePath,
                    textFilePath = txtFile.absolutePath,
                    status = "unplayed",
                    currentTime = 0L
                )
            } else null
        }

        val book = OibleBook(
            title = title,
            by = by,
            about = about,
            totalDurationSec = calculatedTotalDurationSec,
            year = year,
            series = series,
            path = outputDir.absolutePath,
            coverImagePath = coverImagePath,
            titleImagePath = titleImagePath,
            playing = false,
            status = "unplayed",
            addedAt = System.currentTimeMillis(),
            parts = oibleParts
        )
        extractedBooks.add(book)
        saveBooksCache()
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return name
    }
    private fun processBooksIntoListItems() {
        listItems.clear()
        listItems.add(OibleListItem.OibleHeader)

        val grouped = extractedBooks.groupBy { it.series?.title }

        for ((key, books) in grouped) {
            if (key == null) {
                val paired = books.chunked(2).map {
                    if (it.size == 2)
                        OibleListItem.SingleBookPair(it[0], it[1])
                    else
                        OibleListItem.SingleBookPair(it[0], null)
                }
                listItems.addAll(paired)
            } else {
                val ordered = books.sortedBy {
                    it.series?.index?.substringBefore("/")?.toIntOrNull() ?: 0
                }
                listItems.add(OibleListItem.BookGroup(key, ordered))
            }
        }

        adapter.notifyDataSetChanged()
    }

    // CORRIGIDO: Adicionado 'override' e removido 'private'
    override fun saveBooksCache() {
        val cacheFile = File(filesDir, "books_cache.json")
        val jsonArray = extractedBooks.map { book ->
            JSONObject().apply {
                put("title", book.title)
                put("by", book.by)
                put("about", book.about)
                put("totalDurationSec", book.totalDurationSec)
                put("year", book.year)
                put("coverImagePath", book.coverImagePath)
                put("path", book.path)
                put("titleImagePath", book.titleImagePath)
                put("playing", book.playing)
                put("status", book.status)
                put("addedAt", book.addedAt)

                book.series?.let {
                    val s = JSONObject().apply {
                        put("title", it.title)
                        put("by", it.by)
                        put("index", it.index)
                    }
                    put("series", s)
                }

                val partsJsonArray = org.json.JSONArray()
                book.parts.forEach { part ->
                    val partJson = JSONObject().apply {
                        put("title", part.title)
                        put("durationSec", part.durationSec)
                        put("filePath", part.filePath)
                        put("textFilePath", part.textFilePath)
                        put("status", part.status)
                        put("currentTime", part.currentTime)
                    }
                    partsJsonArray.put(partJson)
                }
                put("parts", partsJsonArray)
            }
        }

        val json = jsonArray.joinToString(prefix = "[", postfix = "]")
        cacheFile.writeText(json)
    }

    private fun loadBooksCache() {
        extractedBooks.clear()
        val cacheFile = File(filesDir, "books_cache.json")
        if (!cacheFile.exists()) return

        val jsonStr = cacheFile.readText()
        val jsonArray = org.json.JSONArray(jsonStr)

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)

            val series = if (json.has("series") && !json.isNull("series")) {
                val s = json.getJSONObject("series")
                SeriesInfo(
                    s.getString("title"),
                    s.getString("by"),
                    s.getString("index")
                )
            } else null

            val titleImagePath = if (json.has("titleImagePath") && !json.isNull("titleImagePath")) {
                json.getString("titleImagePath")
            } else null

            val partsJsonArray = json.getJSONArray("parts")
            val loadedParts = mutableListOf<OiblePart>()
            for (j in 0 until partsJsonArray.length()) {
                val partJson = partsJsonArray.getJSONObject(j)
                loadedParts.add(
                    OiblePart(
                        title = partJson.getString("title"),
                        durationSec = partJson.getInt("durationSec"),
                        filePath = partJson.getString("filePath"),
                        textFilePath = partJson.getString("textFilePath"),
                        status = partJson.optString("status", "unplayed"),
                        currentTime = partJson.optLong("currentTime", 0L)
                    )
                )
            }

            val book = OibleBook(
                title = json.getString("title"),
                by = json.getString("by"),
                about = json.getString("about"),
                totalDurationSec = json.optInt("totalDurationSec", 0),
                year = json.optInt("year", 0),
                series = series,
                path = json.getString("path"),
                coverImagePath = json.getString("coverImagePath"),
                titleImagePath = titleImagePath,
                playing = json.optBoolean("playing", false),
                status = json.optString("status", "unplayed"),
                addedAt = json.optLong("addedAt", 0L),
                parts = loadedParts
            )
            extractedBooks.add(book)
        }
    }

    private fun updateBookStatus(book: OibleBook) {
        val allPartsComplete = book.parts.all { it.status == "complete" }
        val anyPartIncomplete = book.parts.any { it.status == "incomplete" }
        val anyPartPlayed = book.parts.any { it.status == "complete" || it.status == "incomplete" }

        val newBookStatus = when {
            allPartsComplete -> "complete"
            !allPartsComplete && anyPartPlayed -> "incomplete"
            else -> "unplayed"
        }

        if (book.status != newBookStatus) {
            book.status = newBookStatus
            processBooksIntoListItems()
        }
    }

    override fun onBookPlayingStatusChanged(bookPath: String, isPlaying: Boolean) {
        extractedBooks.find { it.path == bookPath }?.let { book ->
            book.playing = isPlaying
            extractedBooks.filter { it.path != bookPath }.forEach { otherBook ->
                if (otherBook.playing) {
                    otherBook.playing = false
                }
            }
        }
    }

    override fun onPartStatusChanged(bookPath: String, partIndex: Int, newStatus: String, currentTime: Long) {
        extractedBooks.find { it.path == bookPath }?.let { book ->
            book.parts.getOrNull(partIndex)?.let { part ->
                part.status = newStatus
                part.currentTime = currentTime
                updateBookStatus(book)
            }
        }
    }

    override fun onBookStatusChanged(bookPath: String, newStatus: String) {
        extractedBooks.find { it.path == bookPath }?.let { book ->
            book.status = newStatus
            processBooksIntoListItems()
        }
    }

    // CORRIGIDO: Já é um override da interface, não precisa de uma chamada duplicada.
    // O compilador já entende que esta é a implementação do método da interface.
    // Se você tinha uma chamada como 'saveBooksCache()' aqui antes, ela se tornou redundante.
    // override fun saveBooksCache() {
    //     saveBooksCache() // Esta linha era o problema da ambiguidade
    // }


    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun updateContentMargin() {
        val playerY = playerControlsView.translationY
        val visibleHeight = (playerHeight + playerY).toInt().coerceAtLeast(0)

        val params = contentLayout.layoutParams as FrameLayout.LayoutParams
        if (params.topMargin != visibleHeight) {
            params.topMargin = visibleHeight
            contentLayout.layoutParams = params
        }
    }
}