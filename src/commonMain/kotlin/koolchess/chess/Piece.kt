package koolchess.chess

object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    fun make(white: Boolean, type: Int): Int = if (white) type else -type
    fun isWhite(p: Int): Boolean = p > 0
    fun type(p: Int): Int = kotlin.math.abs(p)
    fun symbol(p: Int): Char {
        val t = type(p)
        val c = when (t) {
            PAWN -> 'p'
            KNIGHT -> 'n'
            BISHOP -> 'b'
            ROOK -> 'r'
            QUEEN -> 'q'
            KING -> 'k'
            else -> '?'
        }
        return if (isWhite(p)) c.uppercaseChar() else c
    }
}
