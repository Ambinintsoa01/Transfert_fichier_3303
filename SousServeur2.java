
import java.io.*;
import java.net.*;

public class SousServeur2 {

    private static int PORT;
    private static String STORAGE_DIR;

    public static void main(String[] args) {
        try {
            // Charger la configuration
            ConfigLoader config = new ConfigLoader("server.conf");
            PORT = config.getInt("sub_server2_port"); // Remplacez par le sous-serveur correspondant
            STORAGE_DIR = config.get("sub_server_storage_dir0");

            // Créer le répertoire de stockage si nécessaire
            File dir = new File(STORAGE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            System.out.println("Sous-serveur en écoute sur le port " + PORT);

            // Reste du code du sous-serveur...
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
        // Créer le répertoire de stockage si nécessaire
        File dir = new File(STORAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Sous-serveur en écoute sur le port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Connexion acceptée : " + socket.getInetAddress());

                try (DataInputStream dataInput = new DataInputStream(socket.getInputStream()); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream())) {

                    String action = dataInput.readUTF(); // "STORE" ou "GET"

                    if ("STORE".equalsIgnoreCase(action)) {
                        // Réception d'une partie de fichier
                        String partName = dataInput.readUTF();
                        long partSize = dataInput.readLong();
                        File partFile = new File(STORAGE_DIR + partName);

                        try (FileOutputStream fileOutput = new FileOutputStream(partFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long totalRead = 0;

                            while ((bytesRead = dataInput.read(buffer)) != -1) {
                                fileOutput.write(buffer, 0, bytesRead);
                                totalRead += bytesRead;
                                if (totalRead >= partSize) {
                                    break;
                                }
                            }
                        }
                        System.out.println("Partie reçue et stockée : " + partName);
                    } else if ("GET".equalsIgnoreCase(action)) {
                        // Envoi d'une partie de fichier
                        String partName = dataInput.readUTF();
                        File partFile = new File(STORAGE_DIR + partName);

                        if (partFile.exists()) {
                            dataOutput.writeLong(partFile.length());
                            try (FileInputStream fileInput = new FileInputStream(partFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;

                                while ((bytesRead = fileInput.read(buffer)) != -1) {
                                    dataOutput.write(buffer, 0, bytesRead);
                                }
                            }
                            System.out.println("Partie envoyée : " + partName);
                        } else {
                            dataOutput.writeLong(0);
                            System.err.println("Partie demandée introuvable : " + partName);
                        }
                    } else if ("SUPPRESSION".equalsIgnoreCase(action)) {
                        String partName = dataInput.readUTF();
                        File partFile = new File(STORAGE_DIR + partName);

                        if (partFile.exists() && partFile.delete()) {
                            dataOutput.writeUTF("OK");
                            System.out.println("Partie supprimée : " + partName);
                        } else {
                            dataOutput.writeUTF("ERROR");
                            System.err.println("Échec de la suppression de : " + partName);
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Erreur avec un client : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du sous-serveur : " + e.getMessage());
        }
    }
}
