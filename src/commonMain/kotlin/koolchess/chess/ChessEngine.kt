package koolchess.chess

import kotlin.random.Random

/**
 * Alpha-beta with quiescence search and piece–square tables. Stockfish uses the same broad family of ideas
 * (alpha–beta, move ordering, pruning) plus NNUE evaluation, Syzygy tablebases, and much deeper search.
 */
object ChessEngine {

    private val rnd = Random.Default

    /** Limits capture chains in quiescence (big speed win; small eval impact). */
    private const val quiescenceMaxDepth = 5

    fun searchBestMove(state: ChessGameState, targetElo: Int): ChessMove? {
        val moves = state.legalMoves()
        if (moves.isEmpty()) return null
        val depth = when {
            targetElo < 850 -> 3
            targetElo < 1200 -> 4
            else -> 5
        }
        val whiteRoot = state.whiteToMove
        val ordered = moves.sortedByDescending { moveScore(state, it) }
        val scored = ordered.map { m ->
            m to search(state.apply(m), depth - 1, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
        }
        val bestPair = if (whiteRoot) {
            scored.maxBy { it.second }
        } else {
            scored.minBy { it.second }
        }
        var best = bestPair.first
        val bestV = bestPair.second
        if (rnd.nextFloat() < shuffleChance(targetElo)) {
            val alts = scored.filter {
                if (whiteRoot) it.second >= bestV - 75 else it.second <= bestV + 75
            }.map { it.first }
            if (alts.isNotEmpty()) best = alts[rnd.nextInt(alts.size)]
        }
        return best
    }

    private fun shuffleChance(elo: Int): Float = when {
        elo < 850 -> 0.35f
        elo < 1200 -> 0.18f
        else -> 0.08f
    }

    /** Positive = better for White. */
    private fun search(s: ChessGameState, depth: Int, a: Int, b: Int): Int {
        if (depth <= 0) return quiesce(s, a, b, quiescenceMaxDepth)
        val moves = s.legalMoves()
        if (moves.isEmpty()) {
            return if (s.inCheck(s.whiteToMove)) {
                if (s.whiteToMove) -28000 + depth else 28000 - depth
            } else {
                0
            }
        }
        if (s.whiteToMove) {
            var v = Int.MIN_VALUE
            var alpha = a
            for (m in moves.sortedByDescending { moveScore(s, it) }) {
                v = maxOf(v, search(s.apply(m), depth - 1, alpha, b))
                alpha = maxOf(alpha, v)
                if (alpha >= b) break
            }
            return v
        } else {
            var v = Int.MAX_VALUE
            var beta = b
            for (m in moves.sortedByDescending { moveScore(s, it) }) {
                v = minOf(v, search(s.apply(m), depth - 1, a, beta))
                beta = minOf(beta, v)
                if (a >= beta) break
            }
            return v
        }
    }

    private fun quiesce(s: ChessGameState, a: Int, b: Int, qDepth: Int): Int {
        val stand = evaluate(s)
        if (qDepth <= 0) return stand
        val caps = s.legalMoves()
            .filter { m -> s.pieceAt(m.to) != Piece.EMPTY || m.isEnPassant }
            .sortedByDescending { captureScore(s, it) }
        if (s.whiteToMove) {
            var v = stand
            var alpha = maxOf(a, stand)
            for (m in caps) {
                v = maxOf(v, quiesce(s.apply(m), alpha, b, qDepth - 1))
                if (v >= b) return v
                alpha = maxOf(alpha, v)
            }
            return v
        } else {
            var v = stand
            var beta = minOf(b, stand)
            for (m in caps) {
                v = minOf(v, quiesce(s.apply(m), a, beta, qDepth - 1))
                if (v <= a) return v
                beta = minOf(beta, v)
            }
            return v
        }
    }

    private fun moveScore(s: ChessGameState, m: ChessMove): Int =
        captureScore(s, m) + if (m.promotion != Piece.EMPTY) 50 else 0

    private fun captureScore(s: ChessGameState, m: ChessMove): Int {
        if (m.isEnPassant) return 100
        val v = s.pieceAt(m.to)
        if (v == Piece.EMPTY) return 0
        val attacker = Piece.type(s.pieceAt(m.from))
        val victim = Piece.type(v)
        return victim * 100 - attacker
    }

    private fun evaluate(s: ChessGameState): Int {
        var sc = 0
        for (sq in 0..63) {
            val p = s.pieceAt(sq)
            if (p == Piece.EMPTY) continue
            val t = Piece.type(p)
            val w = Piece.isWhite(p)
            val v = material(t) + pst(t, sq, w)
            sc += if (w) v else -v
        }
        return sc
    }

    private fun material(t: Int): Int = when (t) {
        Piece.PAWN -> 100
        Piece.KNIGHT -> 320
        Piece.BISHOP -> 330
        Piece.ROOK -> 500
        Piece.QUEEN -> 900
        Piece.KING -> 0
        else -> 0
    }

    private fun pst(type: Int, sq: Int, white: Boolean): Int {
        val r = sq / 8
        val f = sq % 8
        val idx = if (white) r * 8 + f else (7 - r) * 8 + f
        return when (type) {
            Piece.PAWN -> PAWN_PST[idx]
            Piece.KNIGHT -> KNIGHT_PST[idx]
            Piece.BISHOP -> BISHOP_PST[idx]
            else -> 0
        }
    }

    private val PAWN_PST = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val KNIGHT_PST = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )

    private val BISHOP_PST = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
}
