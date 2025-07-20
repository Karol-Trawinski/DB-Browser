package com.karol.trawinski.projekt;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Font;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EditTableController {
    public TableView Table;
    public String item;
    public DB DB;
    public AnchorPane NamePanel;

    public SidebarController Sidebarcontroller;



    public void SetItem(DB DB, String item,SidebarController controller)
    {
        this.DB = DB;
        this.item = item;
        this.Sidebarcontroller = controller;
        reload();
    }

    public void reload()
    {
        Table.getColumns().clear();
        Table.getItems().clear();
        TableName();
        if(DB.type.equals("Cassandra"))
            cassandra();
        else
            OtherDB();

    }
    private void cassandra()
    {
        ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();
        try(CqlSession connection = (CqlSession) DB.connectToDatabase()) {
            Table.setEditable(false);
            TableMetadata tableMetadata = connection.getMetadata().getKeyspaces().get(CqlIdentifier.fromCql(DB.name)).getTable(item).get();
            tableMetadata.getColumns().forEach((c, v) -> {
                String columnName = c.asInternal();
                String columnType = v.getType().asCql(false, true);
                Map<String, Object> row = new HashMap<>();
                row.put("COLUMN_NAME", columnName);
                row.put("TYPE_NAME", columnType);
                data.add(row);
            });

        }catch (Exception e) {
            e.printStackTrace();
        }

        TableColumn<Map<String, Object>, String> name = new TableColumn<>("Nazwa");
        name.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get("COLUMN_NAME").toString()));
        name.setCellFactory(TextFieldTableCell.forTableColumn());
        name.setOnEditCommit(event -> event.getRowValue().put("COLUMN_NAME", event.getNewValue()));

        TableColumn<Map<String, Object>, String> typ = new TableColumn<>("Typ");
        typ.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get("TYPE_NAME").toString()));
        typ.setCellFactory(TextFieldTableCell.forTableColumn());
        typ.setOnEditCommit(event -> event.getRowValue().put("TYPE_NAME", event.getNewValue()));

        name.setMinWidth(100);
        typ.setMinWidth(100);

        Table.getColumns().addAll(name,typ);
        Table.setItems(data);

        Map<String, Object> emptyrow = new HashMap<>();
        emptyrow.put("COLUMN_NAME", "");
        emptyrow.put("TYPE_NAME", "");

        Table.getItems().add(emptyrow);
        PseudoClass lastRow = PseudoClass.getPseudoClass("last-row");
        Table.setRowFactory(tv -> new TableRow() {
            @Override
            public void updateIndex(int index) {
                super.updateIndex(index);
                pseudoClassStateChanged(lastRow,index >= 0 && index == Table.getItems().size() - 1);
            }
        });
    }

    private void OtherDB()
    {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            Table.setEditable(true);
            ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();
            DatabaseMetaData metaData = connection.getMetaData();

            Set<String> primaryKeys = new HashSet<>();
            ResultSet pkColumns = metaData.getPrimaryKeys(DB.name, null, item);
            while (pkColumns.next()) {
                primaryKeys.add(pkColumns.getString("COLUMN_NAME"));
            }

            ResultSet columns = metaData.getColumns(DB.name, null, item, null);
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME");
                if(DB.type.equals("MySQL")||DB.type.equals("MariaDB"))
                {
                    String query = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                    PreparedStatement Statement = connection.prepareStatement(query);
                    Statement.setString(1, DB.name);
                    Statement.setString(2, item);
                    Statement.setString(3, columnName);
                    ResultSet rs = Statement.executeQuery();
                    if (rs.next()) {
                        dataType = rs.getString("COLUMN_TYPE");
                    }
                }
                boolean isNullable = "YES".equals(columns.getString("IS_NULLABLE"));
                String defaultValue = columns.getString("COLUMN_DEF");
                boolean isAutoIncrement = false;
                if(DB.type.equals("Oracle"))
                {
                    if(defaultValue!=null && defaultValue.contains(".nextval"))
                        isAutoIncrement = true;
                }else
                    isAutoIncrement = "YES".equals(columns.getString("IS_AUTOINCREMENT"));
                boolean isPrimaryKey = primaryKeys.contains(columnName);
                Map<String, Object> row = new HashMap<>();
                row.put("COLUMN_NAME", columnName);
                row.put("TYPE_NAME", dataType);
                row.put("IS_NULLABLE", isNullable);
                row.put("COLUMN_DEF", (defaultValue==null?"":defaultValue));
                row.put("IS_AUTOINCREMENT", isAutoIncrement);
                row.put("PrimaryKey", isPrimaryKey);
                data.add(row);
            }

            TableColumn<Map<String, Object>, String> name = new TableColumn<>("Nazwa");
            name.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get("COLUMN_NAME").toString()));
            name.setCellFactory(TextFieldTableCell.forTableColumn());
            name.setOnEditCommit(event ->
            {
                Map<String, Object> row = event.getTableView().getItems().get(event.getTablePosition().getRow());
                String oldname = (String) row.get("COLUMN_NAME");
                event.getRowValue().put("COLUMN_NAME", event.getNewValue());
                if(Table.getItems().indexOf(row)!=Table.getItems().size()-1)
                    UpdateName(row, oldname);
            });

            TableColumn<Map<String, Object>, String> typ = new TableColumn<>("Typ");
            typ.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get("TYPE_NAME").toString()));
            typ.setCellFactory(TextFieldTableCell.forTableColumn());
            typ.setOnEditCommit(event ->
            {
                Map<String, Object> row = event.getTableView().getItems().get(event.getTablePosition().getRow());
                if(DB.type.equals("SQLite")&&Table.getItems().indexOf(row)!=Table.getItems().size()-1) {
                    event.getTableView().refresh();
                    return;
                }
                event.getRowValue().put("TYPE_NAME", event.getNewValue());
                if (Table.getItems().indexOf(row) != Table.getItems().size() - 1)
                    UpdateType(row);

            });

            TableColumn<Map<String, Object>, String> def = new TableColumn<>("Default");
            def.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get("COLUMN_DEF").toString()));
            def.setCellFactory(TextFieldTableCell.forTableColumn());
            def.setOnEditCommit(event ->
            {
                Map<String, Object> row = event.getTableView().getItems().get(event.getTablePosition().getRow());
                if(DB.type.equals("SQLite")&&Table.getItems().indexOf(row)!=Table.getItems().size()-1) {
                    event.getTableView().refresh();
                    return;
                }
                event.getRowValue().put("COLUMN_DEF", event.getNewValue());
                if(Table.getItems().indexOf(row)!=Table.getItems().size()-1)
                    UpdateDEF(row);
            });


            TableColumn<Map<String, Object>, Boolean> nullable = new TableColumn<>("Null");
            nullable.setCellValueFactory(param -> {
                Boolean checked = (Boolean) param.getValue().get("IS_NULLABLE");
                SimpleBooleanProperty property = new SimpleBooleanProperty(checked);
                property.addListener((observable, oldValue, newValue) -> {
                    Map<String, Object> row = param.getValue();
                    if(DB.type.equals("SQLite")&&Table.getItems().indexOf(row)!=Table.getItems().size()-1) {
                        param.getTableView().refresh();
                        return;
                    }
                    row.put("IS_NULLABLE", newValue);
                    if(Table.getItems().indexOf(row)!=Table.getItems().size()-1)
                        UpdateNull(row);

                });

                return property;

            });
            nullable.setCellFactory(CheckBoxTableCell.forTableColumn(nullable));

            TableColumn<Map<String, Object>, Boolean> AutoIncrement = new TableColumn<>("Auto Increment");
            AutoIncrement.setCellValueFactory(param -> {
                Boolean checked = (Boolean) param.getValue().get("IS_AUTOINCREMENT");
                SimpleBooleanProperty property = new SimpleBooleanProperty(checked);
                property.addListener((observable, oldValue, newValue) -> {
                    Map<String, Object> row = param.getValue();
                    if(DB.type.equals("SQLite")&&Table.getItems().indexOf(row)!=Table.getItems().size()-1) {
                        param.getTableView().refresh();
                        return;
                    }
                    row.put("IS_AUTOINCREMENT", newValue);
                    if(Table.getItems().indexOf(row)!=Table.getItems().size()-1)
                        UpdateAI(row);

                });

                return property;

            });
            AutoIncrement.setCellFactory(CheckBoxTableCell.forTableColumn(AutoIncrement));

            TableColumn<Map<String, Object>, Boolean> key = new TableColumn<>("PRIMARY KEY");
            key.setCellValueFactory(param -> {
                Boolean checked = (Boolean) param.getValue().get("PrimaryKey");
                SimpleBooleanProperty property = new SimpleBooleanProperty(checked);
                property.addListener((observable, oldValue, newValue) -> {
                    Map<String, Object> row = param.getValue();
                    if(DB.type.equals("SQLite")&&Table.getItems().indexOf(row)!=Table.getItems().size()-1) {
                        param.getTableView().refresh();
                        return;
                    }
                    row.put("PrimaryKey", newValue);
                    if(Table.getItems().indexOf(row)!=Table.getItems().size()-1)
                        UpdateKey(row);

                });

                return property;

            });
            key.setCellFactory(CheckBoxTableCell.forTableColumn(key));
            name.setMinWidth(100);
            typ.setMinWidth(100);
            def.setMinWidth(100);
            nullable.setMinWidth(100);
            key.setMinWidth(100);
            Table.getColumns().addAll(name,typ,def,nullable,AutoIncrement,key);
            Table.setItems(data);

            Map<String, Object> emptyrow = new HashMap<>();
            emptyrow.put("COLUMN_NAME", "");
            emptyrow.put("TYPE_NAME", "");
            emptyrow.put("IS_NULLABLE", false);
            emptyrow.put("COLUMN_DEF", "");
            emptyrow.put("IS_AUTOINCREMENT", false);
            emptyrow.put("PrimaryKey", false);
            Table.getItems().add(emptyrow);
            PseudoClass lastRow = PseudoClass.getPseudoClass("last-row");
            Table.setRowFactory(tv -> new TableRow() {
                @Override
                public void updateIndex(int index) {
                    super.updateIndex(index);
                    pseudoClassStateChanged(lastRow,index >= 0 && index == Table.getItems().size() - 1);
                }
            });


        }
        catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateName(Map<String, Object> row, String oldname) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            String query ="";
            switch (DB.type) {
                case "MariaDB", "MySQL" -> {
                    String AI = "";
                    String NULLABLE = " ";
                    String DEF = "";
                    if ((Boolean) row.get("IS_AUTOINCREMENT"))
                        AI = "AUTO_INCREMENT";
                    if (!(Boolean) row.get("IS_NULLABLE"))
                        NULLABLE = " NOT NULL ";
                    if (!row.get("COLUMN_DEF").equals(""))
                        DEF = "DEFAULT '" + row.get("COLUMN_DEF") + "'";
                    query = "ALTER TABLE " + item + " CHANGE COLUMN " + oldname + " " + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME") + NULLABLE + DEF + AI;
                }
                default ->
                        query = "ALTER TABLE " + item + " RENAME COLUMN " + oldname + " to " + row.get("COLUMN_NAME");
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateType(Map<String, Object> row) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            String query ="";
            switch (DB.type) {
                case "Oracle" ->
                        query = "ALTER TABLE " + item + " MODIFY (" + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME") + ")";
                case "MariaDB", "MySQL" -> {
                    String AI = "";
                    String DEF = "";
                    String NULLABLE = " ";
                    if ((Boolean) row.get("IS_AUTOINCREMENT"))
                        AI = "AUTO_INCREMENT";
                    if (!(Boolean) row.get("IS_NULLABLE"))
                        NULLABLE = " NOT NULL ";
                    if (!row.get("COLUMN_DEF").equals(""))
                        DEF = "DEFAULT '" + row.get("COLUMN_DEF") + "'";
                    query = "ALTER TABLE " + item + " MODIFY COLUMN " + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME") + NULLABLE + DEF + AI;
                }
                default ->
                        query = "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " TYPE " + row.get("TYPE_NAME");
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateDEF(Map<String, Object> row) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            String query ="";
            if (!row.get("COLUMN_DEF").equals("")){
                query = switch (DB.type) {
                    case "Oracle" ->
                            "ALTER TABLE " + item + " MODIFY " + row.get("COLUMN_NAME") + " DEFAULT '" + row.get("COLUMN_DEF") + "'";
                    case "MariaDB", "MySQL" ->
                            "ALTER TABLE " + item + " ALTER " + row.get("COLUMN_NAME") + " SET DEFAULT '" + row.get("COLUMN_DEF") + "'";
                    default ->
                            "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " SET DEFAULT '" + row.get("COLUMN_DEF") + "'";
                };
            }else{
                if (DB.type.equals("Oracle")) {
                    query = "ALTER TABLE " + item + " MODIFY " + row.get("COLUMN_NAME") + " DEFAULT NULL";
                } else {
                    query = "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " DROP DEFAULT";
                }
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateNull(Map<String, Object> row) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            String query = "";

            if ((Boolean) row.get("IS_NULLABLE"))
            {
                switch (DB.type) {
                    case "Oracle" -> query = "ALTER TABLE " + item + " MODIFY " + row.get("COLUMN_NAME") + " NULL ";
                    case "MariaDB", "MySQL" -> {
                        String AI = "";
                        if ((Boolean) row.get("IS_AUTOINCREMENT"))
                            AI = " AUTO_INCREMENT";
                        query = "ALTER TABLE " + item + " MODIFY COLUMN " + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME") + AI;
                    }
                    default ->
                            query = "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " DROP NOT NULL";
                }
            }else{
                switch (DB.type) {
                    case "Oracle" -> query = "ALTER TABLE " + item + " MODIFY " + row.get("COLUMN_NAME") + " NOT NULL ";
                    case "MariaDB", "MySQL" -> {
                        String AI = "";
                        if ((Boolean) row.get("IS_AUTOINCREMENT"))
                            AI = "AUTO_INCREMENT";
                        query = "ALTER TABLE " + item + " MODIFY COLUMN " + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME") + " NOT NULL " + AI;
                    }
                    default ->
                            query = "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " SET NOT NULL";
                }
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateAI(Map<String, Object> row) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            String query ="";
            if ((Boolean) row.get("IS_AUTOINCREMENT")) {
                switch (DB.type) {
                    case "Oracle" -> {
                        Map<String, Object> x = new HashMap<>(row);

                        x.put("COLUMN_NAME", row.get("COLUMN_NAME") + "_temp");
                        x.put("PrimaryKey", false);
                        if (ADDSelectedRow(x)) {
                            deleteSelectedRow(row);
                            if((Boolean)row.get("PrimaryKey")) {
                                x.put("PrimaryKey", true);
                                UpdateKey(x);
                            }
                            UpdateName(row, row.get("COLUMN_NAME") + "_temp");


                        }
                        return;
                    }
                    case "MariaDB", "MySQL" ->
                            query = "ALTER TABLE " + item + " MODIFY COLUMN " + row.get("COLUMN_NAME") + " INT AUTO_INCREMENT";
                    default -> {
                        String seq = item + "_" + row.get("COLUMN_NAME") + "_seq";
                        query = "CREATE SEQUENCE IF NOT EXISTS " + seq + "; ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " SET DEFAULT nextval('" + seq + "')";
                    }
                }
            }else{
                query = switch (DB.type) {
                    case "Oracle" -> "ALTER TABLE " + item + " MODIFY " + row.get("COLUMN_NAME") + " DROP IDENTITY";
                    case "MariaDB", "MySQL" ->
                            "ALTER TABLE " + item + " MODIFY COLUMN " + row.get("COLUMN_NAME") + " INT";
                    default -> "ALTER TABLE " + item + " ALTER COLUMN " + row.get("COLUMN_NAME") + " DROP DEFAULT";
                };
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }
    private void UpdateKey(Map<String, Object> row) {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            Statement statement = connection.createStatement();
            String query = "";
            if((Boolean)row.get("PrimaryKey"))
                query = "ALTER TABLE " + item + " ADD PRIMARY KEY (" + row.get("COLUMN_NAME") + ")";
            else
            {
                switch (DB.type) {
                    case "MariaDB", "MySQL", "Oracle" -> query = "ALTER TABLE " + item + " DROP PRIMARY KEY";
                    default -> {
                        query = "SELECT conname FROM pg_constraint WHERE conrelid = "
                                + "(SELECT oid FROM pg_class WHERE relname = '" + item + "') AND contype = 'p';";
                        String constraintName = null;
                        try (ResultSet rs = statement.executeQuery(query)) {
                            if (rs.next()) {
                                constraintName = rs.getString("conname");
                            }
                        }
                        query = "ALTER TABLE " + item + " DROP CONSTRAINT " + constraintName;
                    }
                }
            }
            statement.executeUpdate(query);
            reload();

        } catch (Exception e) {
            reload();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("SQL ERROR: "+ e.getMessage());
            alert.showAndWait();
        }
    }


    public void TableName()
    {
        Label label = new Label(item);
        label.setFont(new Font(24));
        if(!DB.type.equals("Cassandra")) {
            TextField textField = new TextField();

            label.setOnMouseClicked(event -> {
                textField.setText(label.getText());
                NamePanel.getChildren().remove(label);
                NamePanel.getChildren().add(textField);
                textField.requestFocus();
            });

            textField.setOnAction(event -> {
                try (Connection connection = (Connection) DB.connectToDatabase()) {
                    String query = "ALTER TABLE " + label.getText() + " RENAME TO " + textField.getText();
                    Statement statement = connection.createStatement();
                    statement.executeUpdate(query);
                    item = textField.getText();
                    label.setText(textField.getText());
                    NamePanel.getChildren().remove(textField);
                    NamePanel.getChildren().add(label);
                    Sidebarcontroller.reload();
                } catch (SQLException e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText("SQL ERROR: " + e.getMessage());
                    alert.showAndWait();
                }

            });
        }
        NamePanel.getChildren().add(label);

    }
    public void TableClicked(MouseEvent mouseEvent) {
        if (DB.type.equals("Cassandra") && mouseEvent.getButton() == MouseButton.PRIMARY) {
            int selectedIndex = Table.getSelectionModel().getSelectedIndex();
            if(selectedIndex>=0) {
                Table.setEditable(selectedIndex == Table.getItems().size() - 1);
            }
        }
        if (mouseEvent.getButton() == MouseButton.SECONDARY) {
            int selectedIndex = Table.getSelectionModel().getSelectedIndex();
            ContextMenu contextMenu = new ContextMenu();
            if (selectedIndex >= 0) {
                Map<String, Object> selectedRowData = (Map<String, Object>) Table.getItems().get(selectedIndex);
                if(selectedIndex == Table.getItems().size()-1) {
                    MenuItem AddMenuItem = new MenuItem("ADD");
                    AddMenuItem.setOnAction(event -> ADDSelectedRow(selectedRowData));
                    contextMenu.getItems().add(AddMenuItem);
                }else
                {
                    MenuItem deleteMenuItem = new MenuItem("Delete");

                    deleteMenuItem.setOnAction(event -> deleteSelectedRow(selectedRowData));
                    contextMenu.getItems().add(deleteMenuItem);
                }



            }
            Table.setContextMenu(contextMenu);

        }
    }
    private boolean ADDSelectedRow( Map<String, Object> row) {
        if(DB.type.equals("Cassandra"))
        {
            try(CqlSession connection = (CqlSession) DB.connectToDatabase()) {
                connection.execute("ALTER TABLE " + item + " ADD " + row.get("COLUMN_NAME") + " " + row.get("TYPE_NAME"));
                reload();
                return true;
            } catch (Exception e) {
                reload();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: "+ e.getMessage());
                alert.showAndWait();
                return false;
            }

        } else {
            try (Connection connection = (Connection) DB.connectToDatabase()) {
                Statement statement = connection.createStatement();
                StringBuilder query = new StringBuilder();
                query.append("ALTER TABLE ").append(item);
                if (DB.type.equals("Oracle"))
                    query.append(" ADD ");
                else
                    query.append(" ADD COLUMN ");

                query.append(row.get("COLUMN_NAME")).append(" ");
                if ((Boolean) row.get("IS_AUTOINCREMENT")) {
                    if (DB.type.equals("PostgreSQL")) {
                        query.append("SERIAL ");
                    } else {
                        query.append("INT ");
                    }
                } else
                    query.append(row.get("TYPE_NAME").equals("") ? "INT" : row.get("TYPE_NAME")).append(" ");

                if (!(Boolean) row.get("IS_NULLABLE") && !(Boolean) row.get("IS_AUTOINCREMENT") && !(DB.type.equals("Oracle") && !row.get("COLUMN_DEF").equals("")))
                    query.append("NOT NULL ");

                if (!row.get("COLUMN_DEF").equals("") && !(Boolean) row.get("IS_AUTOINCREMENT"))
                    query.append("DEFAULT ").append("'").append(row.get("COLUMN_DEF")).append("' ");

                if ((Boolean) row.get("IS_AUTOINCREMENT")) {
                    switch (DB.type) {
                        case "PostgreSQL":

                            break;
                        case "Oracle":
                            query.append("GENERATED BY DEFAULT AS IDENTITY");
                            break;
                        default:
                            query.append("AUTO_INCREMENT ");
                            break;
                    }
                }
                if (!DB.type.equals("Oracle") && ((Boolean) row.get("PrimaryKey") || ((Boolean) row.get("IS_AUTOINCREMENT") && !DB.type.equals("PostgreSQL")))) {
                    query.append("PRIMARY KEY");
                }
                statement.executeUpdate(query.toString());
                if (DB.type.equals("Oracle") && (Boolean) row.get("PrimaryKey"))
                    UpdateKey(row);
                reload();
                return true;
            } catch (Exception e) {
                reload();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("SQL ERROR: " + e.getMessage());
                alert.showAndWait();
                return false;
            }
        }
    }
    private void deleteSelectedRow( Map<String, Object> row) {
        if(DB.type.equals("Cassandra"))
        {
            try(CqlSession connection = (CqlSession) DB.connectToDatabase()) {
                connection.execute("ALTER TABLE " + item + " DROP " + row.get("COLUMN_NAME"));
                reload();
            } catch (Exception e) {
                reload();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: "+ e.getMessage());
                alert.showAndWait();
            }

        } else {
            try (Connection connection = (Connection) DB.connectToDatabase()) {

                Statement statement = connection.createStatement();

                String query = "ALTER TABLE " + item + " DROP COLUMN " + row.get("COLUMN_NAME");
                statement.executeUpdate(query);
                reload();

            } catch (Exception e) {
                reload();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("SQL ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }

    }
}
