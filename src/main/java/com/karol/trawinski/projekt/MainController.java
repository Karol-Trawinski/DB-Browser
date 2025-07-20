package com.karol.trawinski.projekt;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    public BorderPane Panel;

    public void initialize(URL arg0, ResourceBundle arg1)  {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Sidebar.fxml"));
            Parent root = fxmlLoader.load();
            SidebarController controller = fxmlLoader.getController();

            controller.MainController = this;

            Panel.setLeft(root);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void SetMain(int fxml, DB DB, String item, SidebarController Sidebarcontroller) {
        try {
            if (fxml == 1)
            {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Browser.fxml"));
                Parent root = fxmlLoader.load();
                BrowserController controller = fxmlLoader.getController();
                controller.SetItem(DB,item);
                Panel.setCenter(root);
            }
            else if (fxml == 2)
            {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ERD.fxml"));
                Parent root = fxmlLoader.load();
                ERDController controller =  fxmlLoader.getController();
                controller.SetItem(DB);
                Panel.setCenter(root);

            }
            else if (fxml == 3)
            {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("EditTable.fxml"));
                Parent root = fxmlLoader.load();
                EditTableController controller = fxmlLoader.getController();
                controller.SetItem(DB,item,Sidebarcontroller);
                Panel.setCenter(root);

            }
            else if (fxml == 4)
            {

                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Browser.fxml"));
                Parent root = fxmlLoader.load();
                BrowserController controller = fxmlLoader.getController();
                controller.SetSelect(DB,item);
                Panel.setCenter(root);
            }
            else if (fxml == 5)
            {

                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Browser2.fxml"));
                Parent root = fxmlLoader.load();
                Browser2Controller controller = fxmlLoader.getController();
                controller.SetItem(DB,item,Sidebarcontroller);
                Panel.setCenter(root);
            }

        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}