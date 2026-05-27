package ru.ozero.desktop.vpn

import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

interface FilePickerPort {
    fun pickConfigFile(): String?
}

class SwingFilePickerPort : FilePickerPort {
    override fun pickConfigFile(): String? {
        val chooser = JFileChooser()
        chooser.fileFilter = FileNameExtensionFilter("WARP config", "conf")
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile.absolutePath
    }
}
