/**
 * Copyright @ 2018 Quan Nguyen
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.tess4j.util

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PDF utilities based on PDFBox.
 *
 * @author Robert Drysdale
 * @author Quan Nguyen
 */
object PdfBox {

    private val logger = LoggerFactory.getLogger(LoggHelper().toString())

    /**
     * Converts PDF to PNG format.
     *
     * @param inputPdfFile input file
     * @return an array of PNG images
     * @throws java.io.IOException
     */
    @Throws(IOException::class)
    fun convertPdfToBufferedImages(inputPdfFile: File, onImageExtracted: (BufferedImage, pageIndex:Int)->Unit): Array<BufferedImage> {

        val executor = Executors.newFixedThreadPool(8)
        PDDocument.load(inputPdfFile).use { document ->
            val pdfRenderer = PDFRenderer(document)

            val numberOfPages = document.numberOfPages
            val out = Array<BufferedImage?>(numberOfPages) { null }
            out.forEachIndexed { pageIndex, _ ->
                executor.submit {
                    try {
                        val pageImage = pdfRenderer.renderImageWithDPI(pageIndex, 300f, ImageType.GRAY)
                        out[pageIndex] = pageImage
                        onImageExtracted(pageImage,pageIndex)
                    } catch (e: IOException) {
                        logger.error("Error extracting PDF Document pageIndex $pageIndex=> $e", e)
                    }
                }
            }
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.HOURS)

            return out.filterNotNull().toTypedArray()
        }
    }
}