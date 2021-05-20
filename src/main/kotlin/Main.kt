//import com.aspose.imaging.Image
//import com.aspose.imaging.fileformats.djvu.DjvuImage
import com.lizardtech.djvu.DjVuPage
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
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap
import javax.imageio.ImageIO
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension


private val logger = LoggerFactory.getLogger(LoggHelper().toString())

//const val INPUT_FILE = """.\data\11437.pdf"""
const val INPUT_FILE = """.\data\Бабич - Наши авианосцы на стапелях и в дальних походах - 2003.djvu"""

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

            val message = """-----------------------------------  $page  ------------------------------------
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

@OptIn(ExperimentalPathApi::class, kotlin.ExperimentalStdlibApi::class)
private suspend fun loadPages(onPageImageLoaded: PageImageLoaded) {


    val cacheFolder = File("""$INPUT_FILE.img\""")
    if (cacheFolder.isDirectory) {
        loadCachedPages(cacheFolder, onPageImageLoaded)
        return
    }

    cacheFolder.mkdirs()
    val onImageExtracted: PageImageLoaded = { img, index ->
        onPageImageLoaded(img, index)
        val file = File(cacheFolder, "$index.png")
        ImageIO.write(img, "png", file)
    }
    val inputFile = Path(INPUT_FILE)

    when (inputFile.extension.lowercase()) {
        "pdf" -> convertPdfToBufferedImages(File(INPUT_FILE), onImageExtracted)
        "djvu" -> convertDjvuToBufferedImages(inputFile, onImageExtracted)
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
//
//@OptIn(ExperimentalPathApi::class)
//private suspend fun convertDjvuToBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
//    val doc = Image.load(inputFile.absolutePathString()) as DjvuImage
//
//    File("$INPUT_FILE.img/").mkdirs()
//    for (page in 0..doc.pageCount) {
//
//        val djvuPage = doc.djvuPages[page]
//
//
//        //val bufferedImage = djvuPage.backgroundImage.toBitmap() // Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index 3074958 out of bounds for length 3074958
//        //val bufferedImage = djvuPage.image.toBitmap() //only images, text is missing
//
//        //val bufferedImage = djvuPage.foregroundImage.toBitmap() //java.lang.ArrayIndexOutOfBoundsException: Index 193116 out of bounds for length 193116
//        // val bufferedImage = djvuPage.textImage.toBitmap() //only copyright disclaimer visible in the top left corner
//        val bounds = djvuPage.bounds
//        val pixelArray: IntArray = djvuPage.loadArgb32Pixels(bounds)
//        val bufferedImage=BufferedImage(djvuPage.width,djvuPage.height, BufferedImage.TYPE_INT_RGB)
//
//        pixelArray.forEachIndexed { index, pixel ->
//            val x:Int= index % bounds.width
//            val y:Int = index / bounds.width
//            bufferedImage.setRGB(x,y,pixel)
//        }
//
//
//
//        onImageExtracted(bufferedImage, page)
//    }
//}



@OptIn(ExperimentalPathApi::class)
private suspend fun convertDjvuToBufferedImages(inputFile: Path, onImageExtracted: PageImageLoaded) {
    val document = Document()
    document.setAsync(true)
    document.read(inputFile.toUri().toURL())



    val canvas = Canvas()
    for (index in 0 until document.size()) {

        val pages = arrayOf<DjVuPage>(document.getPage(index, DjVuPage.MAX_PRIORITY, true))
        val djvuImage = DjVuImage(pages, true)
        val image_local: Image = djvuImage.getImage(canvas, djvuImage.getPageBounds(0))[0]

        val image = BufferedImage(image_local.getWidth(null), image_local.getHeight(null), BufferedImage.TYPE_INT_ARGB)

        val g: Graphics = image.createGraphics()
        g.drawImage(image_local, 0, 0, null)
        g.dispose()
        onImageExtracted(image, index)
    }
}

suspend fun convertPdfToBufferedImages(
    inputPdfFile: File,
    onImageExtracted: PageImageLoaded
) {


    runCatching { PDDocument.load(inputPdfFile) }
        .onSuccess {
            it.use { document ->
                val pdfRenderer = PDFRenderer(document)
                val deferred = ArrayList<Deferred<Any>>()
                for (pageIndex in 0 until document.numberOfPages) {
                    deferred += extractImage(pdfRenderer, pageIndex, onImageExtracted)
                }
                deferred.awaitAll()
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


fun load(file:URL): BufferedImage {

    val document = Document()
    document.setAsync(false)

    document.read(file)
    val page: Array<DjVuPage> = arrayOf<DjVuPage>(document.getPage(0, DjVuPage.MAX_PRIORITY, true))
    val djvuImage = DjVuImage(page, true)
    val image_local: Image = djvuImage.getImage(Canvas(), djvuImage.getPageBounds(0)).get(0)

    val image = BufferedImage(image_local.getWidth(null), image_local.getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val g: Graphics = image.createGraphics()
    g.drawImage(image_local, 0, 0, null)
    g.dispose()
    return image
}