package converter

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.nio.file.Path

object PdfConverter : AbstractConverter<PDFRenderer>() {

    override fun prepareDocument(inputFile: Path): Triple<PDFRenderer, Int, () -> Unit> {
        val document = PDDocument.load(inputFile.toFile())
        val pdfRenderer = PDFRenderer(document)

        return Triple(pdfRenderer, document.numberOfPages) { document.close() }
    }

    override fun pageToImage(provider: PDFRenderer, pageIndex: Int): BufferedImage =
        provider.renderImageWithDPI(pageIndex, 120f, ImageType.GRAY)

}