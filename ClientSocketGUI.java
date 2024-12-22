
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class ClientSocketGUI {

    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 2000;
    private static JFrame frame;
    private static JTextArea textArea;
    private static JButton sendFileButton, requestFileButton;
    private static Socket socket;
    private static DataOutputStream dataOutput;
    private static DataInputStream dataInput;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientSocketGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Client Socket");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        // Zone d'affichage des messages
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Panneau de boutons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        sendFileButton = new JButton("Envoyer un fichier");
        requestFileButton = new JButton("Demander un fichier");

        sendFileButton.addActionListener(e -> sendFile());
        requestFileButton.addActionListener(e -> requestFile());

        buttonPanel.add(sendFileButton);
        buttonPanel.add(requestFileButton);

        // Ajout d'un bouton dans le panneau
        JButton deleteFileButton;

        // Modifications dans createAndShowGUI()
        deleteFileButton = new JButton("Supprimer un fichier");
        deleteFileButton.addActionListener(e -> deleteFile());

        buttonPanel.add(deleteFileButton);

        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private static void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Choisir un fichier à envoyer");

        int returnValue = fileChooser.showOpenDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            textArea.append("Envoi du fichier : " + file.getName() + "\n");

            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); FileInputStream fileInput = new FileInputStream(file)) {

                dataOutput.writeUTF("ENVOI");
                dataOutput.writeUTF(file.getName());
                dataOutput.writeLong(file.length());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    dataOutput.write(buffer, 0, bytesRead);
                }
                textArea.append("Fichier envoyé au serveur.\n");

            } catch (IOException e) {
                textArea.append("Erreur lors de l'envoi du fichier : " + e.getMessage() + "\n");
            }
        }
    }

    private static void deleteFile() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); DataInputStream dataInput = new DataInputStream(socket.getInputStream())) {

            // Demander la liste des fichiers disponibles
            dataOutput.writeUTF("SUPPRESSION");

            int fileCount = dataInput.readInt();
            if (fileCount > 0) {
                String[] fileNames = new String[fileCount];
                for (int i = 0; i < fileCount; i++) {
                    fileNames[i] = dataInput.readUTF();
                }

                String fileChoice = (String) JOptionPane.showInputDialog(
                        frame,
                        "Sélectionnez un fichier à supprimer",
                        "Supprimer un fichier",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        fileNames,
                        fileNames[0] // valeur par défaut
                );

                if (fileChoice != null) {
                    dataOutput.writeUTF(fileChoice);
                    String serverResponse = dataInput.readUTF();
                    textArea.append(serverResponse + "\n");
                } else {
                    textArea.append("Aucun fichier sélectionné pour suppression.\n");
                }
            } else {
                textArea.append("Aucun fichier disponible sur le serveur pour suppression.\n");
            }
        } catch (IOException e) {
            textArea.append("Erreur lors de la suppression du fichier : " + e.getMessage() + "\n");
        }
    }

    private static void requestFile() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); DataInputStream dataInput = new DataInputStream(socket.getInputStream())) {

            // Demander la liste des fichiers disponibles
            dataOutput.writeUTF("RECEPTION");

            int fileCount = dataInput.readInt();
            if (fileCount > 0) {
                ArrayList<String> availableFiles = new ArrayList<>();
                String[] fileNames = new String[fileCount];

                // Lire et stocker les noms des fichiers
                for (int i = 0; i < fileCount; i++) {
                    fileNames[i] = dataInput.readUTF();
                    availableFiles.add(fileNames[i]);
                }

                // Afficher la liste des fichiers disponibles dans une nouvelle fenêtre
                String fileChoice = (String) JOptionPane.showInputDialog(
                        frame,
                        "Sélectionnez un fichier à télécharger",
                        "Choisir un fichier",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        fileNames,
                        fileNames[0] // valeur par défaut
                );

                if (fileChoice != null) {
                    textArea.append("Fichier demandé : " + fileChoice + "\n");

                    // Envoyer le nom du fichier choisi au serveur
                    dataOutput.writeUTF(fileChoice);

                    long fileSize = dataInput.readLong();
                    if (fileSize > 0) {
                        File receivedFile = new File("download/recu_" + fileChoice);
                        try (FileOutputStream fileOutput = new FileOutputStream(receivedFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long totalRead = 0;

                            while (totalRead < fileSize) {
                                bytesRead = dataInput.read(buffer);
                                fileOutput.write(buffer, 0, bytesRead);
                                totalRead += bytesRead;
                            }
                        }
                        textArea.append("Fichier reçu et enregistré sous : " + receivedFile.getAbsolutePath() + "\n");
                    } else {
                        textArea.append("Fichier introuvable sur le serveur.\n");
                    }
                } else {
                    textArea.append("Aucun fichier choisi.\n");
                }
            } else {
                textArea.append("Aucun fichier disponible sur le serveur.\n");
            }
        } catch (IOException e) {
            textArea.append("Erreur lors de la demande du fichier : " + e.getMessage() + "\n");
        }
    }
}
