package clientside;

import clientside.service.Client;

import java.awt.*;

public class MainClientApp {
    public static void main(String[] args) {
        EventQueue.invokeLater(Client::new);
    }
}
