package com.example.ludoqueen

import kotlin.random.Random
import android.util.Log

class LudoEngine(
    val playerLabels: List<String>,
    val isTeamMode: Boolean = false
) {
    class Token(val playerIndex: Int, val tokenIndex: Int) {
        var localPosition = -1
        fun isHome() = localPosition == -1
        fun isGoal() = localPosition == 56
    }

    class Player(val label: String, val playerIndex: Int) {
        val tokens = List(4) { Token(playerIndex, it) }
        fun hasWon(): Boolean = tokens.all { it.isGoal() }
    }

    data class MoveResult(
        val reachedFinish: Boolean,
        val capturedTokens: List<Token>,
        val grantsExtraTurn: Boolean
    )

    val players = playerLabels.mapIndexed { index, label -> Player(label, index) }
    var currentPlayerIndex = 0
    var leaderboard = mutableListOf<Int>()

    private var activePlayers = listOf(0, 1, 2, 3)
    private val SAFE_CELLS = LudoPathData.SAFE_SQUARES
    private val PARTNER_MAP = mapOf(0 to 2, 2 to 0, 1 to 3, 3 to 1)
    private fun areTeammates(player1: Int, player2: Int): Boolean {

        if (!isTeamMode) return false

        return (player1 == 0 && player2 == 2) ||
                (player1 == 2 && player2 == 0) ||
                (player1 == 1 && player2 == 3) ||
                (player1 == 3 && player2 == 1)
    }
    private val forcedInputs = mutableMapOf<Int, MutableList<Int>>()
    private var consecutiveSixesCount = 0

    fun setActivePlayers(players: List<Int>) { activePlayers = players; currentPlayerIndex = activePlayers.first() }

    fun currentPlayer(): Player = players[currentPlayerIndex]

    fun queueForcedValues(playerIndex: Int, values: List<Int>) {
        if (playerIndex in players.indices) {
            forcedInputs.getOrPut(playerIndex) { mutableListOf() }.addAll(values)
        }
    }

    fun rollDice(playerIndex: Int): Int {
        val queue = forcedInputs[playerIndex]
        if (!queue.isNullOrEmpty()) return queue.removeAt(0)
        return Random.nextInt(1, 7)
    }

    fun getMovableTokens(playerIndex: Int, diceValue: Int): List<Token> {
        val roller = players[playerIndex]
        val targetIdx = if (isTeamMode && roller.hasWon()) (PARTNER_MAP[playerIndex] ?: playerIndex) else playerIndex

        if (players[targetIdx].hasWon()) return emptyList()
        if (diceValue == 6 && consecutiveSixesCount >= 2) return emptyList()

        return players[targetIdx].tokens.filter { token ->
            if (token.isGoal()) false
            else if (token.isHome()) diceValue == 6
            else if (token.localPosition + diceValue > 56) false
            else true
        }
    }

    fun moveToken(playerIndex: Int, tokenIndex: Int, diceValue: Int): MoveResult {

        val roller = players[playerIndex]
        val targetIdx =
            if (isTeamMode && roller.hasWon())
                PARTNER_MAP[playerIndex] ?: playerIndex
            else
                playerIndex

        val token = players[targetIdx].tokens[tokenIndex]

        // ---------------- Move ----------------

        if (token.isHome()) {
            if (diceValue != 6) {
                return MoveResult(false, emptyList(), false)
            }
            token.localPosition = 0
        } else {
            token.localPosition += diceValue
        }

        if (diceValue == 6)
            consecutiveSixesCount++
        else
            consecutiveSixesCount = 0

        val reachedFinish = token.isGoal()

        if (reachedFinish &&
            players[targetIdx].hasWon() &&
            !leaderboard.contains(targetIdx)
        ) {
            leaderboard.add(targetIdx)
        }

        val captured = mutableListOf<Token>()

        // ---------------- Capture ----------------

        if (token.localPosition in 0..50) {

            val myAbsolute =
                getAbsolutePosition(targetIdx, token.localPosition)

            if (myAbsolute !in SAFE_CELLS) {

                val friendly = mutableListOf<Token>()
                val enemy = mutableListOf<Token>()

                players.forEach { player ->

                    player.tokens.forEach { piece ->

                        if (piece.localPosition !in 0..50)
                            return@forEach

                        val abs =
                            getAbsolutePosition(
                                player.playerIndex,
                                piece.localPosition
                            )

                        if (abs != myAbsolute)
                            return@forEach

                        if (
                            player.playerIndex == targetIdx ||
                            (isTeamMode &&
                                    areTeammates(targetIdx, player.playerIndex))
                        ) {
                            friendly.add(piece)
                        } else {
                            enemy.add(piece)
                        }
                    }
                }

                val friendlyCount = friendly.size
                val enemyCount = enemy.size

                when {

                    // nothing to capture
                    enemyCount == 0 -> {
                    }

                    // one piece cannot capture a block
                    friendlyCount == 1 && enemyCount >= 2 -> {
                    }

                    // enough friendly pieces -> capture everyone
                    // enough friendly pieces -> capture everyone
                    friendlyCount >= enemyCount -> {

                        Log.d("TEAM_CAPTURE", "Attacker = $targetIdx")
                        Log.d("TEAM_CAPTURE", "Friendly = ${friendly.map { it.playerIndex }}")
                        Log.d("TEAM_CAPTURE", "Enemy = ${enemy.map { it.playerIndex }}")

                        enemy.forEach {
                            Log.d(
                                "TEAM_CAPTURE",
                                "Capturing Player ${it.playerIndex} Token ${it.tokenIndex}"
                            )

                            it.localPosition = -1
                            captured.add(it)
                        }
                    }
                }
            }
        }

        return MoveResult(
            reachedFinish = reachedFinish,
            capturedTokens = captured,
            grantsExtraTurn =
                diceValue == 6 ||
                        reachedFinish ||
                        captured.isNotEmpty()
        )
    }
    fun advanceTurn() {

        consecutiveSixesCount = 0

        if (isGameOver()) return

        var idx = activePlayers.indexOf(currentPlayerIndex)

        do {

            idx = (idx + 1) % activePlayers.size
            currentPlayerIndex = activePlayers[idx]

            if (!isTeamMode) {
                if (!players[currentPlayerIndex].hasWon()) break
            } else {
                // In Team Mode nobody is skipped until the game ends.
                break
            }

        } while (true)
    }


    fun isGameOver(): Boolean {
        return if (isTeamMode) (players[0].hasWon() && players[2].hasWon()) || (players[1].hasWon() && players[3].hasWon())
        else leaderboard.size >= activePlayers.size - 1
    }

    fun getAbsolutePosition(playerIndex: Int, localPos: Int): Int {
        if (localPos !in 0..50) return -1
        return (LudoPathData.START_OFFSETS[playerIndex]!! + localPos) % 52
    }
}