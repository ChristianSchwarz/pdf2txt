import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.util.PdfBox
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import javax.imageio.ImageIO


fun main() {
    //val images = PdfBox.convertPdf2Png(File("""D:\git-p\pdf-ocr\src\main\kotlin\11437.pdf"""))

    val executor = Executors.newFixedThreadPool(8)

    val pages = ConcurrentSkipListMap<Int, String>()

    loadPages { pageImage, pageIndex ->
        executor.execute {
            pages[pageIndex] = doOCR(pageImage)
            println("     ocr $pageIndex")
        }
    }

    executor.shutdown()
    executor.awaitTermination(10,MINUTES)


    pages.forEach { page: Int, text: String ->
        println(
            """-----------------------------------  $page  ------------------------------------
            $text""".trimMargin()
        )
    }

}

private fun doOCR(pageImage: BufferedImage): String {

    val t = Tesseract()
    t.setDatapath(File("""C:\Program Files\Tesseract-OCR\tessdata""").absolutePath)
    t.setLanguage("rus")
    //t.setHocr(true)
    t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)
    var text = t.doOCR(pageImage)
    text = text.replace("-\n", "")


    return text

}

private fun loadPages(onPageImageLoaded: PageImageLoaded) {

    val input = """D:\git-p\pdf-ocr\src\main\kotlin\11437.pdf"""
    val cacheFolder = File("""$input.img\""")
    if (cacheFolder.isDirectory) {
        loadCachedPages(cacheFolder, onPageImageLoaded)
        return
    }

    cacheFolder.mkdirs()

    PdfBox.convertPdfToBufferedImages(File(input)) { img, index ->
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
        } catch (e:Exception){
            e.printStackTrace()
            throw e
        }
        pageIndex++
        println("loaded $pageIndex.png")
        onPageImageLoaded(image, pageIndex)


    }
}

typealias PageImageLoaded = (BufferedImage, pageIndex: Int) -> Unit