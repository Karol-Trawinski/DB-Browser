package com.karol.trawinski.projekt;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.converter.DefaultStringConverter;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BrowserController {

    public TableView Table;
    public String item;

    public String Select;
    public DB DB;
    private List<String> Columns;

    private String PK;


    private ObservableList<Map<String, String>> data = FXCollections.observableArrayList();

    public void SetItem(DB DB, String item)
    {
        this.DB = DB;
        this.item = item;
        this.Select = null;
        this.PK = null;
        reload();
    }
    public void SetSelect(DB DB, String item)
    {
        this.DB = DB;
        this.item = null;
        this.Select = item;
        reload();
    }

    public void Get_Data()
    {
        Columns = new ArrayList<>();
        data = FXCollections.observableArrayList();
        if(DB.type.equals("Cassandra"))
        {
            try(CqlSession connection = (CqlSession) DB.connectToDatabase()) {
                String query;
                if (Select == null) {
                    query = "Select * From " + item;

                    TableMetadata tableMetadata = connection.getMetadata().getKeyspaces().get(CqlIdentifier.fromCql(DB.name)).getTable(item).get();
                    tableMetadata.getPrimaryKey().forEach(columnMetadata -> PK = columnMetadata.getName().asInternal());
                }
                else
                    query = Select;

                ResultSet resultSet = connection.execute(query);
                resultSet.getColumnDefinitions().forEach(column -> Columns.add(column.getName().asInternal()));

                for (Row row : resultSet) {
                    Map<String, String> x = new HashMap<>();
                    for (int i = 0; i < resultSet.getColumnDefinitions().size(); i++) {

                        Object value = row.getObject(i);
                        x.put(Columns.get(i), value != null ? value.toString() : "[NULL]");

                    }
                    data.add(x);
                }
            }catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }else
        {
            try(Connection connection = (Connection) DB.connectToDatabase()) {
                Statement statement = connection.createStatement();
                String query;
                if (Select == null) {
                    query = "Select * From " + item;
                    DatabaseMetaData databaseMetaData = connection.getMetaData();
                    java.sql.ResultSet resultSet = databaseMetaData.getPrimaryKeys(DB.name, null, item);

                    while (resultSet.next()) {
                        PK = resultSet.getString("COLUMN_NAME");
                    }
                }
                else
                    query = Select;
                java.sql.ResultSet rs = statement.executeQuery(query);
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    Columns.add(metaData.getColumnName(i));
                }

                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    for (String element : Columns) {
                        Object value = rs.getObject(element);
                        row.put(element, value != null ? value.toString() : "[NULL]");

                    }
                    data.add(row);
                }
            }catch (SQLException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }


    public void reload()
    {
        Table.getColumns().clear();
        Table.getItems().clear();

        if(DB != null)
        {
            Get_Data();
            for (String columnname : Columns) {
                TableColumn<Map<String, String>, String> x = new TableColumn<>(columnname);
                x.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(columnname)));

                x.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
                x.setOnEditCommit(event -> {
                    Map<String, String> row = event.getTableView().getItems().get(event.getTablePosition().getRow());
                    String id = row.get(PK);
                    event.getRowValue().put(columnname, event.getNewValue());
                    if (Table.getItems().size() - 1 != event.getTablePosition().getRow())
                        Update(row, id);

                });

                x.setMinWidth(100);
                Table.getColumns().add(x);
            }

            Table.setItems(data);
            if(Select == null) {
                Map<String, String> emptyRow = new HashMap<>();

                for (String column : Columns) {
                    emptyRow.put(column, "[NULL]");
                }
                Table.getItems().add(emptyRow);
                PseudoClass lastRow = PseudoClass.getPseudoClass("last-row");
                Table.setRowFactory(tv -> new TableRow() {
                    @Override
                    public void updateIndex(int index) {
                        super.updateIndex(index);
                        pseudoClassStateChanged(lastRow, index >= 0 && index == Table.getItems().size() - 1);
                    }
                });
            }
        }

        if(Select == null && PK !=null)
            Table.setEditable(true);
    }
    private void Update(Map<String, String> rowData,String ID) {
        StringBuilder queryBuilder = new StringBuilder("UPDATE " + item + " SET ");

        for (int i = 0; i < Columns.size(); i++) {
            if(DB.type.equals("Cassandra") && Columns.get(i).equals(PK))
                continue;

            queryBuilder.append(Columns.get(i)).append(" = ");
            String cell = rowData.get(Columns.get(i));
            if(Datatype(cell))
                queryBuilder.append(rowData.get(Columns.get(i)));
            else
                queryBuilder.append("'").append(rowData.get(Columns.get(i))).append("'");
            if (i < Columns.size() - 1) {
                queryBuilder.append(", ");
            }
        }
        queryBuilder.append(" WHERE ").append(PK).append(" = ");
        if(Datatype(ID))
            queryBuilder.append(ID);
        else
            queryBuilder.append("'").append(ID).append("'");

        if(DB.type.equals("Cassandra")) {
            try (CqlSession  connection = (CqlSession) DB.connectToDatabase()) {
                connection.execute(queryBuilder.toString());
            } catch (Exception e) {
                reload();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }else {
            try (Connection connection = (Connection) DB.connectToDatabase()) {
                Statement statement = connection.createStatement();
                statement.executeUpdate(queryBuilder.toString());
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


    private void deleteSelectedRow(Map<String, String> selectedRowData) {
        StringBuilder query = new StringBuilder();
        query.append("DELETE FROM ").append(item).append(" WHERE ")
                .append(PK).append(" = ");

        String ID = selectedRowData.get(PK);
        if(Datatype(ID))
            query.append(ID);
        else
            query.append("'").append(ID).append("'");

        if(DB.type.equals("Cassandra")) {
            try (CqlSession connection = (CqlSession ) DB.connectToDatabase()) {
                connection.execute(query.toString());
                Table.getItems().remove(selectedRowData);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }else {
            try (Connection connection = (Connection) DB.connectToDatabase()) {
                Statement Statement = connection.createStatement();
                Statement.executeUpdate(query.toString());
                Table.getItems().remove(selectedRowData);
            } catch (SQLException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("SQL ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }

    }
    public void TableClicked(MouseEvent mouseEvent) {

        if (Select == null && mouseEvent.getButton() == MouseButton.PRIMARY) {
            int selectedIndex = Table.getSelectionModel().getSelectedIndex();
            if(selectedIndex>=0) {
                if (selectedIndex == Table.getItems().size() - 1) {
                    Table.setEditable(true);
                } else
                {
                    if(PK ==null) Table.setEditable(false);
                }
            }
        }

        if (mouseEvent.getButton() == MouseButton.SECONDARY) {
            int selectedIndex = Table.getSelectionModel().getSelectedIndex();
            ContextMenu contextMenu = new ContextMenu();
            if (selectedIndex >= 0) {
                Map<String, String> selectedRowData = (Map<String, String>) Table.getItems().get(selectedIndex);
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

    private void ADDSelectedRow(Map<String, String> selectedRowData) {
        StringBuilder query = new StringBuilder("INSERT INTO " + item+" (");
        for (String column : Columns) {
            if (!selectedRowData.get(column).equals("[NULL]")) {
                query.append(column);
                query.append(",");
            }

        }
        query.deleteCharAt(query.length()-1);
        query.append(") VALUES (");
        for (String column : Columns) {
            if (!selectedRowData.get(column).equals("[NULL]")) {
                String cell = selectedRowData.get(column);
                if (Datatype(cell))
                    query.append(selectedRowData.get(column));
                else
                    query.append("'").append(selectedRowData.get(column)).append("'");
                query.append(",");
            }

        }
        query.deleteCharAt(query.length()-1);
        query.append(")");
        if(DB.type.equals("Cassandra")) {
            try (CqlSession  connection = (CqlSession) DB.connectToDatabase()) {
                connection.execute(query.toString());
                reload();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }else {
            try (Connection connection = (Connection) DB.connectToDatabase()) {
                Statement ADDStatement = connection.createStatement();
                ADDStatement.executeUpdate(query.toString());
                reload();
            } catch (SQLException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("SQL ERROR: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    public boolean Datatype(String str) {
        final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
        final Pattern BOOLEAN_PATTERN = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
        final Pattern FUNCTION_PATTERN = Pattern.compile("\\w+\\([^\\)]*\\)");
        final Pattern BINARY_PATTERN = Pattern.compile("0x[0-9A-Fa-f]+");
        final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
        if (NUMERIC_PATTERN.matcher(str).matches()) {
            return true;
        }
        if (BOOLEAN_PATTERN.matcher(str).matches()) {
            return true;
        }
        if (FUNCTION_PATTERN.matcher(str).matches()) {
            return true;
        }
        if (BINARY_PATTERN.matcher(str).matches()) {
            return true;
        }
        return UUID_PATTERN.matcher(str).matches();
    }
}
