import com.lizardtech.djvu.DjVuPage
import com.lizardtech.djvu.DjVuPage.MAX_PRIORITY
import com.lizardtech.djvu.Document
import com.lizardtech.djvubean.DjVuImage
import kotlinx.coroutines.*
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.LoggHelper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import java.awt.Canvas
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_BYTE_GRAY
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension


private val logger = LoggerFactory.getLogger(LoggHelper().toString())

const val INPUT_FILE = """.\data\11437.pdf"""
//const val INPUT_FILE = """.\data\Бабич - Наши авианосцы на стапелях и в дальних походах - 2003.djvu"""

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

    deferred.awaitAll()


    FileWriter("$INPUT_FILE.txt").use {
        pages.forEach { (page: Int, text: String) ->

            val message = """
                -----------------------------------  $page  ------------------------------------
            $text""".trimMargin()
            println(
                message
            )
            it.append(message)
        }
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

@OptIn(ExperimentalPathApi::class, ExperimentalStdlibApi::class)
private suspend fun loadPages(onPageImageLoaded: PageImageLoaded) {


    val cacheFolder = File("""$INPUT_FILE.img\""")
    if (cacheFolder.isDirectory) {
        loadCachedPages(cacheFolder, onPageImageLoaded)
        return
    }

    cacheFolder.mkdirs()
    val onImageExtracted: PageImageLoaded = { img, index ->
        onPageImageLoaded(img, index)

        writeImageToCache(cacheFolder, index, img)
    }
    val inputFile = Path(INPUT_FILE)

    when (inputFile.extension.lowercase()) {
        "pdf" -> convertPdfToBufferedImages(File(INPUT_FILE), onImageExtracted)
        "djvu" -> convertDjvuToBufferedImages(inputFile, onImageExtracted)
    }


}

private fun writeImageToCache(cacheFolder: File, index: Int, image: BufferedImage) {
    val file = File(cacheFolder, "$index.png")
    //ImageIO.write(img, "png", file)

    ImageIO.createImageOutputStream(file.outputStream()).use { out ->
        val type = ImageTypeSpecifier.createFromRenderedImage(image)
        val writer = ImageIO.getImageWriters(type, "png").next()!!
        val param = writer.defaultWriteParam
        if (param.canWriteCompressed()) {
            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = 0.5f

        }
        writer.output = out


        writer.write(null, IIOImage(image, null, null), param)
        writer.dispose()
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

@OptIn(ExperimentalPathApi::class)
private suspend fun convertDjvuToBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
    val document = Document()
    document.isAsync = true

    document.read(inputFile.toUri().toURL())

val deferreds = ArrayList<Deferred<Unit>>()
    for (pageNumber in 0 until document.size()) {
        deferreds += djvuPageToImage(document, pageNumber, onImageExtracted)
    }
    deferreds.awaitAll()

}

private fun djvuPageToImage(
    document: Document,
    pageIndex: Int,
    onImageExtracted: PageImageLoaded
):Deferred<Unit> =
    GlobalScope.async {

        runCatching { document.getPage(pageIndex, MAX_PRIORITY, true) }
            .onSuccess { page ->
                val pages = arrayOf<DjVuPage>(page)
                val djvuImage = DjVuImage(pages, true)
                val image = djvuImage.getImage(Canvas(), djvuImage.getPageBounds(0))[0]

                val width = image.getWidth(null)
                val height = image.getHeight(null)

                val bufferedImage = BufferedImage(width, height, TYPE_BYTE_GRAY)

                val g = bufferedImage.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()

                onImageExtracted(bufferedImage, pageIndex)
            }.onFailure { e -> logger.error("", e) }

    }

suspend fun convertPdfToBufferedImages(
    inputPdfFile: File,
    onImageExtracted: PageImageLoaded
) {
    runCatching { PDDocument.load(inputPdfFile) }
        .onSuccess {
            it.use { document ->
                val pdfRenderer = PDFRenderer(document)
                val deferreds = ArrayList<Deferred<Unit>>()
                for (pageIndex in 0 until document.numberOfPages) {
                    deferreds+=pdfPageToImage(pdfRenderer, pageIndex, onImageExtracted)
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
):Deferred<Unit> = GlobalScope.async {
    runCatching { pdfRenderer.renderImageWithDPI(pageIndex, 120f, ImageType.GRAY) }
        .onSuccess { pageImage -> onImageExtracted(pageImage, pageIndex) }
        .onFailure { e -> logger.error("Error extracting PDF Document pageIndex $pageIndex", e) }
}

typealias PageImageLoaded = (BufferedImage, pageIndex: Int) -> Unit