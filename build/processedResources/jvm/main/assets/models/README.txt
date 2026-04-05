glTF 2.0 piece models (.glb recommended)
======================================

Place files here using names from [PieceModelPaths] in the Kotlin sources, for example:

  w_pawn.glb, w_knight.glb, w_bishop.glb, w_rook.glb, w_queen.glb, w_king.glb
  b_pawn.glb, b_knight.glb, b_bishop.glb, b_rook.glb, b_queen.glb, b_king.glb

The current build uses procedural cubes for pieces. To switch to glTF, load models in a coroutine
(see kool Assets.loadGltfModel) and replace the meshes created in ChessWorldScene.syncPieces.
