import java.io.*;
import java.net.*;
import java.util.*;

public class ClientSocket {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Choisissez une action :");
        System.out.println("1. Envoyer un fichier");
        System.out.println("2. Demander un fichier");
        int choix = scanner.nextInt();
        scanner.nextLine(); // Consommer le retour à la ligne

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream());
                DataInputStream dataInput = new DataInputStream(socket.getInputStream())) {

            if (choix == 1) {
                // Envoi d'un fichier
                System.out.println("Entrez le chemin du fichier à envoyer :");
                String filePath = scanner.nextLine();
                File file = new File(filePath);

                if (!file.exists()) {
                    System.err.println("Fichier introuvable : " + filePath);
                    return;
                }

                dataOutput.writeUTF("ENVOI");
                dataOutput.writeUTF(file.getName());
                dataOutput.writeLong(file.length());

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        dataOutput.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("Fichier envoyé au serveur principal.");
            } else if (choix == 2) {
                // Demande d'un fichier
                System.out.println("Demande de liste des fichiers disponibles...");

                // Demander la liste des fichiers disponibles
                dataOutput.writeUTF("RECEPTION");

                // Lire le nombre de fichiers disponibles
                int fileCount = dataInput.readInt();

                if (fileCount > 0) {
                    System.out.println("Liste des fichiers disponibles :");
                    List<String> availableFiles = new ArrayList<>();
                    for (int i = 0; i < fileCount; i++) {
                        String fileName = dataInput.readUTF();
                        availableFiles.add(fileName);
                        System.out.println((i + 1) + ". " + fileName);
                    }

                    System.out.println("Entrez le numéro du fichier que vous souhaitez télécharger :");
                    int fileChoice = scanner.nextInt();
                    scanner.nextLine(); // Consommer le retour à la ligne

                    // Assurez-vous que l'utilisateur a choisi une option valide
                    if (fileChoice > 0 && fileChoice <= availableFiles.size()) {
                        String fileName = availableFiles.get(fileChoice - 1); // Récupérer le nom du fichier choisi

                        // Envoi du nom du fichier choisi au serveur
                        dataOutput.writeUTF(fileName); // Utiliser le fileName choisi

                        long fileSize = dataInput.readLong();
                        if (fileSize > 0) {
                            File receivedFile = new File("download/recu_" + fileName);
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
                            System.out.println("Fichier reçu et enregistré sous : " + receivedFile.getAbsolutePath());
                        } else {
                            System.err.println("Fichier introuvable sur le serveur.");
                        }
                    } else {
                        System.err.println("Choix invalide.");
                    }
                } else {
                    System.err.println("Aucun fichier disponible sur le serveur.");
                }
            }

        } catch (IOException e) {
            System.err.println("Erreur du client : " + e.getMessage());
        }
    }
}
