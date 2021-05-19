import kotlinx.coroutines.*
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.LoggHelper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentSkipListMap
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger(LoggHelper().toString())

const val INPUT_PDF = """.\data\11437.pdf"""

fun main() = runBlocking {
    print(File(".").absolutePath)

    val deferred = ArrayList<Deferred<Any>>()
    val pages = ConcurrentSkipListMap<Int, String>()

    loadPages { pageImage, pageIndex ->
        deferred += GlobalScope.async {
            pages[pageIndex] = doOCR(pageImage)
            println("     ocr $pageIndex")
        }
    }

    deferred.forEach { it.await() }


    pages.forEach { (page: Int, text: String) ->
        println(
            """-----------------------------------  $page  ------------------------------------
            $text""".trimMargin()
        )
    }

}

private fun doOCR(pageImage: BufferedImage): String {

    val t = Tesseract()

    t.setDatapath(File(".").normalize().absolutePath)
    t.setLanguage("rus")
    //t.setHocr(true)
    t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)
    var text = t.doOCR(pageImage)
    text = text.replace("-\n", "")


    return text

}

private suspend fun loadPages(onPageImageLoaded: PageImageLoaded) {


    val cacheFolder = File("""$INPUT_PDF.img\""")
    if (cacheFolder.isDirectory) {
        loadCachedPages(cacheFolder, onPageImageLoaded)
        return
    }

    cacheFolder.mkdirs()

    convertPdfToBufferedImages(File(INPUT_PDF)) { img, index ->
        onPageImageLoaded(img, index)
        val file = File(cacheFolder, "$index.png")
        ImageIO.write(img, "png", file)


    }

}

private fun loadCachedPages(cacheFolder: File, onPageImageLoaded: PageImageLoaded) {
    var pageIndex = 0

    while (true) {
        val image = try {
            ImageIO.read(File(cacheFolder, "$pageIndex.png"))
        } catch (e: IOException) {
            println("loaded $pageIndex pages")
            return
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        pageIndex++
        println("loaded $pageIndex.png")
        onPageImageLoaded(image, pageIndex)


    }
}


suspend fun convertPdfToBufferedImages(
    inputPdfFile: File,
    onImageExtracted: PageImageLoaded
) {


    runCatching { PDDocument.load(inputPdfFile) }
        .onSuccess { pdDocument ->
            pdDocument.use { document ->
                val pdfRenderer = PDFRenderer(document)
                val deferred = ArrayList<Deferred<Any>>()
                for (pageIndex in 0 until document.numberOfPages) {
                    deferred += extractImage(pdfRenderer, pageIndex, onImageExtracted)
                }
                awaitAll(*deferred.toTypedArray())
            }
        }
        .onFailure { e -> logger.error("Error loading:$inputPdfFile", e) }

}

private suspend fun extractImage(
    pdfRenderer: PDFRenderer,
    pageIndex: Int,
    onImageExtracted: PageImageLoaded
): Deferred<Any> = withContext(Dispatchers.IO) {
    async {
        runCatching { pdfRenderer.renderImageWithDPI(pageIndex, 300f, ImageType.GRAY) }
            .onSuccess { pageImage -> onImageExtracted(pageImage, pageIndex) }
            .onFailure { e -> logger.error("Error extracting PDF Document pageIndex $pageIndex", e) }
    }
}

typealias PageImageLoaded = (BufferedImage, pageIndex: Int) -> Unit