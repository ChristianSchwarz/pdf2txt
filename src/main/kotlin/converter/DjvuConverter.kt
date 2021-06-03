package converter

import com.lizardtech.djvu.DjVuPage
import com.lizardtech.djvu.Document
import com.lizardtech.djvubean.DjVuImage
import java.awt.Canvas
import java.awt.image.BufferedImage
import java.nio.file.Path

object DjvuConverter : AbstractConverter<Document>() {

    override fun prepareDocument(inputFile: Path): Triple<Document, Int, () -> Unit> {
        val document = Document()
        document.isAsync = true

        document.read(inputFile.toUri().toURL())

        return Triple(document, document.size()) {}
    }

    override fun pageToImage(provider: Document, pageIndex: Int): BufferedImage {
        val page = provider.getPage(pageIndex, DjVuPage.MAX_PRIORITY, true)

        val pages = arrayOf<DjVuPage>(page)
        val djvuImage = DjVuImage(pages, true)
        val image = djvuImage.getImage(Canvas(), djvuImage.getPageBounds(0))[0]

        val width = image.getWidth(null)
        val height = image.getHeight(null)

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

        val g = bufferedImage.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()

        return bufferedImage

    }
}