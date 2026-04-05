package koolchess

import de.fabmax.kool.util.Color

enum class UiThemeMode { LIGHT, DARK }

enum class BoardPalette { CLASSIC, PINK }

data class BoardColors(
    val lightSquare: Color,
    val darkSquare: Color,
    val highlightSelected: Color,
    val highlightLegal: Color,
)

fun boardColors(palette: BoardPalette): BoardColors = when (palette) {
    BoardPalette.CLASSIC -> BoardColors(
        lightSquare = Color("f0d9b5ff"),
        darkSquare = Color("b58863ff"),
        highlightSelected = Color("baca44ff"),
        highlightLegal = Color("6464f0aa"),
    )
    BoardPalette.PINK -> BoardColors(
        lightSquare = Color("fff0f5ff"),
        darkSquare = Color("ff69b4cc"),
        highlightSelected = Color("ffff00aa"),
        highlightLegal = Color("00fff088"),
    )
}
