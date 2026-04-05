package koolchess.chess

import kotlin.math.abs

/**
 * Standard chess position. Squares: a1=0 … h8=63 (rank 1 white bottom = indices 0–7).
 */
class ChessGameState private constructor(
    val squares: IntArray,
    val whiteToMove: Boolean,
    val epSquare: Int,
    /** bit 0 W-K, 1 W-Q, 2 B-K, 3 B-Q */
    val castleRights: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {

    fun copy(
        squares: IntArray = this.squares.copyOf(),
        whiteToMove: Boolean = this.whiteToMove,
        epSquare: Int = this.epSquare,
        castleRights: Int = this.castleRights,
        halfmoveClock: Int = this.halfmoveClock,
        fullmoveNumber: Int = this.fullmoveNumber,
    ) = ChessGameState(squares, whiteToMove, epSquare, castleRights, halfmoveClock, fullmoveNumber)

    fun pieceAt(sq: Int): Int = squares[sq]

    fun findKing(white: Boolean): Int {
        val k = if (white) Piece.KING else -Piece.KING
        for (i in 0..63) if (squares[i] == k) return i
        return -1
    }

    fun inCheck(forWhite: Boolean): Boolean {
        val ksq = findKing(forWhite)
        if (ksq < 0) return false
        return isSquareAttacked(ksq, !forWhite)
    }

    fun isCheckmate(): Boolean = inCheck(whiteToMove) && legalMoves().isEmpty()
    fun isStalemate(): Boolean = !inCheck(whiteToMove) && legalMoves().isEmpty()

    fun legalMoves(): List<ChessMove> {
        val pseudo = mutableListOf<ChessMove>()
        for (sq in 0..63) {
            val p = squares[sq]
            if (p == Piece.EMPTY) continue
            if (Piece.isWhite(p) != whiteToMove) continue
            when (Piece.type(p)) {
                Piece.PAWN -> genPawnMoves(sq, p, pseudo)
                Piece.KNIGHT -> genKnightMoves(sq, p, pseudo)
                Piece.BISHOP -> genSliderMoves(sq, p, BISHOP_STEPS, pseudo)
                Piece.ROOK -> genSliderMoves(sq, p, ROOK_STEPS, pseudo)
                Piece.QUEEN -> genSliderMoves(sq, p, QUEEN_STEPS, pseudo)
                Piece.KING -> genKingMoves(sq, p, pseudo)
            }
        }
        return pseudo.filter { m ->
            val next = uncheckedApply(m)
            !next.inCheck(whiteToMove)
        }
    }

    fun apply(move: ChessMove): ChessGameState {
        require(move in legalMoves()) { "Illegal move" }
        return uncheckedApply(move)
    }

    fun uncheckedApply(move: ChessMove): ChessGameState {
        val b = squares.copyOf()
        val moving = b[move.from]
        val captured = b[move.to]
        var newEp = -1
        var newCastle = castleRights
        var newHalf = halfmoveClock + 1
        var newFull = fullmoveNumber

        if (Piece.type(moving) == Piece.PAWN || captured != Piece.EMPTY) newHalf = 0

        fun clearRookRight(rookSq: Int) {
            newCastle = when (rookSq) {
                0 -> newCastle and CR_WQ.inv()
                7 -> newCastle and CR_WK.inv()
                56 -> newCastle and CR_BQ.inv()
                63 -> newCastle and CR_BK.inv()
                else -> newCastle
            }
        }

        if (move.isCastleKingSide || move.isCastleQueenSide) {
            val rankStart = if (Piece.isWhite(moving)) 0 else 56
            val kFrom = rankStart + 4
            val kTo = if (move.isCastleKingSide) rankStart + 6 else rankStart + 2
            val rFrom = if (move.isCastleKingSide) rankStart + 7 else rankStart
            val rTo = if (move.isCastleKingSide) rankStart + 5 else rankStart + 3
            b[kFrom] = Piece.EMPTY
            b[rFrom] = Piece.EMPTY
            b[kTo] = moving
            b[rTo] = Piece.make(Piece.isWhite(moving), Piece.ROOK)
            if (Piece.isWhite(moving)) {
                newCastle = newCastle and (CR_WK or CR_WQ).inv()
            } else {
                newCastle = newCastle and (CR_BK or CR_BQ).inv()
            }
        } else {
            b[move.from] = Piece.EMPTY
            if (move.isEnPassant) {
                val capSq = move.to + if (Piece.isWhite(moving)) -8 else 8
                b[capSq] = Piece.EMPTY
            }
            var place = moving
            if (Piece.type(moving) == Piece.PAWN) {
                val tr = move.to / 8
                if (tr == 0 || tr == 7) {
                    val pr = if (move.promotion == Piece.EMPTY) Piece.QUEEN else move.promotion
                    place = Piece.make(Piece.isWhite(moving), pr)
                }
                val dr = move.to - move.from
                if (abs(dr) == 16) newEp = (move.from + move.to) / 2
            }
            b[move.to] = place
        }

        if (Piece.type(moving) == Piece.KING) {
            if (Piece.isWhite(moving)) newCastle = newCastle and (CR_WK or CR_WQ).inv()
            else newCastle = newCastle and (CR_BK or CR_BQ).inv()
        }
        if (Piece.type(moving) == Piece.ROOK) clearRookRight(move.from)
        if (captured != Piece.EMPTY) {
            if (Piece.type(captured) == Piece.ROOK) clearRookRight(move.to)
        }

        if (!whiteToMove) newFull = fullmoveNumber + 1

        return ChessGameState(
            b,
            !whiteToMove,
            newEp,
            newCastle,
            newHalf,
            newFull,
        )
    }

    private fun genPawnMoves(sq: Int, p: Int, out: MutableList<ChessMove>) {
        val white = Piece.isWhite(p)
        val dir = if (white) 8 else -8
        val startRank = if (white) 1 else 6
        val tr = sq / 8

        val one = sq + dir
        if (one in 0..63 && squares[one] == Piece.EMPTY) {
            if (one / 8 == 0 || one / 8 == 7) addPromotions(sq, one, out)
            else out += ChessMove(sq, one)
            val two = sq + 2 * dir
            if (sq / 8 == startRank && squares[two] == Piece.EMPTY) {
                out += ChessMove(sq, two)
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val cap = sq + dir + df
            if (cap !in 0..63 || cap / 8 != tr + if (white) 1 else -1) continue
            if (squares[cap] != Piece.EMPTY && Piece.isWhite(squares[cap]) != white) {
                if (cap / 8 == 0 || cap / 8 == 7) addPromotions(sq, cap, out)
                else out += ChessMove(sq, cap)
            } else if (cap == epSquare && squares[cap] == Piece.EMPTY) {
                out += ChessMove(sq, cap, isEnPassant = true)
            }
        }
    }

    private fun addPromotions(from: Int, to: Int, out: MutableList<ChessMove>) {
        for (pr in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
            out += ChessMove(from, to, promotion = pr)
        }
    }

    private fun genKnightMoves(sq: Int, p: Int, out: MutableList<ChessMove>) {
        val white = Piece.isWhite(p)
        val f0 = sq % 8
        val r0 = sq / 8
        for (st in KNIGHT_STEPS) {
            val nf = f0 + st[0]
            val nr = r0 + st[1]
            if (nf !in 0..7 || nr !in 0..7) continue
            val t = nr * 8 + nf
            val occ = squares[t]
            if (occ == Piece.EMPTY || Piece.isWhite(occ) != white) out += ChessMove(sq, t)
        }
    }

    private fun genSliderMoves(sq: Int, p: Int, steps: Array<IntArray>, out: MutableList<ChessMove>) {
        val white = Piece.isWhite(p)
        val f0 = sq % 8
        val r0 = sq / 8
        for (st in steps) {
            var f = f0
            var r = r0
            while (true) {
                f += st[0]
                r += st[1]
                if (f !in 0..7 || r !in 0..7) break
                val t = r * 8 + f
                val occ = squares[t]
                if (occ == Piece.EMPTY) {
                    out += ChessMove(sq, t)
                    continue
                }
                if (Piece.isWhite(occ) != white) out += ChessMove(sq, t)
                break
            }
        }
    }

    private fun genKingMoves(sq: Int, p: Int, out: MutableList<ChessMove>) {
        val white = Piece.isWhite(p)
        val f0 = sq % 8
        val r0 = sq / 8
        for (st in KING_STEPS) {
            val nf = f0 + st[0]
            val nr = r0 + st[1]
            if (nf !in 0..7 || nr !in 0..7) continue
            val t = nr * 8 + nf
            val occ = squares[t]
            if (occ == Piece.EMPTY || Piece.isWhite(occ) != white) out += ChessMove(sq, t)
        }
        if (!inCheck(white)) tryCastle(white, out)
    }

    private fun tryCastle(white: Boolean, out: MutableList<ChessMove>) {
        val rank = if (white) 0 else 56
        val kSq = rank + 4
        if (squares[kSq] != Piece.make(white, Piece.KING)) return
        if (white) {
            if ((castleRights and CR_WK) != 0 &&
                squares[rank + 5] == Piece.EMPTY && squares[rank + 6] == Piece.EMPTY &&
                squares[rank + 7] == Piece.make(true, Piece.ROOK)
            ) {
                if (!isSquareAttacked(rank + 5, !white) && !isSquareAttacked(rank + 6, !white)) {
                    out += ChessMove(kSq, rank + 6, isCastleKingSide = true)
                }
            }
            if ((castleRights and CR_WQ) != 0 &&
                squares[rank + 1] == Piece.EMPTY && squares[rank + 2] == Piece.EMPTY && squares[rank + 3] == Piece.EMPTY &&
                squares[rank] == Piece.make(true, Piece.ROOK)
            ) {
                if (!isSquareAttacked(rank + 3, !white) && !isSquareAttacked(rank + 2, !white)) {
                    out += ChessMove(kSq, rank + 2, isCastleQueenSide = true)
                }
            }
        } else {
            if ((castleRights and CR_BK) != 0 &&
                squares[rank + 5] == Piece.EMPTY && squares[rank + 6] == Piece.EMPTY &&
                squares[rank + 7] == Piece.make(false, Piece.ROOK)
            ) {
                if (!isSquareAttacked(rank + 5, !white) && !isSquareAttacked(rank + 6, !white)) {
                    out += ChessMove(kSq, rank + 6, isCastleKingSide = true)
                }
            }
            if ((castleRights and CR_BQ) != 0 &&
                squares[rank + 1] == Piece.EMPTY && squares[rank + 2] == Piece.EMPTY && squares[rank + 3] == Piece.EMPTY &&
                squares[rank] == Piece.make(false, Piece.ROOK)
            ) {
                if (!isSquareAttacked(rank + 3, !white) && !isSquareAttacked(rank + 2, !white)) {
                    out += ChessMove(kSq, rank + 2, isCastleQueenSide = true)
                }
            }
        }
    }

    fun isSquareAttacked(target: Int, byWhite: Boolean): Boolean {
        val tf = target % 8
        val tr = target / 8
        if (byWhite) {
            for (df in intArrayOf(-1, 1)) {
                val r = tr - 1
                val f = tf + df
                if (r < 0 || f !in 0..7) continue
                val pc = squares[r * 8 + f]
                if (pc == Piece.make(true, Piece.PAWN)) return true
            }
        } else {
            for (df in intArrayOf(-1, 1)) {
                val r = tr + 1
                val f = tf + df
                if (r > 7 || f !in 0..7) continue
                val pc = squares[r * 8 + f]
                if (pc == Piece.make(false, Piece.PAWN)) return true
            }
        }
        for (st in KNIGHT_STEPS) {
            val f = tf + st[0]
            val r = tr + st[1]
            if (f !in 0..7 || r !in 0..7) continue
            val pc = squares[r * 8 + f]
            if (pc != Piece.EMPTY && Piece.isWhite(pc) == byWhite && Piece.type(pc) == Piece.KNIGHT) return true
        }
        for (st in KING_STEPS) {
            val f = tf + st[0]
            val r = tr + st[1]
            if (f !in 0..7 || r !in 0..7) continue
            val pc = squares[r * 8 + f]
            if (pc != Piece.EMPTY && Piece.isWhite(pc) == byWhite && Piece.type(pc) == Piece.KING) return true
        }
        for (st in ROOK_STEPS) {
            var f = tf
            var r = tr
            while (true) {
                f += st[0]
                r += st[1]
                if (f !in 0..7 || r !in 0..7) break
                val pc = squares[r * 8 + f]
                if (pc == Piece.EMPTY) continue
                if (Piece.isWhite(pc) == byWhite) {
                    val t = Piece.type(pc)
                    if (t == Piece.ROOK || t == Piece.QUEEN) return true
                }
                break
            }
        }
        for (st in BISHOP_STEPS) {
            var f = tf
            var r = tr
            while (true) {
                f += st[0]
                r += st[1]
                if (f !in 0..7 || r !in 0..7) break
                val pc = squares[r * 8 + f]
                if (pc == Piece.EMPTY) continue
                if (Piece.isWhite(pc) == byWhite) {
                    val t = Piece.type(pc)
                    if (t == Piece.BISHOP || t == Piece.QUEEN) return true
                }
                break
            }
        }
        return false
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = squares[r * 8 + f]
                if (p == Piece.EMPTY) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(Piece.symbol(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (whiteToMove) 'w' else 'b')
        sb.append(' ')
        if (castleRights == 0) sb.append('-') else {
            if ((castleRights and CR_WK) != 0) sb.append('K')
            if ((castleRights and CR_WQ) != 0) sb.append('Q')
            if ((castleRights and CR_BK) != 0) sb.append('k')
            if ((castleRights and CR_BQ) != 0) sb.append('q')
        }
        sb.append(' ')
        sb.append(if (epSquare >= 0) squareToAlgebraic(epSquare) else '-')
        sb.append(' ')
        sb.append(halfmoveClock)
        sb.append(' ')
        sb.append(fullmoveNumber)
        return sb.toString()
    }

    companion object {
        const val CR_WK = 1
        const val CR_WQ = 2
        const val CR_BK = 4
        const val CR_BQ = 8

        private val KNIGHT_STEPS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        private val ROOK_STEPS = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
        )
        private val BISHOP_STEPS = arrayOf(
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1),
        )
        private val QUEEN_STEPS = ROOK_STEPS + BISHOP_STEPS
        private val KING_STEPS = QUEEN_STEPS

        fun start(): ChessGameState {
            val b = IntArray(64)
            val w = true
            val bl = false
            b[0] = Piece.make(w, Piece.ROOK); b[1] = Piece.make(w, Piece.KNIGHT)
            b[2] = Piece.make(w, Piece.BISHOP); b[3] = Piece.make(w, Piece.QUEEN)
            b[4] = Piece.make(w, Piece.KING); b[5] = Piece.make(w, Piece.BISHOP)
            b[6] = Piece.make(w, Piece.KNIGHT); b[7] = Piece.make(w, Piece.ROOK)
            for (f in 0..7) b[8 + f] = Piece.make(w, Piece.PAWN)
            for (f in 0..7) b[48 + f] = Piece.make(bl, Piece.PAWN)
            b[56] = Piece.make(bl, Piece.ROOK); b[57] = Piece.make(bl, Piece.KNIGHT)
            b[58] = Piece.make(bl, Piece.BISHOP); b[59] = Piece.make(bl, Piece.QUEEN)
            b[60] = Piece.make(bl, Piece.KING); b[61] = Piece.make(bl, Piece.BISHOP)
            b[62] = Piece.make(bl, Piece.KNIGHT); b[63] = Piece.make(bl, Piece.ROOK)
            return ChessGameState(b, true, -1, CR_WK or CR_WQ or CR_BK or CR_BQ, 0, 1)
        }

        fun fromFen(fen: String): ChessGameState? {
            val parts = fen.trim().split("\\s+".toRegex())
            if (parts.size < 4) return null
            val rows = parts[0].split('/')
            if (rows.size != 8) return null
            val b = IntArray(64)
            for (r in 0..7) {
                var f = 0
                for (ch in rows[7 - r]) {
                    when {
                        ch.isDigit() -> f += ch.digitToInt()
                        else -> {
                            if (f > 7) return null
                            val white = ch.isUpperCase()
                            val t = when (ch.lowercaseChar()) {
                                'p' -> Piece.PAWN
                                'n' -> Piece.KNIGHT
                                'b' -> Piece.BISHOP
                                'r' -> Piece.ROOK
                                'q' -> Piece.QUEEN
                                'k' -> Piece.KING
                                else -> return null
                            }
                            b[r * 8 + f] = Piece.make(white, t)
                            f++
                        }
                    }
                }
                if (f != 8) return null
            }
            val whiteToMove = parts[1] == "w"
            var cr = 0
            if (parts[2] != "-") {
                for (ch in parts[2]) {
                    when (ch) {
                        'K' -> cr = cr or CR_WK
                        'Q' -> cr = cr or CR_WQ
                        'k' -> cr = cr or CR_BK
                        'q' -> cr = cr or CR_BQ
                    }
                }
            }
            val ep = if (parts[3] == "-" || parts[3].length < 2) -1 else algebraicToSquare(parts[3]) ?: -1
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            return ChessGameState(b, whiteToMove, ep, cr, half, full)
        }
    }
}
