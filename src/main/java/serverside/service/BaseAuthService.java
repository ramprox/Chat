package serverside.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import serverside.interfaces.AuthService;
import serverside.model.User;

import java.sql.*;

public class BaseAuthService implements AuthService {

    private static final Logger LOGGER = LogManager.getLogger(BaseAuthService.class);

    public BaseAuthService() throws SQLException {
        Connection connection = DBConnection.getConnection();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "login VARCHAR(50) NOT NULL UNIQUE," +
                    "password VARCHAR(50) NOT NULL," +
                    "nick VARCHAR(50) NOT NULL UNIQUE" +
                    ");");
            ResultSet result = statement.executeQuery("SELECT * FROM users;");
            if(!result.next()) {
                statement.executeUpdate("INSERT INTO users (login, password, nick) values ('David', 'qazwsx', 'Давид')");
                statement.executeUpdate("INSERT INTO users (login, password, nick) values ('Viktor', 'qwerty', 'Виктор')");
                statement.executeUpdate("INSERT INTO users (login, password, nick) values ('Vladimir', '123456', 'Владимир')");
            }
            connection.commit();
        } catch (SQLException ex) {
            LOGGER.error("Ошибка при создании таблицы users в базе данных: " + ex.getMessage());
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void start() {
        LOGGER.info("Сервис аутентификации запущен");
    }

    @Override
    public void stop() {
        LOGGER.info("Сервис аутентификации остановлен");
    }

    @Override
    public User getUserByLoginAndPassword(String login, String password) throws SQLException {
        String queryNick = "SELECT * FROM users WHERE login=? AND password=?";
        try (PreparedStatement statement = DBConnection.getConnection().prepareStatement(queryNick)) {
            statement.setString(1, login);
            statement.setString(2, password);
            ResultSet result = statement.executeQuery();
            return User.userBuilder(result);
        }
    }
}
