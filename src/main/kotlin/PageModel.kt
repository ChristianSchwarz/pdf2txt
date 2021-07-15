import java.awt.image.BufferedImage
import java.nio.file.Path

data class Document(val path:Path, val pages:MutableList<Page> = ArrayList())
data class Page(val pageNumber:Int, val text:String, val image:BufferedImage)



