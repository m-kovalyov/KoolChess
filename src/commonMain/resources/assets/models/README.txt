glTF 2.0 piece models (.glb recommended)
======================================

Place files here using names from [PieceModelPaths] in the Kotlin sources, for example:

  w_pawn.glb, w_knight.glb, w_bishop.glb, w_rook.glb, w_queen.glb, w_king.glb
  b_pawn.glb, b_knight.glb, b_bishop.glb, b_rook.glb, b_queen.glb, b_king.glb

glTF loading is wired in code: place matching .glb files here; missing names keep the old cube fallback.
Tune global size with PieceModelPaths.GLTF_UNIFORM_SCALE in Kotlin if models look wrong on the board.
