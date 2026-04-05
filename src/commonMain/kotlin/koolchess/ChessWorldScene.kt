package koolchess

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.RayTest
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.*
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import koolchess.chess.Piece
import kotlinx.coroutines.launch

private const val CELL = 1f

/** Square index from board node names (mesh hits parent groups). */
private fun squareIndexFromNodeName(name: String): Int? = when {
    name.startsWith("sq_") -> name.removePrefix("sq_").toIntOrNull()
    name.startsWith("piece_") -> name.removePrefix("piece_").toIntOrNull()
    name.startsWith("hl_sel_") -> name.removePrefix("hl_sel_").toIntOrNull()
    name.startsWith("hl_leg_") -> name.removePrefix("hl_leg_").toIntOrNull()
    else -> null
}

private fun worldPos(sq: Int): Vec3f {
    val f = sq % 8
    val r = sq / 8
    val x = (f - 3.5f) * CELL
    val z = (3.5f - r) * CELL
    return Vec3f(x, 0f, z)
}

fun chessWorldScene(ctx: KoolContext, ctl: GameController): Scene = scene("ChessWorld") {
    defaultOrbitCamera().apply {
        // Left drag competes with chess clicks: PointerInput only sets isLeftButtonClicked if movement
        // stays within ~15px; orbit rotation easily exceeds that. Use RMB to orbit, MMB to pan.
        leftDragMethod = OrbitInputTransform.DragMethod.NONE
        rightDragMethod = OrbitInputTransform.DragMethod.ROTATE
        middleDragMethod = OrbitInputTransform.DragMethod.PAN
        smoothingDecay = 0.0
    }
    lighting.singleDirectionalLight {
        setup(Vec3f(-1f, -2f, -1f))
        setColor(Color.WHITE, 4f)
    }

    val boardRoot = Node("boardRoot")
    val pieceRoot = Node("pieceRoot")
    addNode(boardRoot)
    addNode(pieceRoot)

    val hlSelected = Array<Node?>(64) { null }
    val hlLegal = Array<Node?>(64) { null }
    val pieceNodes = Array<Node?>(64) { null }

    val pieceGltfCache: Array<Array<GltfFile?>> = Array(2) { arrayOfNulls(6) }
    val gltfPieceCfg = gltfPieceConfig()
    coroutineScope.launch {
        preloadPieceGltfFiles(pieceGltfCache)
        ctl.boardRevision.set(ctl.boardRevision.value + 1)
    }

    fun rebuildStatics() {
        boardRoot.clearChildren()
        val pal = boardColors(ctl.boardPalette.value)
        for (sq in 0..63) {
            val lightSq = (sq / 8 + sq % 8) % 2 == 0
            val base = if (lightSq) pal.lightSquare else pal.darkSquare
            val wp = worldPos(sq)
            boardRoot.addGroup("sq_$sq") {
                transform = TrsTransformF().apply { translation.set(wp) }
                addColorMesh {
                    generate {
                        withColor(base) {
                            cube { }
                        }
                    }
                    (transform as TrsTransformF).scale.set(CELL * 0.98f, 0.06f, CELL * 0.98f)
                    shader = KslPbrShader {
                        color { vertexColor() }
                        metallic(0f)
                        roughness(0.85f)
                    }
                }
            }
            boardRoot.addGroup("hl_sel_$sq") {
                transform = TrsTransformF().apply { translation.set(wp.x, 0.08f, wp.z) }
                addColorMesh {
                    generate {
                        withColor(pal.highlightSelected) {
                            cube { }
                        }
                    }
                    (transform as TrsTransformF).scale.set(CELL * 0.92f, 0.04f, CELL * 0.92f)
                    shader = KslPbrShader {
                        color { vertexColor() }
                        metallic(0f)
                        roughness(0.4f)
                    }
                }
                isVisible = false
            }.also { hlSelected[sq] = it }

            boardRoot.addGroup("hl_leg_$sq") {
                transform = TrsTransformF().apply { translation.set(wp.x, 0.07f, wp.z) }
                addColorMesh {
                    generate {
                        withColor(pal.highlightLegal) {
                            cube { }
                        }
                    }
                    (transform as TrsTransformF).scale.set(CELL * 0.88f, 0.03f, CELL * 0.88f)
                    shader = KslPbrShader {
                        color { vertexColor() }
                        metallic(0.1f)
                        roughness(0.35f)
                    }
                }
                isVisible = false
            }.also { hlLegal[sq] = it }
        }
    }

    rebuildStatics()

    var lastRevision = -1
    onUpdate { _: RenderPass.UpdateEvent ->
        val bg = if (ctl.uiTheme.value == UiThemeMode.DARK) {
            Color(0.07f, 0.07f, 0.09f, 1f)
        } else {
            Color(0.78f, 0.8f, 0.84f, 1f)
        }
        mainRenderPass.clearColor = ClearColorFill(bg)

        ctl.tick(Time.deltaT)
        if (ctl.boardRevision.value != lastRevision) {
            lastRevision = ctl.boardRevision.value
            rebuildStatics()
            pieceRoot.clearChildren()
            for (i in pieceNodes.indices) pieceNodes[i] = null
        }
        val sel = ctl.selectedSquare.value
        val leg = ctl.legalTargets.value
        for (sq in 0..63) {
            hlSelected[sq]?.isVisible = sq == sel
            hlLegal[sq]?.isVisible = leg.contains(sq) && sq != sel
        }
        syncPieces(this, pieceRoot, pieceNodes, ctl, pieceGltfCache, gltfPieceCfg)
        if (ctl.screen.value == AppScreen.PLAYING && !ctl.paused.value && ctl.isHumanTurn()) {
            val ptr = PointerInput.primaryPointer
            if (ptr.isLeftButtonClicked) {
                val pick = RayTest()
                if (camera.initRayTes(pick, ptr, mainRenderPass.viewport)) {
                    rayTest(pick)
                    var n: Node? = pick.hitNode
                    while (n != null) {
                        squareIndexFromNodeName(n.name)?.let { sq ->
                            ctl.onSquareClicked(sq)
                            break
                        }
                        n = n.parent
                    }
                }
            }
        }
    }
}

private fun syncPieces(
    scene: Scene,
    root: Node,
    slots: Array<Node?>,
    ctl: GameController,
    gltfCache: Array<Array<GltfFile?>>,
    gltfCfg: GltfLoadConfig,
) {
    val st = ctl.gameState.value
    val occupied = mutableSetOf<Int>()
    for (sq in 0..63) {
        val p = st.pieceAt(sq)
        if (p == Piece.EMPTY) continue
        occupied += sq
        val wp = worldPos(sq)
        val yLift = 0.35f
        val col = if (Piece.isWhite(p)) Color("e8e8ffff") else Color("303030ff")
        if (slots[sq] == null) {
            val g = Node("piece_$sq")
            val t = Piece.type(p)
            val wIdx = if (Piece.isWhite(p)) 0 else 1
            val file = gltfCache.getOrNull(wIdx)?.getOrNull(pieceGltfSlot(t))
            val model = file?.let { instantiatePieceModel(it, gltfCfg, scene) }
            if (model != null) {
                val sc = PieceModelPaths.GLTF_UNIFORM_SCALE
                (model.transform as? TrsTransformF)?.scale?.set(sc, sc, sc)
                g.addNode(model)
            } else {
                g.addColorMesh {
                    generate {
                        withColor(col) {
                            cube { }
                        }
                    }
                    val sy = when (t) {
                        Piece.PAWN -> 0.28f
                        Piece.KNIGHT -> 0.32f
                        Piece.BISHOP -> 0.34f
                        Piece.ROOK -> 0.3f
                        Piece.QUEEN -> 0.38f
                        Piece.KING -> 0.4f
                        else -> 0.3f
                    }
                    (transform as TrsTransformF).scale.set(0.55f, sy, 0.55f)
                    shader = KslPbrShader {
                        color { vertexColor() }
                        metallic(0.15f)
                        roughness(0.45f)
                    }
                }
            }
            root.addNode(g)
            slots[sq] = g
        }
        val tr = slots[sq]?.transform as? TrsTransformF
        tr?.translation?.set(wp.x, yLift, wp.z)
    }
    for (sq in 0..63) {
        if (sq !in occupied && slots[sq] != null) {
            slots[sq]?.let { root.removeNode(it) }
            slots[sq] = null
        }
    }
}
