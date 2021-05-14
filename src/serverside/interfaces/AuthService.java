package serverside.interfaces;

import serverside.model.User;

import java.sql.*;

public interface AuthService {
    void start();
    void stop();
    User getUserByLoginAndPassword(String login, String password) throws SQLException;
}
