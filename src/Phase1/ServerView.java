package Phase1;

import java.awt.EventQueue;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;

public class ServerView {

    private static final int port = 8818;
    private static final String SECRET_KEY = "secretkey";
    private final byte[] keyBytes = SECRET_KEY.getBytes();

    private JFrame frame;
    private ServerSocket serverSocket;

    private JTextArea serverMessageBoard;
    private JList<String> allUserNameList;
    private JList<String> activeClientList;

    private final DefaultListModel<String> activeDlm = new DefaultListModel<>();
    private final DefaultListModel<String> allDlm = new DefaultListModel<>();

    // Thread-safe structures
    private static final ConcurrentHashMap<String, Socket> allUsersList = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DataOutputStream> userOutputStreams = new ConcurrentHashMap<>();
    private static final Set<String> activeUserSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final RC4 rc4 = new RC4();

    // Utility functions moved to helper - no need here.

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                ServerView window = new ServerView();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public ServerView() {
        initialize();
        try {
            serverSocket = new ServerSocket(port);
            serverMessageBoard.append("Server started on port: " + port + "\n");
            serverMessageBoard.append("Waiting for the clients...\n");
            new ClientAccept().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ClientAccept extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream cOutStream = new DataOutputStream(clientSocket.getOutputStream());

                    String encUserNameHex = dis.readUTF();
                    byte[] encUserNameBytes = helper.hexToBytes(encUserNameHex);
                    String uName = new String(rc4.decrypt(keyBytes, encUserNameBytes), "UTF-8");

                    if (activeUserSet.contains(uName)) {
                        String errMsg = helper.bytesToHex(rc4.encrypt(keyBytes, "Username already taken".getBytes("UTF-8")));
                        cOutStream.writeUTF(errMsg);
                    } else {
                        allUsersList.put(uName, clientSocket);
                        userOutputStreams.put(uName, cOutStream);
                        activeUserSet.add(uName);

                        String emptyMsg = helper.bytesToHex(rc4.encrypt(keyBytes, "".getBytes("UTF-8")));
                        cOutStream.writeUTF(emptyMsg);

                        activeDlm.addElement(uName);
                        if (!allDlm.contains(uName))
                            allDlm.addElement(uName);

                        activeClientList.setModel(activeDlm);
                        allUserNameList.setModel(allDlm);
                        serverMessageBoard.append("Client " + uName + " Connected...\n");

                        new MsgRead(clientSocket, uName, dis).start();
                        new PrepareClientList().start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class MsgRead extends Thread {
        Socket s;
        String Id;
        DataInputStream dis;

        MsgRead(Socket s, String uname, DataInputStream dis) {
            this.s = s;
            this.Id = uname;
            this.dis = dis;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String encMessageHex = dis.readUTF();
                    byte[] encMessageBytes = helper.hexToBytes(encMessageHex);
                    String message = new String(rc4.decrypt(keyBytes, encMessageBytes), "UTF-8");
                    System.out.println("Decrypted message: " + message);

                    String[] msgList = message.split(":", 3);

                    if (msgList[0].equalsIgnoreCase("multicast")) {
                        String[] sendToList = msgList[1].split(",");
                        for (String usr : sendToList) {
                            if (activeUserSet.contains(usr)) {
                                String outMsg = "< " + Id + " >" + msgList[2];
                                byte[] encryptedOut = rc4.encrypt(keyBytes, outMsg.getBytes("UTF-8"));
                                String hexOut = helper.bytesToHex(encryptedOut);
                                DataOutputStream dos = userOutputStreams.get(usr);
                                if (dos != null) {
                                    dos.writeUTF(hexOut);
                                    dos.flush();
                                }
                            }
                        }
                    } else if (msgList[0].equalsIgnoreCase("broadcast")) {
                        for (String usrName : allUsersList.keySet()) {
                            if (!usrName.equalsIgnoreCase(Id)) {
                                try {
                                    if (activeUserSet.contains(usrName)) {
                                        String outMsg = "< " + Id + " >" + msgList[1];
                                        byte[] encryptedOut = rc4.encrypt(keyBytes, outMsg.getBytes("UTF-8"));
                                        String hexOut = helper.bytesToHex(encryptedOut);
                                        DataOutputStream dos = userOutputStreams.get(usrName);
                                        if (dos != null) {
                                            dos.writeUTF(hexOut);
                                            dos.flush();
                                        }
                                    } else {
                                        String notice = "Message couldn't be delivered to user " + usrName + " because it is disconnected.\n";
                                        byte[] encryptedNotice = rc4.encrypt(keyBytes, notice.getBytes("UTF-8"));
                                        String hexNotice = helper.bytesToHex(encryptedNotice);
                                        DataOutputStream senderDos = userOutputStreams.get(Id);
                                        if (senderDos != null) {
                                            senderDos.writeUTF(hexNotice);
                                            senderDos.flush();
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else if (msgList[0].equalsIgnoreCase("exit")) {
                        activeUserSet.remove(Id);
                        serverMessageBoard.append(Id + " disconnected....\n");

                        new PrepareClientList().start();

                        for (String usrName2 : activeUserSet) {
                            if (!usrName2.equalsIgnoreCase(Id)) {
                                try {
                                    String outMsg = Id + " disconnected...";
                                    byte[] encryptedOut = rc4.encrypt(keyBytes, outMsg.getBytes("UTF-8"));
                                    String hexOut = helper.bytesToHex(encryptedOut);
                                    DataOutputStream dos = userOutputStreams.get(usrName2);
                                    if (dos != null) {
                                        dos.writeUTF(hexOut);
                                        dos.flush();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        activeDlm.removeElement(Id);
                        activeClientList.setModel(activeDlm);

                        userOutputStreams.remove(Id);
                        allUsersList.remove(Id);
                        s.close();

                        break; // exit loop and terminate thread
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    class PrepareClientList extends Thread {
        @Override
        public void run() {
            try {
                String ids = String.join(",", activeUserSet);
                byte[] encryptedIds = rc4.encrypt(keyBytes, (":;.,/=" + ids).getBytes("UTF-8"));
                String hexIds = helper.bytesToHex(encryptedIds);

                for (String key : activeUserSet) {
                    try {
                        DataOutputStream dos = userOutputStreams.get(key);
                        if (dos != null) {
                            dos.writeUTF(hexIds);
                            dos.flush();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initialize() {
        frame = new JFrame();
        frame.setBounds(100, 100, 796, 530);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(null);
        frame.setTitle("Server View");

        serverMessageBoard = new JTextArea();
        serverMessageBoard.setEditable(false);
        serverMessageBoard.setBounds(12, 29, 489, 435);
        frame.getContentPane().add(serverMessageBoard);
        serverMessageBoard.setText("Starting the Server...\n");

        allUserNameList = new JList<>();
        allUserNameList.setBounds(526, 324, 218, 140);
        frame.getContentPane().add(allUserNameList);

        activeClientList = new JList<>();
        activeClientList.setBounds(526, 78, 218, 156);
        frame.getContentPane().add(activeClientList);

        JLabel lblAllUsers = new JLabel("All Usernames");
        lblAllUsers.setHorizontalAlignment(SwingConstants.LEFT);
        lblAllUsers.setBounds(530, 295, 127, 16);
        frame.getContentPane().add(lblAllUsers);

        JLabel lblActiveUsers = new JLabel("Active Users");
        lblActiveUsers.setBounds(526, 53, 98, 23);
        frame.getContentPane().add(lblActiveUsers);
    }
}
