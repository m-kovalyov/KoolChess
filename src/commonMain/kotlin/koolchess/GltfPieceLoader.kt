package koolchess

import de.fabmax.kool.Assets
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.GltfMaterialConfig
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.releaseWith
import koolchess.chess.Piece

/** Column index in [pieceGltfCache]: 0 = white, 1 = black. Row: PAWN..KING → 0..5 */
fun pieceGltfSlot(pieceType: Int): Int = (pieceType - 1).coerceIn(0, 5)

fun gltfPieceConfig(): GltfLoadConfig = GltfLoadConfig(
    materialConfig = GltfMaterialConfig(),
    loadAnimations = false,
    applySkins = false,
    applyMorphTargets = false,
)

/**
 * Fills [into] with loaded [GltfFile] per side and piece type. Missing files stay null (procedural fallback).
 */
suspend fun preloadPieceGltfFiles(into: Array<Array<GltfFile?>>) {
    require(into.size == 2 && into[0].size == 6)
    for (wi in 0..1) {
        val white = wi == 0
        for (t in Piece.PAWN..Piece.KING) {
            val slot = pieceGltfSlot(t)
            into[wi][slot] = Assets.loadGltfFile(PieceModelPaths.path(white, t)).getOrNull()
        }
    }
}

fun instantiatePieceModel(file: GltfFile, cfg: GltfLoadConfig, scene: Scene): Model? =
    runCatching { file.makeModel(cfg) }
        .getOrNull()
        ?.also { it.releaseWith(scene) }
