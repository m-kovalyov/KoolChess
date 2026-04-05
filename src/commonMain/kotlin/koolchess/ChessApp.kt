package koolchess

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyboardInput

fun runChessApp(ctx: KoolContext, files: FileBridge) {
    val ctl = GameController()
    ctx.scenes += chessWorldScene(ctx, ctl)
    ctx.scenes += chessUiScene(ctx, ctl, files)

    KeyboardInput.addKeyListener(KeyboardInput.KEY_ESC, "koolchess-esc") { ev ->
        if (!ev.isPressed) return@addKeyListener
        when (ctl.screen.value) {
            AppScreen.PLAYING -> ctl.pause()
            AppScreen.PAUSED -> ctl.resume()
            else -> { }
        }
    }
}
