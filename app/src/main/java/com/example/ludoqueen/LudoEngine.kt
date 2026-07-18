package com.example.ludoqueen

import kotlin.random.Random

class LudoEngine(
    val playerLabels: List<String>,
    val isTeamMode: Boolean = false
) {
    class Token(val playerIndex: Int, val tokenIndex: Int) {
        // -1 = Yard, 0-50 = Main Track, 51-55 = Home Column, 56 = Goal
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
    private val forcedInputs = mutableMapOf<Int, MutableList<Int>>()
    private var consecutiveSixesCount = 0

    fun setActivePlayers(players: List<Int>) {
        activePlayers = players
        currentPlayerIndex = activePlayers.first()
    }

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
        val targetIdx = if (isTeamMode && roller.hasWon()) (PARTNER_MAP[playerIndex] ?: playerIndex) else playerIndex
        val token = players[targetIdx].tokens[tokenIndex]

        if (token.isHome()) {
            if (diceValue == 6) token.localPosition = 0
        } else {
            token.localPosition += diceValue
        }

        if (diceValue == 6) consecutiveSixesCount++ else consecutiveSixesCount = 0

        val reachedFinish = token.isGoal()
        if (reachedFinish && !leaderboard.contains(targetIdx) && players[targetIdx].hasWon()) {
            leaderboard.add(targetIdx)
        }

        val captured = mutableListOf<Token>()
        // Captures only occur on the main track (0 to 50)
        if (token.localPosition in 0..50) {
            val absolutePos = getAbsolutePosition(targetIdx, token.localPosition)
            if (absolutePos !in SAFE_CELLS) {
                players.forEach { opponent ->
                    val isTeammate = isTeamMode && (PARTNER_MAP[targetIdx] == opponent.playerIndex)
                    if (opponent.playerIndex != targetIdx && !isTeammate) {
                        opponent.tokens.forEach { oppToken ->
                            if (oppToken.localPosition in 0..50 &&
                                getAbsolutePosition(opponent.playerIndex, oppToken.localPosition) == absolutePos) {
                                oppToken.localPosition = -1
                                captured.add(oppToken)
                            }
                        }
                    }
                }
            }
        }
        return MoveResult(reachedFinish, captured, diceValue == 6 || reachedFinish || captured.isNotEmpty())
    }

    fun advanceTurn() {
        consecutiveSixesCount = 0
        if (isGameOver()) return

        var idx = activePlayers.indexOf(currentPlayerIndex)
        do {
            idx = (idx + 1) % activePlayers.size
            currentPlayerIndex = activePlayers[idx]
        } while (players[currentPlayerIndex].hasWon())
    }

    fun isGameOver(): Boolean {
        return if (isTeamMode) {
            (players[0].hasWon() && players[2].hasWon()) || (players[1].hasWon() && players[3].hasWon())
        } else {
            leaderboard.size >= activePlayers.size - 1
        }
    }

    fun getAbsolutePosition(playerIndex: Int, localPos: Int): Int {
        // Only return absolute position for the main track (0-50)
        if (localPos !in 0..50) return -1
        return (LudoPathData.START_OFFSETS[playerIndex]!! + localPos) % 52
    }
}