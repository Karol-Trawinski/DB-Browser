package com.karol.trawinski.projekt;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SidebarController implements Initializable {

    public MainController MainController;
    @FXML
    private TreeView<String> DB_Menu;
    public static Map<String, DB> DBList = new HashMap<>();
    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        reload();
    }
    @FXML
    private void ADD_DB() {
        ADD(null);
    }

    public void MenuItemClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 1) {
            DB_Menu.setContextMenu(null);
            TreeItem<String> selectedItem = DB_Menu.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                if(selectedItem.getParent().getValue().equals("Bazy Danych")) {
                    DB x = DBList.get(selectedItem.getValue());
                    if(!x.actived)
                    {
                        if(mouseEvent.getButton() == MouseButton.PRIMARY)
                            ADD(x);
                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem menuItem = new MenuItem("Usuń");
                        menuItem.setOnAction(event -> {
                            DBList.remove(x.id);
                            reload();
                        });
                        contextMenu.getItems().add(menuItem);
                        DB_Menu.setContextMenu(contextMenu);
                    }else
                    {
                        ContextMenu contextMenu = new ContextMenu();

                        MenuItem menuItem = new MenuItem("Wykonaj Zapytanie");
                        menuItem.setOnAction(event ->Execute_SQL(x));
                        String text = "Utwórz Nową Tabelę";
                        if(x.type.equals("MangoDB"))
                            text = "Utwórz Nową Kolekcję";
                        MenuItem menuItem2 = new MenuItem(text);
                        menuItem2.setOnAction(event -> ADD_Table(x));

                        MenuItem menuItem3 = new MenuItem("ERD");
                        menuItem3.setOnAction(event -> MainController.SetMain(2,x,null,this));

                        MenuItem menuItem4 = new MenuItem("Edytuj");
                        menuItem4.setOnAction(event -> ADD(x));


                        MenuItem menuItem5 = new MenuItem("Usuń");
                        menuItem5.setOnAction(event -> {
                            DBList.remove(x.id);
                            reload();
                        });
                        switch (x.type) {
                            case "Cassandra" ->
                                    contextMenu.getItems().addAll(menuItem, menuItem2, menuItem4, menuItem5);
                            case "MangoDB" -> contextMenu.getItems().addAll(menuItem2, menuItem4, menuItem5);
                            default ->
                                    contextMenu.getItems().addAll(menuItem, menuItem2, menuItem3, menuItem4, menuItem5);
                        }

                        DB_Menu.setContextMenu(contextMenu);
                    }
                }else
                {
                    DB x = DBList.get(selectedItem.getParent().getValue());
                    if(mouseEvent.getButton() == MouseButton.PRIMARY) {
                        if (x.type.equals("MangoDB"))
                            MainController.SetMain(5,x , selectedItem.getValue(), this);
                        else
                            MainController.SetMain(1,x , selectedItem.getValue(), this);
                    }
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem menuItem = new MenuItem("Edytuj");
                    menuItem.setOnAction(event -> MainController.SetMain(3,x,selectedItem.getValue(),this));
                    MenuItem menuItem2 = new MenuItem("Usuń");

                    menuItem2.setOnAction(event -> {
                        switch (x.type) {
                            case "Cassandra" -> {
                                try (CqlSession connection = (CqlSession) x.connectToDatabase()) {
                                    connection.execute("DROP TABLE IF EXISTS " + selectedItem.getValue());
                                    reload();
                                } catch (Exception e) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Error");
                                    alert.setHeaderText(null);
                                    alert.setContentText("ERROR: " + e.getMessage());
                                    alert.showAndWait();
                                }
                            }
                            case "MangoDB" -> {
                                try {
                                    MongoDatabase connection = (MongoDatabase) x.connectToDatabase();
                                    connection.getCollection(selectedItem.getValue()).drop();
                                    x.mongoClient.close();
                                    reload();

                                } catch (Exception e) {
                                    x.mongoClient.close();
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Error");
                                    alert.setHeaderText(null);
                                    alert.setContentText("ERROR: " + e.getMessage());
                                    alert.showAndWait();
                                }
                            }
                            default -> {
                                try (Connection connection = (Connection) x.connectToDatabase()) {
                                    Statement statement = connection.createStatement();
                                    statement.executeUpdate("DROP TABLE " + selectedItem.getValue());
                                    reload();
                                } catch (Exception e) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Error");
                                    alert.setHeaderText(null);
                                    alert.setContentText("SQL ERROR: " + e.getMessage());
                                    alert.showAndWait();
                                }
                            }
                        }

                    });
                    if (x.type.equals("MangoDB"))
                        contextMenu.getItems().add(menuItem2);
                    else
                        contextMenu.getItems().addAll(menuItem,menuItem2);
                    DB_Menu.setContextMenu(contextMenu);
                }
            }
        }
    }
    public void reload()
    {

        TreeItem<String> DB_List =  new TreeItem<>("Bazy Danych");
        for (String key : DBList.keySet()) {
            DBList.get(key).reload();
            DB_List.getChildren().add(DBList.get(key).MenuItem);
        }

        DB_Menu.setRoot(DB_List);
        DB_Menu.setShowRoot(false);
    }
    public static void LoadDB()
    {
        String filePath = "DB.json";
        File file = new File(filePath);

        JSONObject JsonDB = new JSONObject();
        if (file.exists()) {
            try (FileReader fileReader = new FileReader(file)) {
                JsonDB = new JSONObject(new JSONTokener(fileReader));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (String key : JsonDB.keySet())
        {
            JSONObject x = (JSONObject) JsonDB.get(key);
            String type = (String)x.get("Type");
            String host = (String)x.get("Host");
            String login = (String)x.get("Login");
            String password = (String)x.get("Password");
            String name = (String)x.get("Name");
            DB DB = new DB(type,key,host,login,password,name);
            DBList.put(key,DB);


        }

    }
    public static void SaveDB()
    {
        String filePath = "DB.json";
        JSONObject ListDB = new JSONObject();

        for (String key : DBList.keySet())
        {
            JSONObject x = new JSONObject();
            x.put("Type",DBList.get(key).type);
            x.put("Host",DBList.get(key).host);
            x.put("Login",DBList.get(key).login);
            x.put("Password",DBList.get(key).password);
            x.put("Name",DBList.get(key).name);
            ListDB.put(key,x);
        }
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.write(ListDB.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void ADD(DB Database) {

        Text actionStatus = new Text();
        Stage stage = new Stage();
        stage.setTitle("Konfiguracja bazy danych");

        Button connectButton = new Button("Połącz");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.setAlignment(Pos.TOP_CENTER);

        ChoiceBox<String> type = new ChoiceBox<>();
        type.getItems().addAll("MySQL", "PostgreSQL", "MariaDB","Oracle", "SQLite","MangoDB","Cassandra");
        type.setValue("MySQL");

        TextField id = new TextField();
        id.setPromptText("Identyfikator");

        TextField host = new TextField();
        host.setPromptText("Host");

        TextField login = new TextField();
        login.setPromptText("Login");

        PasswordField pass = new PasswordField();
        pass.setPromptText("Hasło");

        TextField name = new TextField();
        name.setPromptText("Nazwa bazy danych");

        TextField path = new TextField();
        path.setPromptText("Ścieżka do pliku bazy danych");

        Button btnChoosePath = new Button("Wybierz");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Wybierz bazę danych SQLite");

        btnChoosePath.setOnAction(e -> {
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                path.setText(file.getAbsolutePath());
            }
        });
        grid.addRow(0, new Label("Typ bazy danych:"), type);
        grid.addRow(1, new Label("Identyfikator:"), id);

        if(Database!= null)
        {
            type.setValue(Database.type);
            id.setText(Database.id);
            host.setText(Database.host);
            login.setText(Database.login);
            pass.setText(Database.password);
            name.setText(Database.name);

            if(Database.type.equals("SQLite")) {
                grid.addRow(2, new Label("Ścieżka:"), path, btnChoosePath);
                grid.add(actionStatus, 0, 3, 3,1);
                GridPane.setHalignment(actionStatus, HPos.CENTER);
                grid.add(connectButton, 0, 4, 3,1);
                GridPane.setHalignment(connectButton, HPos.CENTER);
                path.setText(Database.host);
            }else{
                grid.addRow(2, new Label("Host:"), host);
                grid.addRow(3, new Label("Login:"), login);
                grid.addRow(4, new Label("Hasło:"), pass);
                grid.addRow(5, new Label("Nazwa bazy danych:"), name);
                actionStatus.setFill(Color.FIREBRICK);
                grid.add(actionStatus, 0, 6, 2,1);
                GridPane.setHalignment(actionStatus, HPos.CENTER);
                grid.add(connectButton, 0, 7, 2,1);
                GridPane.setHalignment(connectButton, HPos.CENTER);
            }

        }else
        {
            grid.addRow(2, new Label("Host:"), host);
            grid.addRow(3, new Label("Login:"), login);
            grid.addRow(4, new Label("Hasło:"), pass);
            grid.addRow(5, new Label("Nazwa bazy danych:"), name);
            actionStatus.setFill(Color.FIREBRICK);
            grid.add(actionStatus, 0, 6, 2,1);
            GridPane.setHalignment(actionStatus, HPos.CENTER);
            grid.add(connectButton, 0, 7, 2,1);
            GridPane.setHalignment(connectButton, HPos.CENTER);
        }




        type.valueProperty().addListener((obs, oldVal, newVal) -> {
            grid.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 1);
            if (newVal.equals("SQLite")) {
                grid.addRow(2, new Label("Ścieżka:"), path, btnChoosePath);
                grid.add(actionStatus, 0, 3, 3, 1);
                GridPane.setHalignment(actionStatus, HPos.CENTER);
                grid.add(connectButton, 0, 4, 3, 1);
                GridPane.setHalignment(connectButton, HPos.CENTER);
            } else {
                grid.addRow(2, new Label("Host:"), host);
                grid.addRow(3, new Label("Login:"), login);
                grid.addRow(4, new Label("Hasło:"), pass);
                grid.addRow(5, new Label("Nazwa bazy danych:"), name);
                grid.add(actionStatus, 0, 6, 2, 1);
                GridPane.setHalignment(actionStatus, HPos.CENTER);
                grid.add(connectButton, 0, 7, 2, 1);
                GridPane.setHalignment(connectButton, HPos.CENTER);
            }
        });


        connectButton.setOnAction(event -> {
            String Type = type.getValue();
            String Id = id.getText();
            String Host = host.getText();
            String Login = login.getText();
            String Pass = pass.getText();
            String Name = name.getText();
            String Path = path.getText();

            if (connectToDatabase(Type,Host,Login,Pass,Name,Path)) {
                DB DB;
                if(Type.equals("SQLite")) {
                    DB = new DB(Type, Id, Path, Login, Pass, Name);
                }
                else
                    DB = new DB(Type,Id,Host,Login,Pass,Name);
                DB.activate();

                DBList.put(Id, DB);
                actionStatus.setText("");
                stage.close();
                reload();


            } else {
                actionStatus.setText("Błąd! Nie można połączyć się z bazą danych!");
            }
        });
        Scene scene = new Scene(grid, 450, 350);
        stage.setScene(scene);
        stage.showAndWait();
    }
    private void ADD_Table(DB DB)
    {
        Stage stage = new Stage();
        if(DB.type.equals("MangoDB"))
            stage.setTitle("Dodaj kolekcję");
        else
            stage.setTitle("Dodaj tabelę");
        Button Button = new Button("Dodaj");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.setAlignment(Pos.TOP_CENTER);
        TextField name = new TextField();
        if(DB.type.equals("MangoDB"))
            name.setPromptText("Nazwa kolekcji");
        else
            name.setPromptText("Nazwa Tabeli");

        Text msg = new Text();
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setWrappingWidth(250);

        if(DB.type.equals("MangoDB"))
            grid.addRow(0, new Label("Nazwa kolekcji:"), name);
        else
            grid.addRow(0, new Label("Nazwa Tabeli:"), name);
        grid.add(msg, 0, 2, 2,1);
        GridPane.setHalignment(msg, HPos.CENTER);
        grid.add(Button, 0, 3, 2,1);
        GridPane.setHalignment(Button, HPos.CENTER);
        Button.setOnAction(event ->
        {
            switch (DB.type) {
                case "Cassandra" -> {
                    try (CqlSession connection = (CqlSession) DB.connectToDatabase()) {
                        connection.execute("CREATE TABLE IF NOT EXISTS " + name.getText() + " (id uuid PRIMARY KEY);");
                        reload();
                        stage.close();
                    } catch (Exception e) {
                        msg.setFill(Color.FIREBRICK);
                        msg.setText("ERROR: " + e.getMessage());
                    }
                }
                case "MangoDB" -> {
                    try {
                        MongoDatabase connection = (MongoDatabase) DB.connectToDatabase();
                        connection.createCollection(name.getText());
                        DB.mongoClient.close();
                        reload();
                        stage.close();
                    } catch (Exception e) {
                        DB.mongoClient.close();
                        msg.setFill(Color.FIREBRICK);
                        msg.setText("ERROR: " + e.getMessage());
                    }
                }
                default -> {
                    try (Connection connection = (Connection) DB.connectToDatabase()) {
                        Statement statement = connection.createStatement();
                        statement.executeUpdate("CREATE TABLE " + name.getText() + " (id INT PRIMARY KEY)");
                        reload();
                        stage.close();
                    } catch (Exception e) {
                        msg.setFill(Color.FIREBRICK);
                        msg.setText("SQL ERROR: " + e.getMessage());
                    }
                }
            }
        });
        Scene scene = new Scene(grid, 300, 150);
        stage.setScene(scene);
        stage.showAndWait();
    }
    private void Execute_SQL(DB DB)
    {
        Stage stage = new Stage();
        stage.setTitle("Zapytanie do bazy danych");

        TextArea textArea = new TextArea();
        textArea.setPrefHeight(1000);
        textArea.minHeight(1000);
        Text msg = new Text();
        msg.setWrappingWidth(300);
        msg.setTextAlignment(TextAlignment.CENTER);
        Button executeButton = new Button("Wykonaj");
        executeButton.setOnAction(Btnevent -> {
            Pattern COMMENT_PATTERN = Pattern.compile("^REM.*| --.*|/\\*(.|[\\r\\n])*?\\*/");
            String text = textArea.getText().trim();
            Matcher commentMatcher = COMMENT_PATTERN.matcher(text);
            text = commentMatcher.replaceAll("");
            String[] lines = text.split("\\r?\\n");
            List<String> sqlStatements = new ArrayList<>();
            StringBuilder currentStatement = new StringBuilder();

            for (String line : lines) {
                commentMatcher = COMMENT_PATTERN.matcher(line);
                line = commentMatcher.replaceAll("");
                line = line.trim();

                if (line.startsWith("--") || line.isEmpty() || line.equals(";")) {
                    continue;
                }

                currentStatement.append(line).append(" ");
                if (line.endsWith(";") || lines[lines.length-1].equals(line)) {
                    sqlStatements.add(currentStatement.toString().trim().replaceAll(";$", ""));
                    currentStatement.setLength(0);
                }
            }

                if(sqlStatements.size()>0) {
                    String Command = sqlStatements.get(0).split("\\s+")[0].toLowerCase();
                    if (sqlStatements.size() == 1 && Command.equals("select")) {
                        MainController.SetMain(4, DB, textArea.getText(), this);
                        msg.setText("");
                    } else {
                        if (DB.type.equals("Cassandra")) {
                            try (CqlSession connection = (CqlSession) DB.connectToDatabase()) {
                                for (String sql : sqlStatements) {
                                    if (sql.trim().isEmpty()) {
                                        continue;
                                    }
                                    connection.execute(sql);
                                }
                                msg.setText("Wykonano zapytanie");
                                msg.setFill(Color.GREEN);
                            } catch (Exception e) {
                                msg.setFill(Color.FIREBRICK);
                                msg.setText("SQL ERROR: " + e.getMessage());
                            }
                        } else {
                            try (Connection connection = (Connection) DB.connectToDatabase()) {
                                Statement statement = connection.createStatement();
                                for (String sql : sqlStatements) {
                                    if (sql.trim().isEmpty()) {
                                        continue;
                                    }
                                    System.out.println(sql);
                                    statement.executeUpdate(sql);
                                }
                                msg.setText("Wykonano zapytanie");
                                msg.setFill(Color.GREEN);
                            } catch (Exception e) {
                                msg.setFill(Color.FIREBRICK);
                                msg.setText("SQL ERROR: " + e.getMessage());
                            }
                        }
                    }
                }
                reload();

        });



        VBox vBox = new VBox(10, textArea,msg, executeButton);
        vBox.setAlignment(Pos.CENTER);
        vBox.setPadding(new Insets(20));

        Scene scene = new Scene(vBox, 450, 350);
        stage.setScene(scene);
        stage.showAndWait();
    }
    private boolean connectToDatabase(String type, String host, String login, String password, String databaseName, String path) {
        String url = null;
        String drive = null;
        switch (type) {
            case "Cassandra" -> {
                try {
                    CqlSession session;
                    if (login.equals("")) {
                        session = CqlSession.builder()
                                .addContactPoint(new InetSocketAddress(host, 9042))
                                .withLocalDatacenter("datacenter1")
                                .withKeyspace(CqlIdentifier.fromCql(databaseName))
                                .build();
                    } else {
                        session = CqlSession.builder()
                                .addContactPoint(new InetSocketAddress(host, 9042))
                                .withLocalDatacenter("datacenter1")
                                .withAuthCredentials(login, password)
                                .withKeyspace(CqlIdentifier.fromCql(databaseName))
                                .build();
                    }
                    session.close();
                    return true;
                } catch (Exception e) {
                    System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
                }
            }
            case "MangoDB" -> {
                if (host.equalsIgnoreCase("localhost"))
                    url = "mongodb://localhost:27017";
                else
                    url = "mongodb+srv://" + login + ":" + password + "@" + host + "/?retryWrites=true&w=majority";
                try (MongoClient mongoClient = MongoClients.create(url)) {
                    mongoClient.getDatabase(databaseName);
                    mongoClient.close();
                    return true;
                } catch (Exception e) {
                    System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
                }
            }
            case "MySQL" -> {
                url = "jdbc:mysql://" + host + "/" + databaseName + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "com.mysql.cj.jdbc.Driver";
            }
            case "Oracle" -> {
                url = "jdbc:oracle:thin:@" + host + ":1521/" + databaseName;
                drive = "oracle.jdbc.driver.OracleDriver";
            }
            case "PostgreSQL" -> {
                url = "jdbc:postgresql://" + host + "/" + databaseName + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "org.postgresql.Driver";
            }
            case "MariaDB" -> {
                url = "jdbc:mariadb://" + host + "/" + databaseName + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "org.mariadb.jdbc.Driver";
            }
            case "SQLite" -> {
                url = "jdbc:sqlite:" + path;
                drive = "org.sqlite.JDBC";
            }
        }


        try {
            Class.forName(drive);
            Connection connection = DriverManager.getConnection(url, login, password);
            connection.close();
            return true;
        } catch (Exception e) {
            System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
            return false;
        }
    }
}
