import converter.DjvuConverter
import converter.PdfConverter
import kotlinx.coroutines.*
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.LoggHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentSkipListMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.name


val logger: Logger = LoggerFactory.getLogger(LoggHelper().toString())

//const val INPUT_FILE = """.\data\11437.pdf"""
const val INPUT_FILE = """.\data\Бабич - Наши авианосцы на стапелях и в дальних походах - 2003.djvu"""

fun main() = runBlocking {
    print(File(".").absolutePath)

    val deferred = ArrayList<Deferred<Any>>()
    val pages = ConcurrentSkipListMap<Int, String>()

    coroutineScope {
        loadPages { pageImage, pageIndex ->
             launch {
                pages[pageIndex] = doOCR(pageImage)
                println("     ocr $pageIndex")
            }
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
    t.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY)
    t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)
    t.setHocr(true)
    var text = t.doOCR(pageImage)
    text = text.replace("-\n", "")

    return text
}

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

    val converter = when (inputFile.extension.lowercase()) {
        "pdf" -> PdfConverter
        "djvu" -> DjvuConverter
        else -> throw Error("Unsupported file format: ${inputFile.name}")
    }
    converter.toBufferedImages(inputFile, onImageExtracted)


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



typealias PageImageLoaded = (BufferedImage, pageIndex: Int) -> Unit