package koolchess

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = KoolApplication(
    config = KoolConfigJvm(
        windowTitle = "KoolChess",
    ),
) {
    val bridge = FileBridge(
        openJson = {
            val fc = JFileChooser()
            fc.fileFilter = FileNameExtensionFilter("Chess save (*.json)", "json")
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                fc.selectedFile.readBytes()
            } else {
                null
            }
        },
        saveJson = { bytes ->
            val fc = JFileChooser()
            fc.selectedFile = java.io.File(fc.currentDirectory, "koolchess-save.json")
            fc.fileFilter = FileNameExtensionFilter("Chess save (*.json)", "json")
            if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                var f = fc.selectedFile
                if (!f.name.endsWith(".json", ignoreCase = true)) {
                    f = java.io.File(f.parentFile, f.name + ".json")
                }
                f.writeBytes(bytes)
            }
        },
    )
    runChessApp(this.ctx, bridge)
}
