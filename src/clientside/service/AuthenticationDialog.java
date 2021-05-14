package clientside.service;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class AuthenticationDialog extends JDialog {

    private boolean ok;
    private JTextField txtLogin;
    private JTextField txtPassword;
    private JButton btnOK;

    public String getLogin() {
        return txtLogin.getText();
    }

    public String getPassword() {
        return txtPassword.getText();
    }

    public void clearLoginAndPassword() {
        txtLogin.setText("");
        txtPassword.setText("");
    }

    public AuthenticationDialog(JFrame owner) {
        super(owner, true);
        setTitle("Аутентификация");
        setSize(300, 150);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        setResizable(false);
        addLoginAndPasswordPanel();
        addOkAndNoButtonsPanel();
        getRootPane().setDefaultButton(btnOK);
    }

    public boolean showDialog() {
        ok = false;
        setVisible(true);
        return ok;
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        txtLogin.grabFocus();
    }

    private void addLoginAndPasswordPanel() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 5, 10);
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        add(panel, c);

        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        JLabel lbl = new JLabel("Логин:");
        panel.add(lbl, c);

        c = new GridBagConstraints();
        c.gridy = 0;
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 10, 5, 0);
        c.anchor = GridBagConstraints.LINE_START;
        txtLogin = new JTextField();
        Border border = BorderFactory.createLineBorder(Color.BLACK, 1);
        Border padding = BorderFactory.createMatteBorder(3, 5, 3, 5, txtLogin.getBackground());
        txtLogin.setBorder(BorderFactory.createCompoundBorder(border, padding));
        panel.add(txtLogin, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.LINE_START;
        lbl = new JLabel("Пароль:");
        panel.add(lbl, c);

        c = new GridBagConstraints();
        c.gridy = 1;
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 10, 0, 0);
        txtPassword = new JTextField();
        txtPassword.setBorder(BorderFactory.createCompoundBorder(border, padding));
        panel.add(txtPassword, c);
    }

    private void addOkAndNoButtonsPanel() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 1;
        JPanel panel = new JPanel();
        add(panel, c);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        btnOK = new JButton("OK");
        btnOK.addActionListener(e -> {
            if(txtLogin.getText().isEmpty() || txtPassword.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Не указан логин или пароль",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            } else {
                ok = true;
                setVisible(false);
            }
        });
        panel.add(btnOK, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> {
            setVisible(false);
        });
        panel.add(btnCancel, c);
    }
}
