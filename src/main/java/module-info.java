module com.karol.trawinski.projekt {
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.xerial.sqlitejdbc;
    requires java.sql;
    requires org.json;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires java.driver.core;


    opens com.karol.trawinski.projekt to javafx.fxml;
    exports com.karol.trawinski.projekt;
}