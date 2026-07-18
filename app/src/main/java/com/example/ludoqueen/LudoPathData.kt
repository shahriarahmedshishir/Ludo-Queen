package com.example.ludoqueen

object LudoPathData {

    val RING_PATH: List<Pair<Int, Int>> = listOf(
        Pair(6,1), Pair(6,2), Pair(6,3), Pair(6,4), Pair(6,5),
        Pair(5,6), Pair(4,6), Pair(3,6), Pair(2,6), Pair(1,6), Pair(0,6),
        Pair(0,7),
        Pair(0,8),
        Pair(1,8), Pair(2,8), Pair(3,8), Pair(4,8), Pair(5,8),
        Pair(6,9), Pair(6,10), Pair(6,11), Pair(6,12), Pair(6,13), Pair(6,14),
        Pair(7,14),
        Pair(8,14),
        Pair(8,13), Pair(8,12), Pair(8,11), Pair(8,10), Pair(8,9),
        Pair(9,8), Pair(10,8), Pair(11,8), Pair(12,8), Pair(13,8), Pair(14,8),
        Pair(14,7),
        Pair(14,6),
        Pair(13,6), Pair(12,6), Pair(11,6), Pair(10,6), Pair(9,6),
        Pair(8,5), Pair(8,4), Pair(8,3), Pair(8,2), Pair(8,1), Pair(8,0),
        Pair(7,0),
        Pair(6,0)
    )

    val HOME_COLUMN_RED: List<Pair<Int, Int>> = listOf(Pair(7,1), Pair(7,2), Pair(7,3), Pair(7,4), Pair(7,5), Pair(7,6))
    val HOME_COLUMN_GREEN: List<Pair<Int, Int>> = listOf(Pair(1,7), Pair(2,7), Pair(3,7), Pair(4,7), Pair(5,7), Pair(6,7))
    val HOME_COLUMN_YELLOW: List<Pair<Int, Int>> = listOf(Pair(7,13), Pair(7,12), Pair(7,11), Pair(7,10), Pair(7,9), Pair(7,8))
    val HOME_COLUMN_BLUE: List<Pair<Int, Int>> = listOf(Pair(13,7), Pair(12,7), Pair(11,7), Pair(10,7), Pair(9,7), Pair(8,7))

    val CENTER_CELL = Pair(7, 7)

    // playerIndex 0=P1(Red,TL) 1=P2(Green,TR) 2=P3(Yellow,BR) 3=P4(Blue,BL)
    // P1 & P3 are now diagonal partners; P2 & P4 are diagonal partners.
    val START_OFFSETS = mapOf(0 to 0, 1 to 13, 2 to 26, 3 to 39)

    val SAFE_SQUARES = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    fun homeColumnFor(playerIndex: Int): List<Pair<Int, Int>> = when (playerIndex) {
        0 -> HOME_COLUMN_RED
        1 -> HOME_COLUMN_GREEN
        2 -> HOME_COLUMN_YELLOW
        3 -> HOME_COLUMN_BLUE
        else -> HOME_COLUMN_RED
    }

    fun yardSlotFraction(tokenIndex: Int): Pair<Float, Float> = when (tokenIndex) {
        0 -> Pair(0.28f, 0.28f)
        1 -> Pair(0.72f, 0.28f)
        2 -> Pair(0.28f, 0.72f)
        else -> Pair(0.72f, 0.72f)
    }

    fun localPositionToCell(playerIndex: Int, localPos: Int): Pair<Int, Int> {
        return when {
            localPos in 0..50 -> {
                val globalIndex = (START_OFFSETS[playerIndex]!! + localPos) % 52
                RING_PATH[globalIndex]
            }
            localPos in 51..56 -> homeColumnFor(playerIndex)[localPos - 51]
            else -> CENTER_CELL
        }
    }
}