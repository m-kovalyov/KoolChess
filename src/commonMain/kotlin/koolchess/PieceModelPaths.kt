package koolchess

/**
 * Asset paths under `src/commonMain/resources/` for glTF 2.0 piece models.
 * Drop your `.glb` / `.gltf` files here and adjust names if needed.
 *
 * Expected layout (example):
 * `resources/assets/models/w_pawn.glb`, `b_queen.glb`, …
 */
object PieceModelPaths {
    const val BASE = "assets/models"

    /**
     * Uniform scale for glTF meshes under each piece node (models are often authored in arbitrary units).
     * Adjust if pieces look too large or small on the board.
     */
    const val GLTF_UNIFORM_SCALE = 0.4f

    fun path(white: Boolean, type: Int): String {
        val c = if (white) "w" else "b"
        val p = when (type) {
            koolchess.chess.Piece.PAWN -> "pawn"
            koolchess.chess.Piece.KNIGHT -> "knight"
            koolchess.chess.Piece.BISHOP -> "bishop"
            koolchess.chess.Piece.ROOK -> "rook"
            koolchess.chess.Piece.QUEEN -> "queen"
            koolchess.chess.Piece.KING -> "king"
            else -> "pawn"
        }
        return "$BASE/${c}_$p.glb"
    }
}
