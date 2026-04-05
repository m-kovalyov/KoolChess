package koolchess

import koolchess.chess.ChessGameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChessSaveV1(
    val version: Int = 1,
    val fen: String,
    val whiteRemainingSec: Double,
    val blackRemainingSec: Double,
    val vsAi: Boolean,
    val aiElo: Int = 1000,
    val humanPlaysWhite: Boolean = true,
    val whitePlayerName: String = "White",
    val blackPlayerName: String = "Black",
    val boardPalette: String = BoardPalette.CLASSIC.name,
    val uiTheme: String = UiThemeMode.DARK.name,
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun fromJson(text: String): ChessSaveV1? = runCatching {
            json.decodeFromString<ChessSaveV1>(text)
        }.getOrNull()

        fun fromState(
            state: ChessGameState,
            whiteSec: Double,
            blackSec: Double,
            vsAi: Boolean,
            aiElo: Int,
            humanPlaysWhite: Boolean,
            whiteName: String,
            blackName: String,
            palette: BoardPalette,
            ui: UiThemeMode,
        ) = ChessSaveV1(
            fen = state.toFen(),
            whiteRemainingSec = whiteSec,
            blackRemainingSec = blackSec,
            vsAi = vsAi,
            aiElo = aiElo,
            humanPlaysWhite = humanPlaysWhite,
            whitePlayerName = whiteName,
            blackPlayerName = blackName,
            boardPalette = palette.name,
            uiTheme = ui.name,
        )
    }
}
