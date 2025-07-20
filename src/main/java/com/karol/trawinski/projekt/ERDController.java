package com.karol.trawinski.projekt;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ERDController {
    public Canvas canvas;
    public ScrollPane scrollPane;
    public String item;
    public DB DB;


    public void SetItem(DB DB)
    {
        this.DB = DB;
        this.item = DB.name;
        reload();
    }
    private class Column {
        private final String name;
        private final String type;
        private Color color;

        private double x;
        private double y;

        private double width;
        private double height;
        private final boolean isPrimaryKey;

        private final Table table;

        public Column(Table table, String name, String type, boolean isPrimaryKey) {
            this.name = name;
            this.type = type;
            this.color = Color.BLACK;
            this.isPrimaryKey = isPrimaryKey;
            this.table = table;

        }
    }
    private class Table {
        String name;
        private List<Column> primaryKeys;
        private final List<Column> columns;
        private double x;
        private double y;
        private double width;
        private double height;
        private boolean isDragging;

        private boolean isUpdated;

        private double forcex;
        private double forcey;

        Table(String name) {
            this.name = name;
            this.primaryKeys = new ArrayList<>();
            this.columns = new ArrayList<>();
            this.x = 0;
            this.y = 0;
            this.forcex = 0;
            this.forcey = 0;
            this.isDragging = false;
            this.isUpdated = false;
            this.width = 100;
            this.height = 40;
        }

        public void ADDPrimaryKey(String columnName, String type) {
            primaryKeys.add(new Column(this, columnName, type, false));
            double minwidth = (columnName.length()+type.length()+3)*7+20;
            this.width = Math.max(minwidth, this.width);
            this.height += 40;
        }
        public void AddColumn(String columnName, String type, boolean isPrimaryKey) {
            columns.add(new Column(this, columnName, type, isPrimaryKey));
            double minwidth = (columnName.length()+type.length()+3)*7+20;
            if(isPrimaryKey)
                minwidth+= 50;
            this.width = Math.max(minwidth, this.width);
            this.height += 20;
        }
        public Column GetColumn(String columnName) {
            for(Column PK:primaryKeys)
            {
                if(PK.name.equals(columnName))
                    return PK;
            }

            for(Column colum:columns)
            {
                if(colum.name.equals(columnName))
                    return colum;
            }
            return null;
        }
        public void update() {
            isUpdated = forcex <= 0.1 && forcey <= 0.1;
            x += forcex;
            y += forcey;
        }
    }

    private final double circleRadius = 10;
    private Table selectedTable = null;

    private Relation selectedRelation = null;

    private Column selectedColumn = null;
    private final List<Table> allTables = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();

    public void printRelations() {
        try(Connection connection = (Connection) DB.connectToDatabase()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<List<Object>> Relations = new ArrayList<>();

            String user = null;
            if(DB.type.equals("Oracle"))
                user = DB.login.toUpperCase();
            ResultSet tables = metaData.getTables(item, user, null, new String[] { "TABLE" });
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                Table table = new Table(tableName);
                ResultSet primaryKeys = metaData.getPrimaryKeys(item, null, tableName);
                List<String> PrimaryKeys = new ArrayList<>();
                while (primaryKeys.next()) {
                    String primaryKeyName = primaryKeys.getString("COLUMN_NAME");
                    PrimaryKeys.add(primaryKeyName);
                }

                ResultSet foreignKeys = metaData.getImportedKeys(item, null, tableName);
                List<String> ForeignKeys = new ArrayList<>();
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    ForeignKeys.add(fkColumnName);
                }

                ResultSet columns = metaData.getColumns(item, null, tableName, null);
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    int indeks = PrimaryKeys.indexOf(columnName);
                    int indeks2 = ForeignKeys.indexOf(columnName);
                    if (indeks != -1) table.ADDPrimaryKey(columnName,columnType);
                    else table.AddColumn(columnName,columnType, indeks2 != -1);
                }
                allTables.add(table);
                foreignKeys = metaData.getImportedKeys(item, null, tableName);

                while (foreignKeys.next()) {
                    String fkTableName = foreignKeys.getString("FKTABLE_NAME");
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                    String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
                    String FK_NAME = foreignKeys.getString("FK_NAME");
                    RelationType relationType = determineRelationType(fkTableName, fkColumnName);
                    if(relationType!=null) {
                        List<Object> x = new ArrayList<>();
                        x.add(FK_NAME);
                        x.add(fkTableName);
                        x.add(pkTableName);
                        x.add(fkColumnName);
                        x.add(pkColumnName);
                        x.add(relationType);
                        Relations.add(x);

                    }
                }
            }
            for (List<Object> relation : Relations) {
                String FK_NAME = (String) relation.get(0);
                Table table1 = FindTable((String) relation.get(1));
                Table table2 = FindTable((String) relation.get(2));
                Column column1 = table1.GetColumn((String)relation.get(3));
                Column column2 = table2.GetColumn((String)relation.get(4));
                relations.add(new Relation(FK_NAME, table1, table2,column1,column2, (RelationType) relation.get(5)));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private Table FindTable(String name)
    {
        for (Table table : allTables) if(table.name.equals(name)) return table;
        return null;
    }
    private RelationType determineRelationType(String fkTableName, String fkColumnName) {

        try(Connection connection = (Connection) DB.connectToDatabase()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT COUNT(DISTINCT " + fkColumnName + ") FROM " + fkTableName);
            resultSet.next();
            int uniqueValuesCount = resultSet.getInt(1);

            resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + fkTableName);
            resultSet.next();
            int rowCount = resultSet.getInt(1);

            if (uniqueValuesCount == rowCount) {
                return RelationType.ONE_TO_ONE;
            } else if (uniqueValuesCount < rowCount) {
                return RelationType.ONE_TO_MANY;
            } else {
                return RelationType.MANY_TO_MANY;
            }
        }catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public void reload() {
        printRelations();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);


        canvas.setOnMousePressed(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();

            if(selectedRelation!=null)
                selectedRelation = null;

            if(selectedTable!=null) {
                List<Relation> TableRelation = FindTableRelations(selectedTable);
                for (Relation relation : TableRelation) {
                    relation.column1.color = Color.BLACK;
                    relation.column2.color = Color.BLACK;
                }
                selectedTable = null;

            }
            for (Table table : allTables) {
                if (isInsideTable(table, mouseX, mouseY)) {
                    selectedTable = table;
                    selectedTable.isDragging = true;
                    List<Column> allColumns = new ArrayList<>(table.columns);
                    allColumns.addAll(table.primaryKeys);
                    for(Column column : allColumns) {
                        if(isInsideColumn(column, mouseX, mouseY)) {
                            if(selectedColumn!=null) {
                                try(Connection connection = (Connection) DB.connectToDatabase()) {
                                    String name = "FK_"+selectedColumn.name+column.name;
                                    String query = "ALTER TABLE " + selectedColumn.table.name
                                            + " ADD CONSTRAINT "+name
                                            +" FOREIGN KEY (" + selectedColumn.name + ") "+ "REFERENCES " + table.name + "(" + column.name + ")";
                                    PreparedStatement statement = connection.prepareStatement(query);
                                    statement.executeUpdate();

                                    relations.add(new Relation(name,selectedColumn.table,column.table,selectedColumn,column,determineRelationType(selectedColumn.table.name,selectedColumn.name)));
                                }catch (SQLException e) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Error");
                                    alert.setHeaderText(null);
                                    alert.setContentText("SQL ERROR: "+ e.getMessage());
                                    alert.showAndWait();
                                }
                                selectedColumn.color = Color.BLACK;
                                selectedColumn=null;

                            }else {
                                selectedColumn = column;
                                selectedColumn.color = Color.GREEN;
                            }
                            selectedTable.isDragging = false;
                            redrawCanvas(gc);
                            return;
                        }
                    }
                    redrawCanvas(gc);
                    return;
                }
            }
            if(selectedColumn!=null) {
                selectedColumn.color = Color.BLACK;
                selectedColumn = null;
            }

            for(Relation relation:relations)
            {
                if(isInsideRelation(relation,mouseX,mouseY))
                {
                    selectedRelation = relation;
                    break;
                }
            }
            redrawCanvas(gc);
        });

        canvas.setOnMouseDragged(event -> {

            if (selectedTable != null && selectedTable.isDragging) {
                double mouseX = event.getX();
                double mouseY = event.getY();
                selectedTable.x = mouseX;
                selectedTable.y = mouseY;
                redrawCanvas(gc);
            }

        });
        canvas.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            if(selectedColumn!=null)
            {
                redrawCanvas(gc);
                gc.setStroke(Color.GREEN);
                drawArrow(gc,selectedColumn.x+20,selectedColumn.y+10,mouseX,mouseY);

            }
        });
        canvas.setOnMouseReleased(event -> {
            if (selectedTable != null) {
                selectedTable.isDragging = false;
                redrawCanvas(gc);
            }
        });

        scrollPane.setOnKeyPressed(event -> {
            if (selectedRelation!=null && event.getCode() == KeyCode.DELETE) {
                try(Connection connection = (Connection) DB.connectToDatabase()) {
                    String query;
                    if(DB.type.equals("PostgreSQL")||DB.type.equals("Oracle"))
                        query = "ALTER TABLE " + selectedRelation.startTable.name + " DROP CONSTRAINT " + selectedRelation.name;
                    else
                        query = "ALTER TABLE " + selectedRelation.startTable.name + " DROP FOREIGN KEY " + selectedRelation.name;
                    PreparedStatement statement = connection.prepareStatement(query);
                    statement.executeUpdate();
                    relations.remove(selectedRelation);
                }catch (SQLException e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText("SQL ERROR: "+ e.getMessage());
                    alert.showAndWait();
                }

                redrawCanvas(gc);
            }
            });

        initializeTablePositions();

        redrawCanvas(gc);
        int i = 0;
        while (CheckTables()&&i<=10000)
        {

            UpdateTablePositions();
            for (Table node : allTables) {
                node.update();
            }
            i++;
        }
        FixTablePositions();
        redrawCanvas(gc);

    }
    private Color getRandomColor() {
        Random random = new Random();
        double red = random.nextDouble();
        double green = random.nextDouble();
        double blue = random.nextDouble();
        return new Color(red, green, blue, 1.0);
    }

    private void FixTablePositions() {
        double minx = 0;
        double miny = 0;
        for (Table table : allTables)
        {
            minx = Math.min(minx, table.x);
            miny = Math.min(miny, table.y);
        }
        for (Table table : allTables)
        {
            table.x +=-minx+10;
            table.y +=-miny+10;
        }
    }

    private void initializeTablePositions() {
        Random random = new Random();
        for (Table table : allTables) {
            table.x = random.nextDouble();
            table.y = random.nextDouble();
        }
    }
    private boolean CheckTables() {
        for (Table table1 : allTables) {
            for (Table table2 : allTables) {
                if(!table1.isUpdated) return true;
                if (table1 != table2 && isColliding(table1, table2)) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean isColliding(Table table1, Table table2) {
        double x1 = table1.x;
        double y1 = table1.y;
        double width1 = table1.width;
        double height1 = table1.height;


        return isInsideTable(table2, x1, y1)
                || isInsideTable(table2, x1 + width1, y1)
                || isInsideTable(table2, x1, y1 + height1)
                || isInsideTable(table2, x1 + width1, y1 + height1);

    }
    private void UpdateTablePositions()
    {
        double gravityConstant = 0.008;
        double forceConstant = 250;
        for (Table table : allTables) {

            table.forcex = (-table.x+table.width/2) * gravityConstant;
            table.forcey = (-table.y+table.height/2) * gravityConstant;
        }
        for (Table table1 : allTables) {
            for (Table table2 : allTables) {
                if (table1 != table2) {
                    double centerX1 = table1.x+table1.width/2;
                    double centerY1 = table1.y+table1.height/2;
                    double centerX2 = table2.x+table2.width/2;
                    double centerY2 = table2.y+table2.height/2;
                    double dx = centerX2 - centerX1;
                    double dy = centerY2 - centerY1;
                    double Distance = Math.sqrt(dx * dx + dy * dy);
                    double forceX = forceConstant * dx / (Distance*Distance);
                    double forceY = forceConstant * dy / (Distance*Distance);
                    table1.forcex -= forceX;
                    table1.forcey -= forceY;
                    table2.forcex += forceX;
                    table2.forcey += forceY;

                }
            }
        }
        for (Relation e : relations) {
            if(e.startTable!= e.endTable) {
                double centerX1 = e.startTable.x + e.startTable.width / 2;
                double centerY1 = e.startTable.y + e.startTable.height / 2;
                double centerX2 = e.endTable.x + e.endTable.width / 2;
                double centerY2 = e.endTable.y + e.endTable.height / 2;
                double dx = centerX1 - centerX2;
                double dy = centerY1 - centerY2;
                e.startTable.forcex -= dx / forceConstant;
                e.startTable.forcey -= dy / forceConstant;
                e.endTable.forcex += dx / forceConstant;
                e.endTable.forcey += dy / forceConstant;
            }

        }
    }
    private void redrawCanvas(GraphicsContext gc) {
        double maxX = scrollPane.getWidth();
        double maxY = scrollPane.getHeight();
        for (Table table : allTables) {
            maxX = Math.max(maxX, table.x + table.width+100);
            maxY = Math.max(maxY, table.y + table.height+100);
        }

        canvas.setWidth(maxX);
        canvas.setHeight(maxY);

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (Relation relation : relations) {
            DrawRelations(gc, relation);
        }

        for (Table table : allTables) {
            drawTable(gc, table);
        }
    }

    private void drawTable(GraphicsContext gc, Table table) {

        gc.setFill(Color.WHITE);
        gc.fillRect(table.x, table.y, table.width, table.height);

        gc.setStroke(Color.BLACK);
        gc.strokeRect(table.x, table.y, table.width, table.height);

        gc.setFill(Color.LIGHTYELLOW);
        gc.fillRect(table.x, table.y, table.width, 30);
        gc.setFill(Color.BLACK);

        int PosY = 20;
        gc.fillText(table.name, table.x + 10, table.y + PosY);
        PosY += 10;

        gc.strokeLine(table.x, table.y + PosY, table.x + table.width, table.y + PosY);
        PosY+=20;
        for (Column PK: table.primaryKeys) {
            gc.setFill(PK.color);
            gc.fillText(PK.name + " (" + PK.type + ')', table.x + 10, table.y + PosY);
            PK.x = table.x;
            PK.y = table.y + PosY - 15;
            PK.width = table.width;
            PK.height = 20;
            PosY += 20;
        }
        gc.strokeLine(table.x, table.y + PosY, table.x + table.width, table.y + PosY);
        PosY += 20;
        for (Column column: table.columns) {
            gc.setFill(column.color);
            if(column.isPrimaryKey)
                gc.fillText("<<FK>> "+column.name+  " (" + column.type + ')', table.x + 10, table.y + PosY);
            else
                gc.fillText(column.name+  " (" + column.type + ')', table.x + 10, table.y + PosY);

            column.x = table.x;
            column.y = table.y + PosY-15;
            column.width = table.width;
            column.height = 20;
            PosY+=20;
        }
    }
    private void DrawRelations(GraphicsContext gc, Relation relation) {
        if(selectedTable == relation.endTable || selectedTable == relation.startTable)
        {
            if(relation.column1.color!=Color.GREEN)
                relation.column1.color = relation.color;
            if(relation.column2.color!=Color.GREEN)
                relation.column2.color = relation.color;
        }
        if(selectedRelation == relation) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(4);
        }
        else
            gc.setStroke(relation.color);

        if(relation.startTable == relation.endTable)
        {
            Random random = new Random(relation.name.hashCode() & 0xffffffffL);


            Double randx = random.nextDouble(10,relation.startTable.width-10);
            Double randy = random.nextDouble(10,relation.startTable.height-10);
            Boolean edge1 = random.nextBoolean();
            Boolean edge2 = random.nextBoolean();
            double x1 = relation.startTable.x+randx;
            double y1 = relation.startTable.y+(edge1?1:0)*relation.startTable.height;
            double x2 = relation.startTable.x+(edge2?1:0)*relation.startTable.width;
            double y2 = relation.startTable.y+randy;

            double x3 = x2+(2*circleRadius+ 20)*(edge2?1:-1);
            double y3 = y1 + (2*circleRadius+20)*(edge1?1:-1);

            switch (relation.type) {
                case ONE_TO_ONE -> {
                    drawCircle(gc, x1, y1 + circleRadius*(edge1?1:-1));
                    drawCircle(gc,x2 + circleRadius*(edge2?1:-1), y2);
                    gc.beginPath();
                    gc.moveTo(x1,y1 + 2*circleRadius*(edge1?1:-1));
                    gc.lineTo(x1,y3);
                    gc.lineTo(x3,y3);
                    gc.lineTo(x3,y2);
                    gc.lineTo(x2 + 2*circleRadius*(edge2?1:-1) ,y2);
                    gc.stroke();
                    gc.fillText("1:1", x3 - (x3-x1)/2, y3-5);
                }
                case ONE_TO_MANY -> {
                    drawCircle(gc, x1, y1 + circleRadius*(edge1?1:-1));
                    gc.beginPath();
                    gc.moveTo(x1,y1 + 2*circleRadius*(edge1?1:-1));
                    gc.lineTo(x1,y3);
                    gc.lineTo(x3,y3);
                    gc.lineTo(x3,y2);
                    gc.lineTo(x2 ,y2);
                    gc.stroke();
                    gc.strokeLine(x2, y2, x2 + 8*(edge2?1:-1), y2+5);
                    gc.strokeLine(x2, y2, x2 + 8*(edge2?1:-1), y2-5);
                    gc.fillText("1:N", x3 - (x3-x1)/2, y3-5);
                }
                case MANY_TO_MANY -> {
                    gc.strokeLine(x1, y1, x1 + 5 , y1+ 8*(edge1?1:-1));
                    gc.strokeLine(x1, y1, x1 - 5 , y1+ 8*(edge1?1:-1));
                    gc.beginPath();
                    gc.moveTo(x1,y1);
                    gc.lineTo(x1,y3);
                    gc.lineTo(x3,y3);
                    gc.lineTo(x3,y2);
                    gc.lineTo(x2 ,y2);
                    gc.stroke();
                    gc.strokeLine(x2, y2, x2 + 8*(edge2?1:-1), y2+5);
                    gc.strokeLine(x2, y2, x2 + 8*(edge2?1:-1), y2-5);
                    gc.fillText("N:N", x3 - (x3-x1)/2, y3-5);

                }
            }
            gc.setLineWidth(2);
            gc.setStroke(Color.BLACK);
            return;
        }
        double HalfWidth1 = relation.endTable.width/2;
        double HalfHeight1 = relation.endTable.height/2;
        double HalfWidth2 = relation.startTable.width/2;
        double HalfHeigh2 = relation.startTable.height/2;

        double startX = relation.endTable.x+HalfWidth1;
        double startY = relation.endTable.y+HalfHeight1;
        double endX = relation.startTable.x+HalfWidth2;
        double endY = relation.startTable.y+HalfHeigh2;

        double angle = Math.atan2(endY - startY, endX - startX);

        double stopnie = angle * 180 / Math.PI;

        double tan = Math.tan(angle);


        if(stopnie>=-45&&stopnie<=45) {
            startX = startX + HalfWidth1;
            startY = startY + HalfHeight1 * tan;
            endX = endX - HalfWidth2;
            endY = endY - HalfHeigh2 * tan;
        }
        else if(stopnie>=45&&stopnie<=135)
        {
            startX = startX + HalfWidth1 / tan;
            startY = startY + HalfHeight1;
            endX = endX - HalfWidth2 / tan;
            endY = endY - HalfHeigh2 ;
        }
        else if(stopnie>=-135&&stopnie<=-45)
        {
            startX = startX - HalfWidth1 / tan;
            startY = startY - HalfHeight1;
            endX = endX + HalfWidth2 / tan;
            endY = endY + HalfHeigh2;
        }
        else
        {
            startX = startX - HalfWidth1;
            startY = startY - HalfHeight1 * tan;
            endX = endX + HalfWidth2;
            endY = endY + HalfHeigh2 * tan;
        }

        double CircleX = circleRadius * Math.cos(angle);
        double CircleY = circleRadius * Math.sin(angle);


        switch (relation.type) {
            case ONE_TO_ONE -> {

                drawCircle(gc, startX + CircleX, startY + CircleY);
                drawCircle(gc, endX - CircleX, endY - CircleY);
                gc.strokeLine(startX + 2 * CircleX, startY + 2 * CircleY, endX - 2 * CircleX, endY - 2 * CircleY);
                gc.fillText("1:1", (startX + endX) / 2, (startY + endY) / 2);
            }
            case ONE_TO_MANY -> {
                drawCircle(gc, startX + CircleX, startY + CircleY);
                drawArrow(gc, startX + 2 * CircleX, startY + 2 * CircleY, endX, endY);
                gc.fillText("1:N", (startX + endX) / 2, (startY + endY) / 2);
            }
            case MANY_TO_MANY -> {
                drawArrow(gc, startX, startY, endX, endY);
                drawArrow(gc, endX, endY, startX, startY);
                gc.fillText("N:N", (startX + endX) / 2, (startY + endY) / 2);
            }
        }
        gc.setLineWidth(2);
        gc.setStroke(Color.BLACK);
    }




    private void drawCircle(GraphicsContext gc, double x, double y) {
        gc.strokeOval(x - circleRadius, y - circleRadius, 2 * circleRadius, 2 * circleRadius);
    }
    private void drawArrow(GraphicsContext gc, double startX, double startY, double endX, double endY) {
        double angle = Math.atan2(endY - startY, endX - startX);

        double arrowSize = 10;
        double x1 = endX - arrowSize * Math.cos(angle - Math.toRadians(30));
        double y1 = endY - arrowSize * Math.sin(angle - Math.toRadians(30));
        double x2 = endX - arrowSize * Math.cos(angle + Math.toRadians(30));
        double y2 = endY - arrowSize * Math.sin(angle + Math.toRadians(30));
        gc.strokeLine(startX, startY, endX, endY);
        gc.strokeLine(endX, endY, x1, y1);
        gc.strokeLine(endX, endY, x2, y2);
    }

    private boolean isInsideTable(Table table, double x, double y) {
        return x >= table.x && x <= (table.x + table.width) && y >= table.y && y <= (table.y + table.height);
    }

    private boolean isInsideColumn(Column column, double x, double y) {
        return x >= column.x && x <= (column.x + column.width) && y >= column.y && y <= (column.y + column.height);
    }
    private boolean isInsideRelation(Relation relation, double x, double y) {
        if(relation.endTable == relation.startTable) {
            Random random = new Random(relation.name.hashCode() & 0xffffffffL);

            Double randx = random.nextDouble(10, relation.startTable.width - 10);
            random.nextDouble(10,relation.startTable.height-10);
            Boolean edge1 = random.nextBoolean();
            Boolean edge2 = random.nextBoolean();
            double x1 = relation.startTable.x + randx;
            double y1 = relation.startTable.y + (edge1 ? 1 : 0) * relation.startTable.height;
            double x2 = relation.startTable.x + (edge2 ? 1 : 0) * relation.startTable.width;

            double x3 = x2 + (2 * circleRadius + 20) * (edge2 ? 1 : -1);
            double y3 = y1 + (2 * circleRadius + 20) * (edge1 ? 1 : -1);
            double distance = Math.sqrt(Math.pow(x - (x3+10 - (x3 - x1) / 2), 2) + Math.pow(y - (y3 - 5), 2));

            return distance <= 30;
        }

        double centerX1 = relation.startTable.x + relation.startTable.width/2;
        double centerY1 = relation.startTable.y + relation.startTable.height/2;
        double centerX2 = relation.endTable.x + relation.endTable.width/2;
        double centerY2 = relation.endTable.y + relation.endTable.height/2;

        double distance;
        if(x>=Math.min(centerX1,centerX2) && x<=Math.max(centerX1,centerX2) && y>=Math.min(centerY1,centerY2) && y<=Math.max(centerY1,centerY2))
        {

            if (centerX1 == centerX2) {
                distance = Math.abs(x - centerX1);
                return distance <= 15;

            }
            double dx = centerX2 - centerX1;
            double dy = centerY2 - centerY1;
            double m = dy / dx;
            double b = centerY1 - m * centerX1;
            distance = Math.abs(m*x-y+b)/Math.sqrt(m * m + 1);
            return distance <= 15;
        }

        return false;
    }

    private enum RelationType {
        ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
    }

    private List<Relation> FindTableRelations(Table table)
    {
        List<Relation> x = new ArrayList<>();
        for(Relation relation:relations)
        {
            if(relation.startTable == table||relation.endTable == table)
                x.add(relation);
        }
        return x;
    }
    private class Relation {
        String name;
        Table startTable;
        Table endTable;

        Column column1;
        Column column2;
        RelationType type;


        Color color;

        Relation(String name, Table startTable, Table endTable,Column column1, Column column2, RelationType type) {
            this.startTable = startTable;
            this.endTable = endTable;
            this.column1 = column1;
            this.column2 = column2;
            this.type = type;
            this.color = getRandomColor();
            this.name = name;
        }
    }
}
