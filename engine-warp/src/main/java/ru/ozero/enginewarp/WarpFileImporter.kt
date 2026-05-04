package ru.ozero.enginewarp

import java.io.InputStream

interface WarpFileImporter {
    fun import(stream: InputStream): Result<WarpConfig>
}
