package ui

import javafx.application.Application
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.TreeView
import javafx.stage.Stage
import java.net.URL


class JavaFXApplication1 : Application() {


    override fun start(stage: Stage) {
        val xml = URL("""file:///D:/git/pdf2txt\src\main\kotlin\ui\DocrApp.fxml""")
        val root = FXMLLoader.load<Parent>(xml)


        val scene = Scene(root, 800.0, 600.0)
        stage.title = "Hello World!"
        stage.scene = scene
        stage.show()
    }


}