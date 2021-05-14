package serverside.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

public class DBConnection {
    private static final String DB = "jdbc:mysql://localhost:3306/chat";
    private static final String USER = "root";
    private static final String PASSWORD = "root";
    private static Connection dbConnection;

    private static final Logger LOGGER = LogManager.getLogger(DBConnection.class);
    
    public static Connection getConnection() throws SQLException {
        /*try {
            Class.forName("com.mysql.cj.jdbc.Driver");   // в версии mysql connector 8.0.23 писать это необязательно
        } catch (ClassNotFoundException e) {             // т.к. этот класс загружается автоматически
            e.printStackTrace();
        }*/
        if(dbConnection == null) {
            dbConnection = DriverManager.getConnection(DB, USER, PASSWORD);
            LOGGER.info("Соединение с базой данных установлено");
        }
        return dbConnection;
    }
    
    public static void closeConnection() {
        try {
            if(dbConnection != null) {
                dbConnection.close();
            }
            LOGGER.info("Соединение с базой данных закрыто.");
        } catch(SQLException ex) {
            LOGGER.error("Ошибка при закрытии соединения с базой данных: " + ex.getMessage());
        } finally {
            dbConnection = null;
        }
    }
}
