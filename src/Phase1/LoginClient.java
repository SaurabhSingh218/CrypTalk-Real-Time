package Phase1;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class LoginClient extends JFrame {

    private static final String SECRET_KEY = "secretkey";
    private final RC4 rc4 = new RC4();
    private final byte[] keyBytes = SECRET_KEY.getBytes();

    private JFrame frame;
    private JTextField clientUserName;
    private int port = 8818;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                LoginClient window = new LoginClient();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public LoginClient() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame();
        frame.setBounds(100, 100, 619, 342);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(null);
        frame.setTitle("Client Register");

        clientUserName = new JTextField();
        clientUserName.setBounds(207, 50, 276, 61);
        frame.getContentPane().add(clientUserName);
        clientUserName.setColumns(10);

        JButton clientLoginBtn = new JButton("Connect");
        clientLoginBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String id = clientUserName.getText();
                    if (id == null || id.trim().isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Please enter a username");
                        return;
                    }

                    // Encrypt username bytes and convert to hex
                    byte[] encryptedBytes = rc4.encrypt(keyBytes, id.getBytes("UTF-8"));
                    String encryptedId = helper.bytesToHex(encryptedBytes);

                    Socket s = new Socket("localhost", port);
                    DataOutputStream outStream = new DataOutputStream(s.getOutputStream());
                    outStream.writeUTF(encryptedId);

                    DataInputStream inputStream = new DataInputStream(s.getInputStream());
                    String encryptedResponse = inputStream.readUTF();
                    byte[] decryptedBytes = rc4.decrypt(keyBytes, helper.hexToBytes(encryptedResponse));
                    String msgFromServer = new String(decryptedBytes, "UTF-8");

                    if ("Username already taken".equals(msgFromServer)) {
                        JOptionPane.showMessageDialog(frame, "Username already taken\n");
                        s.close();
                    } else {
                        new ClientView(id, s);
                        frame.dispose();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "Connection failed: " + ex.getMessage());
                }
            }
        });

        clientLoginBtn.setFont(new Font("Tahoma", Font.PLAIN, 17));
        clientLoginBtn.setBounds(207, 139, 132, 61);
        frame.getContentPane().add(clientLoginBtn);

        JLabel lblNewLabel = new JLabel("Username");
        lblNewLabel.setFont(new Font("Tahoma", Font.PLAIN, 17));
        lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        lblNewLabel.setBounds(44, 55, 132, 47);
        frame.getContentPane().add(lblNewLabel);
    }
}
