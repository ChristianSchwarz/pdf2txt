package ui

import javafx.fxml.FXML
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView

class UiController {

    @FXML
    private lateinit var treeView: TreeView<Any>


    fun initialize() {

        val root: TreeItem<Any> = TreeItem("pages")


        treeView.root = root
    }
}