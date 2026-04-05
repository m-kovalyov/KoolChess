package koolchess

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorLoad
import de.fabmax.kool.util.MdColor

class FileBridge(
    val openJson: () -> ByteArray?,
    val saveJson: (ByteArray) -> Unit,
)

private fun panelColors(mode: UiThemeMode): Colors = when (mode) {
    UiThemeMode.DARK -> Colors.singleColorDark(MdColor.LIGHT_BLUE)
    UiThemeMode.LIGHT -> Colors.singleColorLight(MdColor.INDIGO)
}

fun chessUiScene(ctx: KoolContext, ctl: GameController, files: FileBridge) = UiScene(
    name = "ChessUi",
    clearColor = ClearColorLoad,
) {
    addPanelSurface(
        colors = panelColors(ctl.uiTheme.value),
        backgroundColor = { null },
    ) {
        ctl.uiTheme.use(surface)
        Box(Grow.Std, Grow.Std) {
            modifier.isBlocking(false)
            val scr = ctl.screen.use(surface)

            when (scr) {
                AppScreen.PLAYING -> {
                    Row(width = FitContent, height = FitContent) {
                        modifier
                            .align(AlignmentX.Center, AlignmentY.Top)
                            .margin(top = 16.dp, start = 20.dp, end = 20.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .background(RoundRectBackground(colors.backgroundVariant, 14.dp))

                        Text("${ctl.whiteName.use(surface)}  ${formatClock(ctl.whiteRemaining.use(surface))}") {
                            modifier
                                .margin(end = 16.dp)
                                .alignY(AlignmentY.Center)
                                .font(sizes.normalText)
                        }
                        divider(verticalMargin = 6.dp, marginStart = 4.dp, marginEnd = 4.dp)
                        Text("${ctl.blackName.use(surface)}  ${formatClock(ctl.blackRemaining.use(surface))}") {
                            modifier
                                .margin(end = 18.dp)
                                .alignY(AlignmentY.Center)
                                .font(sizes.normalText)
                        }
                        val st = ctl.statusLine.use(surface)
                        if (st.isNotBlank()) {
                            Text(st) {
                                modifier
                                    .margin(end = 14.dp)
                                    .alignY(AlignmentY.Center)
                                    .font(sizes.smallText)
                            }
                        }
                        Button("Пауза") {
                            modifier
                                .alignY(AlignmentY.Center)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .onClick { ctl.pause() }
                        }
                    }
                }

                else -> {
                    Column(width = 400.dp, height = FitContent) {
                        modifier
                            .align(AlignmentX.Center, AlignmentY.Center)
                            .padding(28.dp)
                            .background(RoundRectBackground(colors.background, 22.dp))

                        when (scr) {
                            AppScreen.MAIN_MENU -> {
                                menuTitle("KoolChess", "Шахматы на Kool Engine")
                                menuButton("Загрузить сохранение") {
                                    files.openJson()?.let { bytes ->
                                        ChessSaveV1.fromJson(bytes.decodeToString())?.let { ctl.loadSave(it) }
                                    }
                                }
                                menuButton("Новая партия") { ctl.beginNewGameFlow() }
                            }

                            AppScreen.NEW_GAME_TIME -> {
                                menuTitle("Время на партию", "Для каждой стороны")
                                listOf(60 to "1 минута", 300 to "5 минут", 600 to "10 минут", 1800 to "30 минут").forEach { (sec, label) ->
                                    menuButton(label) { ctl.pickTime(sec) }
                                }
                                menuSpacer()
                                menuButtonSecondary("Назад") { ctl.resetMenu() }
                            }

                            AppScreen.NEW_GAME_MODE -> {
                                menuTitle("Режим", "Выберите тип партии")
                                menuButton("Один — против компьютера") { ctl.pickMode(true) }
                                menuButton("Двое — на одном экране") { ctl.pickMode(false) }
                                menuSpacer()
                                menuButtonSecondary("Назад") { ctl.screen.set(AppScreen.NEW_GAME_TIME) }
                            }

                            AppScreen.NEW_GAME_ELO -> {
                                menuTitle("Сложность", "Номинальный Elo ИИ")
                                listOf(700, 1000, 1500).forEach { elo ->
                                    menuButton("$elo") { ctl.pickElo(elo) }
                                }
                                menuSpacer()
                                menuButtonSecondary("Назад") { ctl.screen.set(AppScreen.NEW_GAME_MODE) }
                            }

                            AppScreen.NEW_GAME_NAMES -> {
                                menuTitle("Игроки", "Цвет фигур выберется случайно")
                                Text("Игрок 1") {
                                    modifier
                                        .margin(top = 8.dp, bottom = 4.dp)
                                        .font(sizes.smallText)
                                        .alignX(AlignmentX.Start)
                                }
                                TextField(ctl.nameDraftWhite.use(surface)) {
                                    modifier
                                        .width(340.dp)
                                        .margin(bottom = 12.dp)
                                        .onChange { ctl.nameDraftWhite.set(it) }
                                }
                                Text("Игрок 2") {
                                    modifier
                                        .margin(bottom = 4.dp)
                                        .font(sizes.smallText)
                                        .alignX(AlignmentX.Start)
                                }
                                TextField(ctl.nameDraftBlack.use(surface)) {
                                    modifier
                                        .width(340.dp)
                                        .margin(bottom = 16.dp)
                                        .onChange { ctl.nameDraftBlack.set(it) }
                                }
                                menuButton("Начать партию") { ctl.submitHotseatNames() }
                                menuButtonSecondary("Назад") { ctl.screen.set(AppScreen.NEW_GAME_MODE) }
                            }

                            AppScreen.PAUSED -> {
                                menuTitle("Пауза", null)
                                menuButton("Продолжить") { ctl.resume() }
                                menuButton("Сохранить партию") {
                                    files.saveJson(ctl.buildSave().toJson().encodeToByteArray())
                                }
                                menuButton("Загрузить партию") {
                                    files.openJson()?.let { bytes ->
                                        ChessSaveV1.fromJson(bytes.decodeToString())?.let {
                                            ctl.loadSave(it)
                                            ctl.resume()
                                        }
                                    }
                                }
                                menuButton("Фон: светлый / тёмный") { ctl.toggleUiTheme() }
                                menuButton("Доска: классика / розовая") { ctl.toggleBoardPalette() }
                                menuButton("Выход (с автосохранением)") {
                                    ctl.exitWithAutosave { files.saveJson(it.toJson().encodeToByteArray()) }
                                }
                            }

                            AppScreen.GAME_OVER -> {
                                menuTitle("Партия окончена", null)
                                Text(ctl.gameOverReason.use(surface)) {
                                    modifier
                                        .margin(bottom = 20.dp)
                                        .alignX(AlignmentX.Center)
                                        .font(sizes.normalText)
                                }
                                menuButton("В меню") { ctl.resetMenu() }
                            }

                            AppScreen.PLAYING -> { }
                        }
                    }
                }
            }
        }
    }.apply {
        // Default CaptureInsideBounds: full-screen Panel root Box is isBlocking=true, so the UI always
        // "covers" the viewport and orbit camera / 3D picking never see the pointer.
        inputMode = UiSurface.InputCaptureMode.CaptureOverBackground
    }
}

private fun ColumnScope.menuTitle(title: String, subtitle: String?) {
    Text(title) {
        modifier
            .margin(bottom = if (subtitle != null) 6.dp else 18.dp)
            .alignX(AlignmentX.Center)
            .font(sizes.largeText)
    }
    if (subtitle != null) {
        Text(subtitle) {
            modifier
                .margin(bottom = 20.dp)
                .alignX(AlignmentX.Center)
                .font(sizes.smallText)
        }
    }
}

private fun ColumnScope.menuSpacer() {
    Box(FitContent, 12.dp) { }
}

private fun ColumnScope.menuButton(label: String, onClick: () -> Unit) {
    Button(label) {
        modifier
            .width(320.dp)
            .margin(bottom = 10.dp)
            .alignX(AlignmentX.Center)
            .padding(vertical = 8.dp)
            .onClick { onClick() }
    }
}

private fun ColumnScope.menuButtonSecondary(label: String, onClick: () -> Unit) {
    Button(label) {
        modifier
            .width(220.dp)
            .margin(top = 8.dp)
            .alignX(AlignmentX.Center)
            .font(sizes.smallText)
            .onClick { onClick() }
    }
}

private fun formatClock(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}
