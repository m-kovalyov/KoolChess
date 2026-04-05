package koolchess

import de.fabmax.kool.modules.ui2.mutableStateOf
import koolchess.chess.ChessEngine
import koolchess.chess.ChessGameState
import koolchess.chess.ChessMove
import koolchess.chess.Piece
import kotlin.random.Random as KRandom

enum class AppScreen {
    MAIN_MENU,
    NEW_GAME_TIME,
    NEW_GAME_MODE,
    NEW_GAME_ELO,
    NEW_GAME_NAMES,
    PLAYING,
    PAUSED,
    GAME_OVER,
}

class GameController {

    val screen = mutableStateOf(AppScreen.MAIN_MENU)
    val uiTheme = mutableStateOf(UiThemeMode.DARK)
    val boardPalette = mutableStateOf(BoardPalette.CLASSIC)

    val whiteName = mutableStateOf("Белые")
    val blackName = mutableStateOf("Чёрные")
    val nameDraftWhite = mutableStateOf("")
    val nameDraftBlack = mutableStateOf("")

    val vsAi = mutableStateOf(false)
    val aiElo = mutableStateOf(1000)
    val humanPlaysWhite = mutableStateOf(true)

    val totalSecondsPerSide = mutableStateOf(300.0)
    val whiteRemaining = mutableStateOf(300.0)
    val blackRemaining = mutableStateOf(300.0)

    val gameState = mutableStateOf(ChessGameState.start())
    val paused = mutableStateOf(false)
    val gameOverReason = mutableStateOf("")

    val selectedSquare = mutableStateOf<Int?>(null)
    val legalTargets = mutableStateOf<Set<Int>>(emptySet())
    val boardRevision = mutableStateOf(0)

    val statusLine = mutableStateOf("")

    fun resetMenu() {
        screen.set(AppScreen.MAIN_MENU)
        paused.set(false)
        selectedSquare.set(null)
        legalTargets.set(emptySet())
    }

    fun beginNewGameFlow() {
        screen.set(AppScreen.NEW_GAME_TIME)
    }

    fun pickTime(totalSec: Int) {
        totalSecondsPerSide.set(totalSec.toDouble())
        whiteRemaining.set(totalSec.toDouble())
        blackRemaining.set(totalSec.toDouble())
        screen.set(AppScreen.NEW_GAME_MODE)
    }

    fun pickMode(ai: Boolean) {
        vsAi.set(ai)
        if (ai) screen.set(AppScreen.NEW_GAME_ELO)
        else screen.set(AppScreen.NEW_GAME_NAMES)
    }

    fun pickElo(elo: Int) {
        aiElo.set(elo)
        humanPlaysWhite.set(KRandom.nextBoolean())
        whiteName.set(if (humanPlaysWhite.value) "Вы" else "Компьютер")
        blackName.set(if (humanPlaysWhite.value) "Компьютер" else "Вы")
        startPlaying()
    }

    fun submitHotseatNames() {
        val pa = nameDraftWhite.value.ifBlank { "Игрок 1" }
        val pb = nameDraftBlack.value.ifBlank { "Игрок 2" }
        if (KRandom.nextBoolean()) {
            whiteName.set(pa)
            blackName.set(pb)
        } else {
            whiteName.set(pb)
            blackName.set(pa)
        }
        vsAi.set(false)
        startPlaying()
    }

    private fun startPlaying() {
        gameState.set(ChessGameState.start())
        val t = totalSecondsPerSide.value
        whiteRemaining.set(t)
        blackRemaining.set(t)
        paused.set(false)
        selectedSquare.set(null)
        legalTargets.set(emptySet())
        gameOverReason.set("")
        screen.set(AppScreen.PLAYING)
        boardRevision.set(boardRevision.value + 1)
        updateStatus()
        maybeRunAi()
    }

    fun loadSave(data: ChessSaveV1) {
        val st = ChessGameState.fromFen(data.fen) ?: return
        gameState.set(st)
        whiteRemaining.set(data.whiteRemainingSec)
        blackRemaining.set(data.blackRemainingSec)
        vsAi.set(data.vsAi)
        aiElo.set(data.aiElo)
        humanPlaysWhite.set(data.humanPlaysWhite)
        whiteName.set(data.whitePlayerName)
        blackName.set(data.blackPlayerName)
        boardPalette.set(
            runCatching { BoardPalette.valueOf(data.boardPalette) }.getOrElse { BoardPalette.CLASSIC },
        )
        uiTheme.set(
            runCatching { UiThemeMode.valueOf(data.uiTheme) }.getOrElse { UiThemeMode.DARK },
        )
        paused.set(false)
        selectedSquare.set(null)
        legalTargets.set(emptySet())
        gameOverReason.set("")
        screen.set(AppScreen.PLAYING)
        boardRevision.set(boardRevision.value + 1)
        updateStatus()
        maybeRunAi()
    }

    fun buildSave(): ChessSaveV1 = ChessSaveV1.fromState(
        state = gameState.value,
        whiteSec = whiteRemaining.value,
        blackSec = blackRemaining.value,
        vsAi = vsAi.value,
        aiElo = aiElo.value,
        humanPlaysWhite = humanPlaysWhite.value,
        whiteName = whiteName.value,
        blackName = blackName.value,
        palette = boardPalette.value,
        ui = uiTheme.value,
    )

    fun pause() {
        if (screen.value != AppScreen.PLAYING) return
        paused.set(true)
        screen.set(AppScreen.PAUSED)
    }

    fun resume() {
        paused.set(false)
        screen.set(AppScreen.PLAYING)
    }

    fun exitWithAutosave(saveSink: (ChessSaveV1) -> Unit) {
        if (screen.value == AppScreen.PLAYING || screen.value == AppScreen.PAUSED) {
            saveSink(buildSave())
        }
        resetMenu()
    }

    fun toggleUiTheme() {
        uiTheme.set(
            if (uiTheme.value == UiThemeMode.DARK) UiThemeMode.LIGHT else UiThemeMode.DARK,
        )
    }

    fun toggleBoardPalette() {
        boardPalette.set(
            if (boardPalette.value == BoardPalette.CLASSIC) BoardPalette.PINK else BoardPalette.CLASSIC,
        )
        boardRevision.set(boardRevision.value + 1)
    }

    fun tick(deltaSec: Float) {
        if (screen.value != AppScreen.PLAYING || paused.value) return
        val st = gameState.value
        if (st.isCheckmate() || st.isStalemate()) return
        if (st.whiteToMove) {
            whiteRemaining.set((whiteRemaining.value - deltaSec).coerceAtLeast(0.0))
            if (whiteRemaining.value <= 0) endByTime()
        } else {
            blackRemaining.set((blackRemaining.value - deltaSec).coerceAtLeast(0.0))
            if (blackRemaining.value <= 0) endByTime()
        }
    }

    private fun endByTime() {
        val wtm = gameState.value.whiteToMove
        gameOverReason.set(
            if (wtm) "Время белых истекло" else "Время чёрных истекло",
        )
        screen.set(AppScreen.GAME_OVER)
    }

    private fun endGame(msg: String) {
        gameOverReason.set(msg)
        screen.set(AppScreen.GAME_OVER)
    }

    private fun updateStatus() {
        val s = gameState.value
        statusLine.set(
            when {
                s.isCheckmate() -> "Мат!"
                s.isStalemate() -> "Пат"
                s.inCheck(s.whiteToMove) -> "Шах"
                else -> ""
            },
        )
        if (s.isCheckmate()) {
            val winner = if (s.whiteToMove) blackName.value else whiteName.value
            endGame("Победитель: $winner")
        } else if (s.isStalemate()) {
            endGame("Ничья (пат)")
        }
    }

    fun isHumanTurn(): Boolean {
        val s = gameState.value
        return if (vsAi.value) {
            (s.whiteToMove && humanPlaysWhite.value) || (!s.whiteToMove && !humanPlaysWhite.value)
        } else {
            true
        }
    }

    fun onSquareClicked(sq: Int) {
        if (screen.value != AppScreen.PLAYING || paused.value) return
        if (!isHumanTurn()) return
        val s = gameState.value
        if (s.isCheckmate() || s.isStalemate()) return

        val piece = s.pieceAt(sq)
        val sel = selectedSquare.value

        if (sel != null && sq in legalTargets.value) {
            val moves = s.legalMoves().filter { it.from == sel && it.to == sq }
            val move = moves.find { it.promotion == Piece.QUEEN } ?: moves.firstOrNull() ?: return
            applyHumanMove(move)
            return
        }

        if (piece != Piece.EMPTY && Piece.isWhite(piece) == s.whiteToMove) {
            selectedSquare.set(sq)
            legalTargets.set(
                s.legalMoves().filter { it.from == sq }.map { it.to }.toSet(),
            )
            return
        }

        selectedSquare.set(null)
        legalTargets.set(emptySet())
    }

    private fun applyHumanMove(move: ChessMove) {
        val next = gameState.value.apply(move)
        gameState.set(next)
        selectedSquare.set(null)
        legalTargets.set(emptySet())
        boardRevision.set(boardRevision.value + 1)
        updateStatus()
        maybeRunAi()
    }

    fun maybeRunAi() {
        val s = gameState.value
        if (!vsAi.value || screen.value != AppScreen.PLAYING) return
        if (s.isCheckmate() || s.isStalemate()) return
        if (isHumanTurn()) return
        val m = ChessEngine.searchBestMove(s, aiElo.value) ?: return
        gameState.set(s.apply(m))
        boardRevision.set(boardRevision.value + 1)
        updateStatus()
    }
}
