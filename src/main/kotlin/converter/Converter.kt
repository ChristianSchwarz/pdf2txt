package converter

import PageImageLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import logger
import java.awt.image.BufferedImage
import java.nio.file.Path

interface Converter {
    suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded)
}

abstract class AbstractConverter<PAGE_PROVIDER> : Converter {

    final override suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {

        runCatching {
            prepareDocument(inputFile)
        }.onSuccess { (pageProvider, numberOfPages, finally) ->
            loadPages(pageProvider, numberOfPages, onImageExtracted, inputFile)
            finally()
        }.onFailure { e -> logger.error("Error loading:$inputFile", e) }
    }

    private suspend fun loadPages(
        pageProvider: PAGE_PROVIDER,
        numberOfPages: Int,
        onImageExtracted: PageImageLoaded,
        inputFile: Path
    ) = coroutineScope {
        for (pageIndex in 0 until numberOfPages) {
            withContext(Dispatchers.Default) {
                runCatching {
                    pageToImage(pageProvider, pageIndex)
                }.onSuccess { image ->
                    onImageExtracted(image, pageIndex)
                }.onFailure { e ->
                    logger.error("failed to extract image of page $pageIndex, of ${inputFile.fileName}", e)
                }
            }
        }
    }


    abstract fun prepareDocument(inputFile: Path): Triple<PAGE_PROVIDER, Int, () -> Unit>

    abstract fun pageToImage(
        provider: PAGE_PROVIDER,
        pageIndex: Int

    ): BufferedImage
}