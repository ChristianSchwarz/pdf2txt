import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger(LoggHelper().toString())

fun main() = runBlocking{
    val executor = Executors.newFixedThreadPool(8)

    val deferred = ArrayList<Deferred<Any>>()
    val pages = ConcurrentSkipListMap<Int, String>()

    loadPages { pageImage, pageIndex ->
        deferred+=GlobalScope.async {
            pages[pageIndex] = doOCR(pageImage)
            println("     ocr $pageIndex")
        }
    }

    deferred.forEach { it.await() }
    executor.shutdown()
    executor.awaitTermination(10, MINUTES)


    pages.forEach { (page: Int, text: String) ->
        println(
            """-----------------------------------  $page  ------------------------------------
            $text""".trimMargin()
        )
    }

}

private fun doOCR(pageImage: BufferedImage): String {

    val t = Tesseract()

    t.setDatapath(File("""c:\git-p\pdf2txt\src\main\kotlin\""").absolutePath)
    t.setLanguage("rus")
    //t.setHocr(true)
    t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)
    var text = t.doOCR(pageImage)
    text = text.replace("-\n", "")


    return text

}

private suspend fun loadPages(onPageImageLoaded: PageImageLoaded) {

    val input = """c:\git-p\pdf2txt\src\main\kotlin\11437.pdf"""
    val cacheFolder = File("""$input.img\""")
    if (cacheFolder.isDirectory) {
        loadCachedPages(cacheFolder, onPageImageLoaded)
        return
    }

    cacheFolder.mkdirs()

    convertPdfToBufferedImages(File(input)) { img, index ->
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


@Throws(IOException::class)
suspend fun convertPdfToBufferedImages(
    inputPdfFile: File,
    onImageExtracted: PageImageLoaded
) {

    val deferred = ArrayList<Deferred<Any>>()
    PDDocument.load(inputPdfFile).use { document ->
        val pdfRenderer = PDFRenderer(document)

        for (pageIndex in 0 until document.numberOfPages) {
            deferred+=GlobalScope.async {
                try {
                    val pageImage = pdfRenderer.renderImageWithDPI(pageIndex, 300f, ImageType.GRAY)

                    onImageExtracted(pageImage, pageIndex)
                } catch (e: IOException) {
                    logger.error("Error extracting PDF Document pageIndex $pageIndex=> $e", e)
                }
            }
        }
        deferred.forEach { it.await() }
    }

}

typealias PageImageLoaded = (BufferedImage, pageIndex: Int) -> Unit