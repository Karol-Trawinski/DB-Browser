package com.karol.trawinski.projekt;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import javafx.scene.control.TreeItem;

import java.net.InetSocketAddress;
import java.sql.*;

public class DB {
    public String type;

    public String id;
    public String host;
    public String login;
    public String password;


    public String name;
    public boolean actived = false;
    public TreeItem<String> MenuItem;
    public MongoClient mongoClient;
    public DB(String type, String id,String host,String login,String password,String name)
    {
        this.type = type;
        this.id = id;
        this.host = host;
        this.login = login;
        this.password = password;
        this.name = name;
        MenuItem = new TreeItem<>(id);
    }
    public void reload()
    {
        MenuItem.getChildren().clear();
        if(actived) {
            switch (type) {
                case "Cassandra" -> {
                    try (CqlSession session = (CqlSession) connectToDatabase()) {
                        Metadata metadata = session.getMetadata();

                        KeyspaceMetadata keyspaceMetadata = metadata.getKeyspaces().get(CqlIdentifier.fromCql(name));
                        for (TableMetadata table : keyspaceMetadata.getTables().values()) {
                            TreeItem<String> Table = new TreeItem<>(table.getName().asCql(true));
                            MenuItem.getChildren().add(Table);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "MangoDB" -> {
                    try {
                        MongoDatabase connection = (MongoDatabase) connectToDatabase();
                        MongoIterable<String> collectionNames = connection.listCollectionNames();
                        for (String name : collectionNames) {
                            TreeItem<String> Table = new TreeItem<>(name);
                            MenuItem.getChildren().add(Table);
                        }
                        mongoClient.close();
                    } catch (MongoException e) {
                        e.printStackTrace();
                        mongoClient.close();
                    }
                }
                default -> {
                    try (Connection connection = (Connection) connectToDatabase()) {
                        DatabaseMetaData metaData = connection.getMetaData();
                        String user = null;
                        if (type.equals("Oracle"))
                            user = login.toUpperCase();
                        ResultSet tables = metaData.getTables(name, user, null, new String[]{"TABLE"});
                        while (tables.next()) {
                            String tableName = tables.getString("TABLE_NAME");
                            TreeItem<String> Table = new TreeItem<>(tableName);
                            MenuItem.getChildren().add(Table);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public Object connectToDatabase() {
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
                                .withKeyspace(CqlIdentifier.fromCql(name))
                                .build();
                    } else {
                        session = CqlSession.builder()
                                .addContactPoint(new InetSocketAddress(host, 9042))
                                .withLocalDatacenter("datacenter1")
                                .withAuthCredentials(login, password)
                                .withKeyspace(CqlIdentifier.fromCql(name))
                                .build();
                    }
                    return session;
                } catch (Exception e) {
                    System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
                }
            }
            case "MangoDB" -> {
                if (host.equalsIgnoreCase("localhost"))
                    url = "mongodb://localhost:27017";
                else
                    url = "mongodb+srv://" + login + ":" + password + "@" + host + "/?retryWrites=true&w=majority";
                try {
                    mongoClient = MongoClients.create(url);
                    return mongoClient.getDatabase(name);
                } catch (MongoException e) {
                    System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
                }
            }
            case "MySQL" -> {
                url = "jdbc:mysql://" + host + "/" + name + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "com.mysql.cj.jdbc.Driver";
            }
            case "Oracle" -> {
                url = "jdbc:oracle:thin:@" + host + ":1521/" + name;
                drive = "oracle.jdbc.driver.OracleDriver";
            }
            case "PostgreSQL" -> {
                url = "jdbc:postgresql://" + host + "/" + name + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "org.postgresql.Driver";
            }
            case "MariaDB" -> {
                url = "jdbc:mariadb://" + host + "/" + name + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";
                drive = "org.mariadb.jdbc.Driver";
            }
            case "SQLite" -> {
                url = "jdbc:sqlite:" + host;
                drive = "org.sqlite.JDBC";
            }
        }
        try {
            Class.forName(drive);
            return DriverManager.getConnection(url, login, password);
        } catch (Exception e) {
            System.out.println("Błąd podczas nawiązywania połączenia z bazą danych: " + e.getMessage());
            return null;
        }
    }

    public void activate()
    {
        actived = true;
    }
}

