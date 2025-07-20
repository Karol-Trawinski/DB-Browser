package com.karol.trawinski.projekt;

import com.mongodb.MongoException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONTokener;

import static com.mongodb.client.model.Filters.eq;

public class Browser2Controller {
    public String item;

    public DB DB;
    public TextField Filterfield;
    public Button Filterbtn;
    public Button Reset;
    public Label Prevbtn;
    public Label Pageinfo;
    public Label Nextbtn;
    public VBox documents;
    public Button ADDbtn;
    public AnchorPane NamePanel;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 20;
    private int totalDocumentsCount;
    private Document filter;
    public SidebarController Sidebarcontroller;
    public void SetItem(DB DB, String item, SidebarController controller)
    {
        this.DB = DB;
        this.item = item;
        this.Sidebarcontroller = controller;
        reload();
    }

    public void reload()
    {
        Filterbtn.setOnAction(e -> applyFilter(Filterfield.getText()));
        Reset.setOnAction(e -> applyFilter(null));
        Prevbtn.setOnMouseClicked(e -> changePage(-1));
        Nextbtn.setOnMouseClicked(e -> changePage(1));
        ADDbtn.setOnAction(event -> ADD_Doc());
        loadDocuments(currentPage);
        TableName();
    }
    public void TableName()
    {
        Label label = new Label(item);
        TextField textField = new TextField();
        label.setFont(new Font(24));

        label.setOnMouseClicked(event -> {
            textField.setText(label.getText());
            NamePanel.getChildren().remove(label);
            NamePanel.getChildren().add(textField);
            textField.requestFocus();
        });

        textField.setOnAction(event -> {
            try
            {
                MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
                connection.getCollection(item).renameCollection(new MongoNamespace(DB.name, textField.getText()));
                item = textField.getText();
                label.setText(textField.getText());
                NamePanel.getChildren().remove(textField);
                NamePanel.getChildren().add(label);
                Sidebarcontroller.reload();
                DB.mongoClient.close();
            }
            catch (Exception e) {
                DB.mongoClient.close();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: "+ e.getMessage());
                alert.showAndWait();
            }

        });

        NamePanel.getChildren().add(label);

    }
    private void ADD_Doc() {
        Stage stage = new Stage();
        stage.setTitle("Dodaj dane");
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);

        Button submitButton = new Button("Dodaj");
        submitButton.setOnAction(event -> {
            try {
                MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
                MongoCollection<Document>  collection = connection.getCollection(item);
                String text = textArea.getText().trim();
                if(text.startsWith("[")) {
                    JSONArray Json = new JSONArray(new JSONTokener(text));
                    for (Object x : Json) {
                        Document doc = Document.parse(x.toString());
                        collection.insertOne(doc);
                    }
                }else
                {
                    Document doc = Document.parse(text);
                    collection.insertOne(doc);
                }
                DB.mongoClient.close();
                loadDocuments(currentPage);
                stage.close();

            } catch (Exception e) {
                DB.mongoClient.close();
                loadDocuments(currentPage);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: "+e.getMessage());
                alert.showAndWait();
            }
        });

        VBox vBox = new VBox(10, textArea, submitButton);
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vBox, 450, 350);

        stage.setScene(scene);
        stage.showAndWait();
    }

    private void loadDocuments(int page) {
        try {
            documents.getChildren().clear();
            MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
            MongoCollection<Document>  collection = connection.getCollection(item);
            if(filter != null) {
                FindIterable<Document> iterable = collection.find(filter)
                        .skip((page - 1) * PAGE_SIZE)
                        .limit(PAGE_SIZE);
                totalDocumentsCount = (int) collection.countDocuments(filter);
                iterable.forEach(this::ShowDocument);
            }else {
                FindIterable<Document> iterable = collection.find()
                        .skip((page - 1) * PAGE_SIZE)
                        .limit(PAGE_SIZE);
                totalDocumentsCount = (int) collection.countDocuments();
                iterable.forEach(this::ShowDocument);
            }
            int fromIndex = (currentPage - 1) * PAGE_SIZE + 1;
            int toIndex = Math.min(currentPage * PAGE_SIZE,  totalDocumentsCount);
            Pageinfo.setText(fromIndex + " - " + toIndex + " z " + totalDocumentsCount);
            DB.mongoClient.close();
        }catch (MongoException e){
            DB.mongoClient.close();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("ERROR: "+e.getMessage());
            alert.showAndWait();
        }



    }

    private void ShowDocument(Document doc)
    {
        VBox document = new VBox();
        document.getStyleClass().add("document");
        TextArea textArea = new TextArea();
        JsonWriterSettings prettyPrint = JsonWriterSettings.builder().indent(true).build();
        textArea.setText(doc.toJson(prettyPrint));
        textArea.setPrefHeight(textArea.getText().lines().count()*20);
        HBox panel = new HBox();
        panel.setPadding(new Insets(5));
        panel.setSpacing(10);
        panel.setAlignment(Pos.CENTER_RIGHT);
        Button updatebtn = new Button("Update");
        ObjectId id = doc.getObjectId("_id");
        updatebtn.setOnAction(e -> updateDocument(id,textArea.getText()));
        Button deletebtn = new Button("Delete");
        deletebtn.setOnAction(e -> DeleteDocument(id));
        panel.getChildren().addAll(updatebtn, deletebtn);
        document.getChildren().addAll(textArea, panel);
        documents.getChildren().add(document);
    }
    private void applyFilter(String filterJson) {
        if (filterJson != null && !filterJson.isEmpty()) {
            try {
            filter = Document.parse(filterJson);
            currentPage = 1;
            loadDocuments(currentPage);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: "+e.getMessage());
                alert.showAndWait();
            }
        }else {
            filter = null;
            currentPage = 1;
            loadDocuments(currentPage);
        }
    }
    private void changePage(int delta) {
        int newPage = currentPage + delta;
        int totalPages = (int) Math.ceil(totalDocumentsCount / (double) PAGE_SIZE);
        if (newPage < 1 || newPage > totalPages) {
            return;
        }

        currentPage = newPage;
        loadDocuments(currentPage);
    }

    private void updateDocument(ObjectId id, String doc) {
        try {
            MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
            MongoCollection<Document>  collection = connection.getCollection(item);
            Document newDoc = Document.parse(doc);
            Bson filter = eq("_id", id);
            collection.replaceOne(filter, newDoc);
            DB.mongoClient.close();
            loadDocuments(currentPage);

        } catch (Exception e) {
            DB.mongoClient.close();
            loadDocuments(currentPage);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("ERROR: "+e.getMessage());
            alert.showAndWait();

        }
    }
    private void DeleteDocument(ObjectId id) {
        try {
            MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
            MongoCollection<Document>  collection = connection.getCollection(item);
            Bson filter = eq("_id", id);
            collection.deleteOne(filter);
            DB.mongoClient.close();
            loadDocuments(currentPage);

        } catch (Exception e) {
            DB.mongoClient.close();
            loadDocuments(currentPage);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("ERROR: "+e.getMessage());
            alert.showAndWait();

        }
    }

}
