package com.example.ludoqueen

enum class TokenState { YARD, ACTIVE, FINISHED }

data class LudoToken(
    val playerIndex: Int,
    val tokenIndex: Int,
    var state: TokenState = TokenState.YARD,
    var localPosition: Int = -1
)

data class LudoPlayer(
    val playerIndex: Int,
    val label: String,
    val tokens: MutableList<LudoToken> = mutableListOf()
) {
    fun hasWon(): Boolean = tokens.all { it.state == TokenState.FINISHED }
}

data class MoveResult(
    val movedToken: LudoToken,
    val capturedTokens: List<LudoToken>,
    val reachedFinish: Boolean,
    val grantsExtraTurn: Boolean
)