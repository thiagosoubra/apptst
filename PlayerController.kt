package com.agospace.bokob

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

@SuppressLint("StaticFieldLeak")
object PlayerController {
    interface PlayerReadyCallback {
        fun onPlayerReady()
    }

    // Adicionado: Interface para comunicação de volta para a MainActivity
    interface OibleDataUpdateCallback {
        fun onBookPlayingStatusChanged(bookPath: String, isPlaying: Boolean)
        fun onPartStatusChanged(bookPath: String, partIndex: Int, newStatus: String, currentTime: Long)
        fun onBookStatusChanged(bookPath: String, newStatus: String)
        fun saveBooksCache() // Para instruir a MainActivity a persistir os dados
    }

    private var readyCallback: PlayerReadyCallback? = null
    private var dataUpdateCallback: OibleDataUpdateCallback? = null // Adicionado: Instância do novo callback
    private var isInitialized = false

    private lateinit var applicationContext: Context
    private var activityContext: MainActivity? = null
    private lateinit var player: ExoPlayer
    private var playerView: View? = null

    private var currentIndex = 0
    // ATUALIZADO: Agora mantém uma referência ao OibleBook atualmente em reprodução
    private var currentPlayingBook: OibleBook? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            // Salvar o tempo atual a cada 0.5 segundos (ou mais frequentemente se necessário)
            if (player.isPlaying && player.currentPosition > 0 && currentPlayingBook != null) {
                val part = currentPlayingBook?.parts?.getOrNull(currentIndex)
                if (part != null) {
                    val newCurrentTime = (player.currentPosition / 1000).toLong()
                    if (part.currentTime != newCurrentTime) {
                        part.currentTime = newCurrentTime
                        // Notificar a MainActivity para atualizar o status da parte e salvar o cache
                        dataUpdateCallback?.onPartStatusChanged(currentPlayingBook!!.path, currentIndex, part.status, part.currentTime!!)
                        dataUpdateCallback?.saveBooksCache()
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private var partTitleAnimator: ObjectAnimator? = null
    private var fullTitleAnimator: ObjectAnimator? = null

    private val SCROLL_SPEED_DP_PER_SECOND = 30f
    private val SCROLL_DELAY_MS = 2000L

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentPlayingBook?.let { book ->
                // Marcar a parte anterior como completa se ela terminou naturalmente
                val previousPartIndex = player.previousMediaItemIndex
                if (previousPartIndex != -1 && previousPartIndex < book.parts.size) {
                    val previousPart = book.parts[previousPartIndex]
                    previousPart.status = "complete"
                    previousPart.currentTime = 0L // Resetar o tempo atual para a parte concluída
                    dataUpdateCallback?.onPartStatusChanged(book.path, previousPartIndex, "complete", 0L)
                }

                currentIndex = player.currentMediaItemIndex
                updateOverlay()

                // Marcar a nova parte atual como incompleta se ela estiver como "unplayed"
                val newPart = book.parts.getOrNull(currentIndex)
                if (newPart != null && newPart.status == "unplayed") {
                    newPart.status = "incomplete"
                    dataUpdateCallback?.onPartStatusChanged(book.path, currentIndex, "incomplete", 0L)
                }
                dataUpdateCallback?.onBookPlayingStatusChanged(book.path, true) // Marcar o livro atual como em reprodução
                dataUpdateCallback?.saveBooksCache() // Persistir as mudanças
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerView?.findViewById<ImageButton>(R.id.playPause)?.apply {
                setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }
            currentPlayingBook?.let { book ->
                dataUpdateCallback?.onBookPlayingStatusChanged(book.path, isPlaying)
                val currentPart = book.parts.getOrNull(currentIndex)
                if (currentPart != null) {
                    if (!isPlaying && player.playbackState == Player.STATE_READY) { // Pausado
                        currentPart.currentTime = (player.currentPosition / 1000).toLong()
                        currentPart.status = "incomplete"
                        dataUpdateCallback?.onPartStatusChanged(book.path, currentIndex, "incomplete", currentPart.currentTime!!)
                        dataUpdateCallback?.saveBooksCache() // Persistir as mudanças ao pausar
                    } else if (!isPlaying && player.playbackState == Player.STATE_ENDED) { // Terminado
                        // A última parte reproduzida está agora completa.
                        currentPart.currentTime = 0L
                        currentPart.status = "complete"
                        dataUpdateCallback?.onPartStatusChanged(book.path, currentIndex, "complete", 0L)
                        dataUpdateCallback?.onBookPlayingStatusChanged(book.path, false) // Marcar livro como não reproduzindo
                        dataUpdateCallback?.saveBooksCache() // Persistir as mudanças ao terminar
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            currentPlayingBook?.let { book ->
                val currentPart = book.parts.getOrNull(currentIndex)
                if (currentPart != null) {
                    if (playbackState == Player.STATE_ENDED) {
                        currentPart.status = "complete"
                        currentPart.currentTime = 0L
                        dataUpdateCallback?.onPartStatusChanged(book.path, currentIndex, "complete", 0L)
                        dataUpdateCallback?.onBookPlayingStatusChanged(book.path, false)
                        dataUpdateCallback?.saveBooksCache()
                    }
                }
            }
        }
    }

    // Adicionado: Define o callback para atualização de dados
    fun setDataUpdateCallback(callback: OibleDataUpdateCallback) {
        dataUpdateCallback = callback
    }

    fun init(ctx: Context, exoPlayer: ExoPlayer) {
        applicationContext = ctx
        player = exoPlayer
        player.addListener(playerListener)
        isInitialized = true
        readyCallback?.onPlayerReady()
    }

    fun setReadyCallback(callback: PlayerReadyCallback) {
        readyCallback = callback
        if (isInitialized) callback.onPlayerReady()
    }

    // ATUALIZADO: Recebe OibleBook diretamente
    fun loadPlaylist(
        book: OibleBook,
        startIndex: Int = 0
    ) {
        currentPlayingBook = book // Armazena o livro em reprodução
        currentIndex = startIndex

        val mediaItems = book.parts.map { part -> // Obtém os itens de mídia das partes do livro
            MediaItem.fromUri(File(part.filePath).toUri())
        }

        val startPosition = book.parts.getOrNull(startIndex)?.currentTime?.times(1000L) ?: 0L
        player.setMediaItems(mediaItems, startIndex, startPosition)
        player.prepare()
        player.play()

        dataUpdateCallback?.onBookPlayingStatusChanged(book.path, true) // Marcar o livro atual como em reprodução
        updateOverlay()
        handler.post(updateRunnable)
    }

    fun setPlayerView(view: View, activityCtx: MainActivity) {
        playerView = view
        activityContext = activityCtx
        bindView(view)
        updateOverlay()
    }

    fun hidePlayerOverlay() {
        playerView = null
        activityContext = null
        handler.removeCallbacks(updateRunnable)
        partTitleAnimator?.cancel()
        fullTitleAnimator?.cancel()

        // Quando a sobreposição do player é escondida, se um livro estava tocando, marcar como não tocando
        currentPlayingBook?.let { book ->
            dataUpdateCallback?.onBookPlayingStatusChanged(book.path, false)
            dataUpdateCallback?.saveBooksCache() // Persistir as mudanças
            currentPlayingBook = null
        }
    }

    private fun bindView(view: View) {
        val playPause = view.findViewById<ImageButton>(R.id.playPause)
        val next = view.findViewById<ImageButton>(R.id.nextPart)
        val prev = view.findViewById<ImageButton>(R.id.prevPart)
        val timeSpeedAdjust = view.findViewById<TextView>(R.id.timeSpeedAdjust)
        val partTextButton = view.findViewById<ImageButton>(R.id.partText)

        playPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        next.setOnClickListener { player.seekToNextMediaItem() }
        prev.setOnClickListener { player.seekToPreviousMediaItem() }

        timeSpeedAdjust.setOnClickListener {
            activityContext?.let { ctx ->
                showSpeedAdjustmentDialog(ctx)
            }
        }

        partTextButton.setOnClickListener {
            val currentPart = currentPlayingBook?.parts?.getOrNull(currentIndex) // ATUALIZADO: Usar currentPlayingBook
            if (currentPart != null && currentPart.textFilePath.isNotEmpty()) {
                activityContext?.displayPartTextContent(currentPart.textFilePath, currentPart.title)
            } else {
                println("DEBUG: Arquivo de texto da parte não encontrado ou caminho vazio para ${currentPart?.title}")
            }
        }

        val progressContainer = view.findViewById<View>(R.id.progressContainer)
        progressContainer.setOnTouchListener { v, event ->
            val duration = player.duration
            if (duration <= 0) return@setOnTouchListener false

            val x = event.x
            val width = v.width
            val newPosition = (x / width * duration).toLong()
            player.seekTo(newPosition)
            true
        }
    }

    private fun showSpeedAdjustmentDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_speed_adjust, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.speedRadioGroup)
        val currentSpeed = player.playbackParameters.speed

        when (currentSpeed) {
            0.5f -> radioGroup.check(R.id.speed0_5x)
            0.75f -> radioGroup.check(R.id.speed0_75x)
            1.0f -> radioGroup.check(R.id.speed1_0x)
            1.25f -> radioGroup.check(R.id.speed1_25x)
            1.5f -> radioGroup.check(R.id.speed1_5x)
            1.75f -> radioGroup.check(R.id.speed1_75x)
            2.0f -> radioGroup.check(R.id.speed2_0x)
            else -> radioGroup.check(R.id.speed1_0x)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newSpeed = when (checkedId) {
                R.id.speed0_5x -> 0.5f
                R.id.speed0_75x -> 0.75f
                R.id.speed1_0x -> 1.0f
                R.id.speed1_25x -> 1.25f
                R.id.speed1_5x -> 1.5f
                R.id.speed1_75x -> 1.75f
                R.id.speed2_0x -> 2.0f
                else -> 1.0f
            }
            player.playbackParameters = PlaybackParameters(newSpeed)
            playerView?.findViewById<TextView>(R.id.timeSpeedAdjust)?.text = "${newSpeed}x"
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun startScrollingScrollViewAnimation(scrollView: HorizontalScrollView, animatorRef: ObjectAnimator?, contentWidth: Float): ObjectAnimator? {
        animatorRef?.cancel()
        scrollView.scrollTo(0, 0)

        val containerWidth = scrollView.width

        if (contentWidth > containerWidth) {
            val scrollDistance = contentWidth - containerWidth + dpToPx(10f)
            val duration = (scrollDistance / SCROLL_SPEED_DP_PER_SECOND * 1000).toLong()

            val animator = ObjectAnimator.ofInt(scrollView, "scrollX", 0, scrollDistance.toInt()).apply {
                this.duration = duration
                repeatMode = ObjectAnimator.RESTART
                repeatCount = ObjectAnimator.INFINITE
                startDelay = SCROLL_DELAY_MS
                start()
            }
            return animator
        }
        return null
    }

    private fun dpToPx(dp: Float): Float {
        return dp * applicationContext.resources.displayMetrics.density
    }

    fun updateOverlay() {
        playerView?.let { view ->
            val partTitleTextView = view.findViewById<TextView>(R.id.partTitle)
            val bookTitleTextView = view.findViewById<TextView>(R.id.title)
            val bookByTextView = view.findViewById<TextView>(R.id.by)
            val coverImageView = view.findViewById<ImageView>(R.id.cover)
            val currentPartIndexTextView = view.findViewById<TextView>(R.id.currentPartIndex)
            val allPartsIndexTextView = view.findViewById<TextView>(R.id.allPartsIndex)
            val playPauseButton = view.findViewById<ImageButton>(R.id.playPause)
            val timeSpeedAdjustTextView = view.findViewById<TextView>(R.id.timeSpeedAdjust)
            val prevPartButton = view.findViewById<ImageButton>(R.id.prevPart)
            val nextPartButton = view.findViewById<ImageButton>(R.id.nextPart)

            val partTitleScrollView = view.findViewById<HorizontalScrollView>(R.id.partTitleScrollView)
            val fullTitleScrollView = view.findViewById<HorizontalScrollView>(R.id.fullTitleScrollView)
            val fullTitleLinearLayout = view.findViewById<LinearLayout>(R.id.fullTitle)

            // ATUALIZADO: Obter informações do currentPlayingBook
            val currentPart = currentPlayingBook?.parts?.getOrNull(currentIndex)

            partTitleTextView.text = currentPart?.title ?: ""
            bookTitleTextView.text = currentPlayingBook?.title ?: ""
            bookByTextView.text = currentPlayingBook?.by ?: ""
            currentPlayingBook?.coverImagePath?.let {
                coverImageView.setImageURI(Uri.parse(it))
            }


            currentPartIndexTextView.text = (player.currentMediaItemIndex + 1).toString()
            allPartsIndexTextView.text = player.mediaItemCount.toString()

            playPauseButton.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            timeSpeedAdjustTextView.text = "${player.playbackParameters.speed}x"

            prevPartButton.isEnabled = player.hasPreviousMediaItem()
            nextPartButton.isEnabled = player.hasNextMediaItem()

            prevPartButton.alpha = if (prevPartButton.isEnabled) 1.0f else 0.5f
            nextPartButton.alpha = if (nextPartButton.isEnabled) 1.0f else 0.5f

            partTitleScrollView.post {
                val partTitleContentWidth = partTitleTextView.width.toFloat()
                partTitleAnimator = startScrollingScrollViewAnimation(partTitleScrollView, partTitleAnimator, partTitleContentWidth)
            }

            fullTitleLinearLayout.post {
                val combinedTextWidth = bookTitleTextView.paint.measureText(bookTitleTextView.text.toString()) +
                        bookByTextView.paint.measureText(bookByTextView.text.toString()) +
                        dpToPx(6f) +
                        dpToPx(6f) * 2

                fullTitleAnimator = startScrollingScrollViewAnimation(fullTitleScrollView, fullTitleAnimator, combinedTextWidth)
            }
        }
    }

    fun buildNotification(): Notification {
        val channelId = "oible_player"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Oible Player", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // ATUALIZADO: Usar currentPlayingBook para título e texto da notificação
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(currentPlayingBook?.title ?: "Reproduzindo...")
            .setContentText(currentPlayingBook?.parts?.getOrNull(currentIndex)?.title ?: "")
            .setSmallIcon(R.drawable.ic_play)
            .setOngoing(true)
            .build()
    }

    private fun updateProgress() {
        val view = playerView ?: return
        val duration = player.duration
        val position = player.currentPosition

        if (duration <= 0) return

        val progressPercent = (position * 100 / duration).toInt().coerceIn(0, 100)

        val progressBar = view.findViewById<View>(R.id.progressBar)
        val percentualText = view.findViewById<TextView>(R.id.progressPercentual)

        val progressContainer = view.findViewById<View>(R.id.progressContainer)
        val progressBarLayoutParams = progressBar.layoutParams as? ViewGroup.MarginLayoutParams
        val containerLeftMargin = (progressContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
        val containerRightMargin = (progressContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

        val fullWidth = progressContainer.width - containerLeftMargin - containerRightMargin

        if (fullWidth <= 0) return

        val barWidthPx = (fullWidth * progressPercent / 100f).toInt()

        if (progressBarLayoutParams != null && progressBarLayoutParams.width != barWidthPx) {
            progressBarLayoutParams.width = barWidthPx
            progressBar.layoutParams = progressBarLayoutParams
        }

        percentualText.text = "$progressPercent%"

        val percentualLayoutParams = percentualText.layoutParams as? ViewGroup.MarginLayoutParams
        if (percentualLayoutParams != null) {
            percentualLayoutParams.marginStart = (barWidthPx + (progressBarLayoutParams?.leftMargin ?: 0) - percentualText.measuredWidth / 2)
                .coerceAtLeast(0)
            percentualText.layoutParams = percentualLayoutParams
        }

        val elapsedText = view.findViewById<TextView>(R.id.timeElapsed)
        val remainingText = view.findViewById<TextView>(R.id.timeRemaining)

        elapsedText.text = formatMillis(position)
        remainingText.text = "-${formatMillis(duration - position)}"
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}