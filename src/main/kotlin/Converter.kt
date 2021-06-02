import com.lizardtech.djvu.DjVuPage
import com.lizardtech.djvu.Document
import com.lizardtech.djvubean.DjVuImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Canvas
import java.awt.image.BufferedImage
import java.nio.file.Path

interface Converter {
    suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded)
}

object PdfConverter : Converter {
    override suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
        runCatching { PDDocument.load(inputFile.toFile()) }
            .onSuccess {
                it.use { document ->
                    val pdfRenderer = PDFRenderer(document)

                    coroutineScope {
                        for (pageIndex in 0 until document.numberOfPages) {
                            withContext(Dispatchers.Default) {
                                pdfPageToImage(
                                    pdfRenderer,
                                    pageIndex,
                                    onImageExtracted
                                )
                            }
                        }
                    }


                }
            }
            .onFailure { e -> logger.error("Error loading:$inputFile", e) }

    }

    private fun pdfPageToImage(
        pdfRenderer: PDFRenderer,
        pageIndex: Int,
        onImageExtracted: PageImageLoaded
    ) {
        runCatching { pdfRenderer.renderImageWithDPI(pageIndex, 120f, ImageType.GRAY) }
            .onSuccess { pageImage -> onImageExtracted(pageImage, pageIndex) }
            .onFailure { e -> logger.error("Error extracting PDF Document pageIndex $pageIndex", e) }

    }

}

object DjvuConverter : Converter {


    override suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
        val document = Document()
        document.isAsync = true

        runCatching { document.read(inputFile.toUri().toURL()) }
            .onFailure {
                logger.error("failed to read djvu file", it)
                return@toBufferedImages
            }

        coroutineScope {
            for (pageNumber in 0 until document.size()) {
                withContext(Dispatchers.Default) {
                    pageToImageAsync(
                        document,
                        pageNumber,
                        onImageExtracted
                    )
                }
            }
        }
    }

    private fun pageToImageAsync(
        document: Document,
        pageIndex: Int,
        onImageExtracted: PageImageLoaded
    ) {


        runCatching { document.getPage(pageIndex, DjVuPage.MAX_PRIORITY, true) }
            .onSuccess { page ->
                val pages = arrayOf<DjVuPage>(page)
                val djvuImage = DjVuImage(pages, true)
                val image = djvuImage.getImage(Canvas(), djvuImage.getPageBounds(0))[0]

                val width = image.getWidth(null)
                val height = image.getHeight(null)

                val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

                val g = bufferedImage.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()

                onImageExtracted(bufferedImage, pageIndex)
            }.onFailure { e -> logger.error("", e) }

    }

}