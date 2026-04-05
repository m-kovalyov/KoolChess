package koolchess.chess

data class ChessMove(
    val from: Int,
    val to: Int,
    /** Set only for pawn promotions; [Piece.EMPTY] means none. */
    val promotion: Int = Piece.EMPTY,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
) {
    fun uci(): String {
        val f = squareToAlgebraic(from)
        val t = squareToAlgebraic(to)
        val pr = when (promotion) {
            Piece.QUEEN -> "q"
            Piece.ROOK -> "r"
            Piece.BISHOP -> "b"
            Piece.KNIGHT -> "n"
            else -> ""
        }
        return f + t + pr
    }
}

fun squareToAlgebraic(sq: Int): String {
    val f = sq % 8
    val r = sq / 8
    return "${'a' + f}${r + 1}"
}

fun algebraicToSquare(a: String): Int? {
    if (a.length < 2) return null
    val f = a[0].lowercaseChar().code - 'a'.code
    val r = a[1].digitToIntOrNull()?.minus(1) ?: return null
    if (f !in 0..7 || r !in 0..7) return null
    return r * 8 + f
}
