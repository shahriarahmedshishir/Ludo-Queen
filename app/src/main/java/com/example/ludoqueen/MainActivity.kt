package com.example.ludoqueen

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
class MainActivity : AppCompatActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var tvTurnIndicator: TextView
    private lateinit var tvTurnBanner: TextView
    private lateinit var ludoBoardView: LudoBoardView
    private lateinit var diceViews: List<DiceView>

    private lateinit var engine: LudoEngine
    private var serverSocket: ServerSocket? = null
    private val SOCKET_PORT = 8888

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isTurnInProgress = AtomicBoolean(false)
    private var gameOver = false
    private var playerCount = 4
    private var isTeamMode = false
    private var activePlayers = listOf(0, 1, 2, 3)

    // Fixed order to match Ludo King: Red (0), Green (1), Yellow (2), Blue (3)
    private val playerColors = listOf(
        Color.parseColor("#E53935"), // P1 Red
        Color.parseColor("#43A047"), // P2 Green
        Color.parseColor("#FDD835"), // P3 Yellow
        Color.parseColor("#1E88E5")  // P4 Blue
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeader = findViewById(R.id.tvHeader)
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator)
        tvTurnBanner = findViewById(R.id.tvTurnBanner)
        ludoBoardView = findViewById(R.id.ludoBoardView)

        val diceP1: DiceView = findViewById(R.id.diceP1)
        val diceP2: DiceView = findViewById(R.id.diceP2)
        val diceP3: DiceView = findViewById(R.id.diceP3)
        val diceP4: DiceView = findViewById(R.id.diceP4)

        diceViews = listOf(diceP1, diceP2, diceP3, diceP4)

        playerCount = intent.getIntExtra("PLAYER_COUNT", 4)
        isTeamMode = intent.getBooleanExtra("IS_TEAM_MODE", false)
        Log.d("TEAM_MODE", "playerCount = $playerCount")
        Log.d("TEAM_MODE", "isTeamMode = $isTeamMode")

        val labels = listOf("P1", "P2", "P3", "P4")
        engine = LudoEngine(labels, isTeamMode = isTeamMode)

        activePlayers = when {
            isTeamMode -> listOf(0, 1, 2, 3)
            playerCount == 2 -> listOf(0, 2)
            playerCount == 3 -> listOf(0, 1, 2)
            else -> listOf(0, 1, 2, 3)
        }

        engine.setActivePlayers(activePlayers)
        ludoBoardView.bindEngine(engine)


        diceViews.forEachIndexed { index, dice ->

            if (index !in activePlayers) {
                dice.visibility = View.GONE
            } else {
                dice.visibility = View.VISIBLE

                dice.accentColor = playerColors[index]

                dice.setOnClickListener {
                    if (index == engine.currentPlayerIndex &&
                        !isTurnInProgress.get() &&
                        !gameOver
                    ) {
                        performTurn()
                    }
                }
            }
        }

        tvHeader.setOnLongClickListener {
            Toast.makeText(this, "IP: ${getLocalIpAddress()}", Toast.LENGTH_LONG).show()
            true
        }

        setupBackPressInterceptor()
        updateTurnIndicator(showBanner = true)
        startSocketServer()
    }

    private fun setupBackPressInterceptor() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exit Game")
                    .setMessage("Are you sure you want to exit the game? Your current progress will be lost.")
                    .setPositiveButton("Yes") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        })
    }

    private fun playSound(soundResourceId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(this, soundResourceId)
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("LudoQueenAudio", "Error playing sound: ${e.message}")
        }
    }

    private fun updateTurnIndicator(showBanner: Boolean) {
        if (gameOver) return

        tvTurnIndicator.text = ""

        ludoBoardView.activePlayerIndex = engine.currentPlayerIndex

        diceViews.forEachIndexed { index, dice ->
            dice.visibility =
                if (index in activePlayers) View.VISIBLE
                else View.GONE

            dice.isActive = (index == engine.currentPlayerIndex)
        }

        if (showBanner) {
            showTurnBanner(
                engine.currentPlayer().label,
                playerColors[engine.currentPlayerIndex]
            )
        }
    }

    private fun showTurnBanner(label: String, color: Int) {
        tvTurnBanner.text = "$label's Move"
        tvTurnBanner.setBackgroundColor(color)
        tvTurnBanner.visibility = android.view.View.VISIBLE
        tvTurnBanner.alpha = 0f
        tvTurnBanner.scaleX = 0.6f
        tvTurnBanner.scaleY = 0.6f
        tvTurnBanner.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                mainHandler.postDelayed({
                    tvTurnBanner.animate().alpha(0f).setDuration(300)
                        .withEndAction { tvTurnBanner.visibility = android.view.View.GONE }
                        .start()
                }, 900)
            }
            .start()
    }

    private fun performTurn() {
        if (gameOver) return
        isTurnInProgress.set(true)

        playSound(R.raw.sound_roll)

        // The current player's dice always rolls.
        val playerIndex = engine.currentPlayerIndex
        val diceView = diceViews[playerIndex]

        var shuffleCount = 0

        val shuffleRunnable = object : Runnable {
            override fun run() {
                if (shuffleCount < 7) {
                    diceView.value = Random.nextInt(1, 7)
                    shuffleCount++
                    mainHandler.postDelayed(this, 60)
                } else {
                    val diceValue = engine.rollDice(playerIndex)
                    diceView.value = diceValue

                    // Engine decides whether this moves teammate's token.
                    proceedAfterRoll(playerIndex, diceValue)
                }
            }
        }

        mainHandler.post(shuffleRunnable)
    }

    private fun proceedAfterRoll(playerIndex: Int, diceValue: Int) {
        val movable = engine.getMovableTokens(playerIndex, diceValue)

        val tokenOwner =
            if (isTeamMode && engine.players[playerIndex].hasWon()) {
                when (playerIndex) {
                    0 -> 2
                    2 -> 0
                    1 -> 3
                    3 -> 1
                    else -> playerIndex
                }
            } else {
                playerIndex
            }

        if (movable.isEmpty()) {
            mainHandler.postDelayed({
                finishTurn(false)
            }, 400)
            return
        }

        if (movable.size == 1) {
            val singleToken = movable.first()
            executeTokenMove(playerIndex, singleToken.tokenIndex, diceValue)
        } else {
            val actualTokenOwnerIndex = tokenOwner
            val interactiveTokens = movable.map { Pair(actualTokenOwnerIndex, it.tokenIndex) }.toSet()
            ludoBoardView.selectableTokens = interactiveTokens

            ludoBoardView.onTokenTapped = { tappedPlayerIndex, tappedTokenIndex ->
                val isMoveLegal = movable.any { it.playerIndex == tappedPlayerIndex && it.tokenIndex == tappedTokenIndex }

                if (isMoveLegal) {
                    ludoBoardView.selectableTokens = emptySet()
                    ludoBoardView.onTokenTapped = null
                    executeTokenMove(playerIndex, tappedTokenIndex, diceValue)
                } else {
                    val remaining = 57 - engine.players[tappedPlayerIndex].tokens[tappedTokenIndex].localPosition
                    Toast.makeText(this, "Illegal choice! Piece requires exactly $remaining to enter goal.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun executeTokenMove(
        playerIndex: Int,
        tokenIndex: Int,
        diceValue: Int
    ) {

        val tokenOwner =
            if (isTeamMode && engine.players[playerIndex].hasWon()) {
                when (playerIndex) {
                    0 -> 2
                    2 -> 0
                    1 -> 3
                    3 -> 1
                    else -> playerIndex
                }
            } else {
                playerIndex
            }

        val token = engine.players[tokenOwner].tokens[tokenIndex]
        val fromLocal = token.localPosition

        val result = engine.moveToken(
            playerIndex,
            tokenIndex,
            diceValue
        )

        ludoBoardView.animateTokenMove(
            tokenOwner,
            tokenIndex,
            fromLocal,
            token.localPosition
        ) {

            if (result.capturedTokens.isNotEmpty()) {
                playSound(R.raw.sound_capture)

                result.capturedTokens.forEach {

                    ludoBoardView.snapTokenToYard(
                        it.playerIndex,
                        it.tokenIndex
                    )
                }
            }
            else if (result.reachedFinish) {
                playSound(R.raw.sound_finish)
            }

            if (engine.isGameOver()) {
                handleGameOverState()
                return@animateTokenMove
            }

            finishTurn(result.grantsExtraTurn)
        }
    }

    private fun finishTurn(grantExtra: Boolean) {
        if (!grantExtra) {
            engine.advanceTurn()
        }
        updateTurnIndicator(showBanner = true)
        isTurnInProgress.set(false)
    }

    private fun handleGameOverState() {

        gameOver = true
        playSound(R.raw.sound_win)
        isTurnInProgress.set(false)

        val dialogView = layoutInflater.inflate(R.layout.dialog_game_over, null)

        val tvWinner = dialogView.findViewById<TextView>(R.id.tvWinner)
        val tvRanking = dialogView.findViewById<TextView>(R.id.tvRanking)

        if (isTeamMode) {

            val team1Won = engine.players[0].hasWon() && engine.players[2].hasWon()

            tvWinner.text =
                if (team1Won)
                    "🏆 TEAM RED & YELLOW WIN!"
                else
                    "🏆 TEAM GREEN & BLUE WIN!"

            tvRanking.text =
                if (team1Won)
                    "Congratulations!"
                else
                    "Congratulations!"

        } else {

            val allIndices = (0 until playerCount).toList()
            val loserIndex =
                allIndices.firstOrNull { !engine.leaderboard.contains(it) } ?: 3

            val ranking = mutableListOf<Int>()
            ranking.addAll(engine.leaderboard)

            if (!ranking.contains(loserIndex))
                ranking.add(loserIndex)

            tvWinner.text =
                "🏆 ${engine.players[ranking.first()].label} Wins!"

            val sb = StringBuilder()

            ranking.forEachIndexed { index, player ->

                sb.append("${index + 1}. ${engine.players[player].label}")

                if (index != ranking.lastIndex)
                    sb.append("\n")
            }

            tvRanking.text = sb.toString()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnRestart).setOnClickListener {

            dialog.dismiss()

            finish()

            startActivity(intent)

        }

        dialogView.findViewById<Button>(R.id.btnExit).setOnClickListener {

            dialog.dismiss()
            finish()

        }

        dialog.show()
    }

    private fun startSocketServer() {
        Thread {
            try {
                serverSocket = ServerSocket(SOCKET_PORT)
                while (true) {
                    val client = serverSocket!!.accept()
                    Thread {
                        try {
                            client.getInputStream().bufferedReader().use { reader ->
                                val line = reader.readLine()
                                if (!line.isNullOrBlank()) processPayload(line.trim())
                            }
                        } catch (e: Exception) {
                            Log.e("LudoQueenServer", "Client error: ${e.message}")
                        } finally {
                            client.close()
                        }
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("LudoQueenServer", "Server error: ${e.message}")
            }
        }.start()
    }

    private fun processPayload(payload: String) {
        try {
            val parts = payload.split(":")
            if (parts.size != 2) return
            val playerLabel = parts[0].trim().uppercase()
            val values = parts[1].split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..6 }
            if (values.isEmpty()) return

            val playerIndex = engine.players.indexOfFirst { it.label.equals(playerLabel, ignoreCase = true) }
            if (playerIndex == -1) return

            engine.queueForcedValues(playerIndex, values)
            Log.d("LudoQueenServer", "Queued $values for $playerLabel")
        } catch (e: Exception) {
            Log.e("LudoQueenServer", "Payload error: ${e.message}")
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LudoQueenServer", "IP error: ${e.message}")
        }
        return "Unknown"
    }

    override fun onDestroy() {
        super.onDestroy()
        try { serverSocket?.close() } catch (e: Exception) { }
    }
}