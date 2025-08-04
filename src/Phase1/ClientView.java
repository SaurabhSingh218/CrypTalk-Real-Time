package Phase1;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.StringTokenizer;

public class ClientView extends JFrame {

    private static final long serialVersionUID = 1L;

    private JTextField clientTypingBoard;
    private JList<String> clientActiveUsersList;
    private JTextArea clientMessageBoard;
    private JButton clientKillProcessBtn;
    private JRadioButton oneToNRadioBtn;
    private JRadioButton broadcastBtn;

    DataInputStream inputStream;
    DataOutputStream outStream;
    DefaultListModel<String> dm;
    String id, clientIds = "";

    private final RC4 rc4 = new RC4();
    private final byte[] keyBytes = "secretkey".getBytes();

    public ClientView(String id, Socket s) {
        this.id = id;
        initialize();
        try {
            setTitle("Client View - " + id);
            dm = new DefaultListModel<>();
            clientActiveUsersList.setModel(dm);
            inputStream = new DataInputStream(s.getInputStream());
            outStream = new DataOutputStream(s.getOutputStream());

            new Read().start();
            setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    class Read extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    String encryptedMsg = inputStream.readUTF();

                    // Decrypt message
                    byte[] encryptedBytes = helper.hexToBytes(encryptedMsg);
                    byte[] decryptedBytes = rc4.decrypt(keyBytes, encryptedBytes);
                    String decryptedMsg = new String(decryptedBytes, "UTF-8");

                    if (decryptedMsg.contains(":;.,/=")) {
                        decryptedMsg = decryptedMsg.substring(6);
                        dm.clear();
                        StringTokenizer st = new StringTokenizer(decryptedMsg, ",");
                        while (st.hasMoreTokens()) {
                            String u = st.nextToken();
                            if (!id.equals(u))
                                dm.addElement(u);
                        }
                    } else {
                        clientMessageBoard.append(decryptedMsg + "\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void initialize() {
        setBounds(100, 100, 926, 705);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);
        setTitle("Client View");

        clientMessageBoard = new JTextArea();
        clientMessageBoard.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(clientMessageBoard);
        scrollPane.setBounds(12, 25, 530, 495);
        getContentPane().add(scrollPane);

        clientTypingBoard = new JTextField();
        clientTypingBoard.setHorizontalAlignment(SwingConstants.LEFT);
        clientTypingBoard.setBounds(12, 533, 530, 84);
        getContentPane().add(clientTypingBoard);
        clientTypingBoard.setColumns(10);

        JButton clientSendMsgBtn = new JButton("Send");
        clientSendMsgBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String textAreaMessage = clientTypingBoard.getText();
                if (textAreaMessage != null && !textAreaMessage.isEmpty()) {
                    try {
                        String messageToBeSentToServer = "";
                        String cast = "broadcast";
                        int flag = 0;
                        if (oneToNRadioBtn.isSelected()) {
                            cast = "multicast";
                            List<String> clientList = clientActiveUsersList.getSelectedValuesList();
                            if (clientList.size() == 0)
                                flag = 1;
                            for (String selectedUsr : clientList) {
                                if (clientIds.isEmpty())
                                    clientIds += selectedUsr;
                                else
                                    clientIds += "," + selectedUsr;
                            }
                            messageToBeSentToServer = cast + ":" + clientIds + ":" + textAreaMessage;
                        } else {
                            messageToBeSentToServer = cast + ":" + textAreaMessage;
                        }

                        byte[] encryptedBytes = rc4.encrypt(keyBytes, messageToBeSentToServer.getBytes("UTF-8"));
                        String encryptedHex = helper.bytesToHex(encryptedBytes);

                        if (cast.equalsIgnoreCase("multicast")) {
                            if (flag == 1) {
                                JOptionPane.showMessageDialog(null, "No user selected");
                            } else {
                                outStream.writeUTF(encryptedHex);
                                clientTypingBoard.setText("");
                                clientMessageBoard.append("< You sent msg to " + clientIds + " > " + textAreaMessage + "\n");
                            }
                        } else {
                            outStream.writeUTF(encryptedHex);
                            clientTypingBoard.setText("");
                            clientMessageBoard.append("< You sent msg to All > " + textAreaMessage + "\n");
                        }
                        clientIds = "";
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "User does not exist anymore.");
                    }
                }
            }
        });
        clientSendMsgBtn.setBounds(554, 533, 137, 84);
        getContentPane().add(clientSendMsgBtn);

        clientActiveUsersList = new JList<>();
        clientActiveUsersList.setToolTipText("Active Users");
        JScrollPane listScrollPane = new JScrollPane(clientActiveUsersList);
        listScrollPane.setBounds(554, 63, 327, 457);
        getContentPane().add(listScrollPane);

        clientKillProcessBtn = new JButton("Kill Process");
        clientKillProcessBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    byte[] encryptedExit = rc4.encrypt(keyBytes, "exit".getBytes("UTF-8"));
                    String encryptedExitHex = helper.bytesToHex(encryptedExit);
                    outStream.writeUTF(encryptedExitHex);
                    clientMessageBoard.append("You are disconnected now.\n");
                    dispose();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });
        clientKillProcessBtn.setBounds(703, 533, 193, 84);
        getContentPane().add(clientKillProcessBtn);

        JLabel lblNewLabel = new JLabel("Active Users");
        lblNewLabel.setHorizontalAlignment(SwingConstants.LEFT);
        lblNewLabel.setBounds(559, 43, 95, 16);
        getContentPane().add(lblNewLabel);

        oneToNRadioBtn = new JRadioButton("1 to N");
        oneToNRadioBtn.addActionListener(e -> clientActiveUsersList.setEnabled(true));
        oneToNRadioBtn.setSelected(true);
        oneToNRadioBtn.setBounds(682, 17, 72, 25);
        getContentPane().add(oneToNRadioBtn);

        broadcastBtn = new JRadioButton("Broadcast");
        broadcastBtn.addActionListener(e -> clientActiveUsersList.setEnabled(false));
        broadcastBtn.setBounds(762, 17, 95, 25);
        getContentPane().add(broadcastBtn);

        ButtonGroup group = new ButtonGroup();
        group.add(oneToNRadioBtn);
        group.add(broadcastBtn);
    }
}
