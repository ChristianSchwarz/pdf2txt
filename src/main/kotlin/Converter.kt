import com.lizardtech.djvu.DjVuPage
import com.lizardtech.djvu.Document
import com.lizardtech.djvubean.DjVuImage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Canvas
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi

interface Converter{
    suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded)
}

object PdfConverter:Converter{
    override suspend fun toBufferedImages(inputPdfFile: Path, onImageExtracted: PageImageLoaded) {
        runCatching { PDDocument.load(inputPdfFile.toFile()) }
            .onSuccess {
                it.use { document ->
                    val pdfRenderer = PDFRenderer(document)
                    val deferreds = ArrayList<Deferred<Unit>>()
                    for (pageIndex in 0 until document.numberOfPages) {
                        deferreds += pdfPageToImage(pdfRenderer, pageIndex, onImageExtracted)
                    }
                    deferreds.awaitAll()

                }
            }
            .onFailure { e -> logger.error("Error loading:$inputPdfFile", e) }

    }

    private fun pdfPageToImage(
        pdfRenderer: PDFRenderer,
        pageIndex: Int,
        onImageExtracted: PageImageLoaded
    ): Deferred<Unit> = GlobalScope.async {
        runCatching { pdfRenderer.renderImageWithDPI(pageIndex, 120f, ImageType.GRAY) }
            .onSuccess { pageImage -> onImageExtracted(pageImage, pageIndex) }
            .onFailure { e -> logger.error("Error extracting PDF Document pageIndex $pageIndex", e) }

    }

}

object DjvuConverter:Converter {


    override suspend fun toBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
        val document = Document()
        document.isAsync = true

        runCatching { document.read(inputFile.toUri().toURL()) }
            .onFailure {
                logger.error("failed to read djvu file", it)
                return@toBufferedImages
            }

        val deferreds = ArrayList<Deferred<Unit>>()
        for (pageNumber in 0 until document.size()) {
            deferreds += pageToImage(document, pageNumber, onImageExtracted)
        }
        deferreds.awaitAll()

    }

    private fun pageToImage(
        document: Document,
        pageIndex: Int,
        onImageExtracted: PageImageLoaded
    ): Deferred<Unit> =
        GlobalScope.async {

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