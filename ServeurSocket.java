
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class ServeurSocket {

    private static int PORT;
    private static String[] SUB_SERVER_HOSTS;
    private static int[] SUB_SERVER_PORTS;

    private static final String FILE_LIST_PATH = "file_list.txt"; // Fichier contenant la liste des fichiers uploadés

    public static void main(String[] args) {
        try {
            // Charger la configuration
            ConfigLoader config = new ConfigLoader("server.conf");
            PORT = config.getInt("main_server_port");

            SUB_SERVER_HOSTS = new String[]{
                config.get("sub_server1_host"),
                config.get("sub_server2_host"),
                config.get("sub_server3_host")
            };

            SUB_SERVER_PORTS = new int[]{
                config.getInt("sub_server1_port"),
                config.getInt("sub_server2_port"),
                config.getInt("sub_server3_port")
            };

            System.out.println("Serveur principal en écoute sur le port " + PORT);

        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }

        int startedServers = countLoadSlavs();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur principal en écoute sur le port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion acceptée : " + clientSocket.getInetAddress());

                try (DataInputStream dataInput = new DataInputStream(clientSocket.getInputStream()); DataOutputStream dataOutput = new DataOutputStream(clientSocket.getOutputStream())) {

                    String action = dataInput.readUTF(); // "ENVOI" ou "RECEPTION"

                    if ("ENVOI".equalsIgnoreCase(action)) {
                        // Réception et division du fichier
                        String fileName = dataInput.readUTF();
                        long fileSize = dataInput.readLong();
                        System.out.println("Réception du fichier : " + fileName);

                        File tempFile = File.createTempFile("server_file_", ".tmp");
                        try (FileOutputStream fileOutput = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long totalRead = 0;

                            while ((bytesRead = dataInput.read(buffer)) != -1) {
                                fileOutput.write(buffer, 0, bytesRead);
                                totalRead += bytesRead;
                                if (totalRead >= fileSize) {
                                    break;
                                }
                            }
                        }

                        // Diviser le fichier en n parties
                        File[] parts = divideFile(tempFile, startedServers);

                        // Envoyer chaque partie aux sous-serveurs
                        for (int i = 0; i < startedServers; i++) {
                            sendToSubServer(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i], parts[i],
                                    fileName + "_part" + (i + 1));
                        }

                        // Ajouter le fichier à la liste
                        addToFileList(fileName);
                        System.out.println("Fichier divisé et envoyé aux sous-serveurs.");
                    } else if ("RECEPTION".equalsIgnoreCase(action)) {
                        // Demander la liste des fichiers
                        sendFileList(dataOutput);

                        // Réception du fichier demandé
                        String fileName = dataInput.readUTF();
                        File assembledFile = assembleFile(fileName);

                        dataOutput.writeLong(assembledFile.length());
                        try (FileInputStream fileInput = new FileInputStream(assembledFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;

                            while ((bytesRead = fileInput.read(buffer)) != -1) {
                                dataOutput.write(buffer, 0, bytesRead);
                            }
                        }
                        System.out.println("Fichier " + fileName + " assemblé et envoyé au client.");
                    } else if ("SUPPRESSION".equalsIgnoreCase(action)) {
                        sendFileList(dataOutput);

                        System.out.println("haha");
                        String fileName = dataInput.readUTF();
                        boolean fileDeleted = deleteFileFromServer(fileName);
                        if (fileDeleted) {
                            dataOutput.writeUTF("Fichier supprimé avec succès : " + fileName);
                            System.out.println("Fichier " + fileName + " supprime.");
                        } else {
                            dataOutput.writeUTF("Erreur : Fichier introuvable ou suppression échouée.");
                        }

                    }
                } catch (IOException e) {
                    System.err.println("Erreur avec le client : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur principal : " + e.getMessage());
        }
    }

    // Ajouter un fichier à la liste des fichiers disponibles
    private static void addToFileList(String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_LIST_PATH, true))) {
            writer.write(fileName);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'ajout du fichier à la liste : " + e.getMessage());
        }
    }

    // Envoyer la liste des fichiers au client
    private static void sendFileList(DataOutputStream dataOutput) throws IOException {
        File fileList = new File(FILE_LIST_PATH);
        if (fileList.exists()) {
            List<String> files = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(fileList))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    files.add(line);
                }
            }
            dataOutput.writeInt(files.size());
            for (String file : files) {
                dataOutput.writeUTF(file);
            }
        } else {
            dataOutput.writeInt(0);
        }
    }

    private static File[] divideFile(File file, int partsCount) throws IOException {
        long partSize = file.length() / partsCount;
        File[] parts = new File[partsCount];
        try (FileInputStream fileInput = new FileInputStream(file)) {
            for (int i = 0; i < partsCount; i++) {
                parts[i] = File.createTempFile("part_" + (i + 1) + "_", ".tmp");
                try (FileOutputStream partOutput = new FileOutputStream(parts[i])) {
                    byte[] buffer = new byte[4096];
                    long written = 0;

                    while (written < partSize && fileInput.available() > 0) {
                        int bytesRead = fileInput.read(buffer);
                        partOutput.write(buffer, 0, bytesRead);
                        written += bytesRead;
                    }
                }
            }
        }
        return parts;
    }

    private static void sendToSubServer(String host, int port, File partFile, String partName) {
        try (Socket socket = new Socket(host, port); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); FileInputStream fileInput = new FileInputStream(partFile)) {

            dataOutput.writeUTF("STORE"); // Envoyer la commande "STORE"
            dataOutput.writeUTF(partName); // Envoyer le nom de la partie
            dataOutput.writeLong(partFile.length()); // Envoyer la taille de la partie

            // Envoyer les données de la partie
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInput.read(buffer)) != -1) {
                dataOutput.write(buffer, 0, bytesRead);
            }

            System.out.println("Partie " + partName + " envoyée au sous-serveur " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("Erreur d'envoi à " + host + ":" + port + " : " + e.getMessage());
        }
    }

    private static File assembleFile(String fileName) throws IOException {
        File assembledFile = File.createTempFile("assembled_", ".tmp");
        int startedServers = countLoadSlavs();

        try (FileOutputStream fileOutput = new FileOutputStream(assembledFile)) {
            for (int i = 0; i < startedServers; i++) {
                File partFile = retrieveFromSubServer(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i],
                        fileName + "_part" + (i + 1));

                try (FileInputStream fileInput = new FileInputStream(partFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        fileOutput.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
        return assembledFile;
    }

    private static File retrieveFromSubServer(String host, int port, String partName) throws IOException {
        File partFile = File.createTempFile("retrieved_", ".tmp");
        try (Socket socket = new Socket(host, port); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); DataInputStream dataInput = new DataInputStream(socket.getInputStream()); FileOutputStream fileOutput = new FileOutputStream(partFile)) {
            dataOutput.writeUTF("GET");
            dataOutput.writeUTF(partName);
            long fileSize = dataInput.readLong();
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            while (totalRead < fileSize) {
                int bytesRead = dataInput.read(buffer);
                fileOutput.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        }
        return partFile;
    }

    public static int countLoadSlavs() {
        int nb = 0;
        for (int i = 0; i < SUB_SERVER_HOSTS.length; i++) {
            try (Socket socket = new Socket(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i])) {
                nb++;
            } catch (IOException e) {
                System.err.println("Sous-serveur " + SUB_SERVER_HOSTS[i] + ":" + SUB_SERVER_PORTS[i] + " non accessible.");
            }
        }
        return nb;
    }

    private static boolean deleteFileFromServer(String fileName) {
        boolean allDeleted = true;
        int startedServers = countLoadSlavs();

        for (int i = 0; i < startedServers; i++) {
            try (Socket socket = new Socket(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i]); DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream()); DataInputStream dataInput = new DataInputStream(socket.getInputStream())) {

                dataOutput.writeUTF("SUPPRESSION"); // Commande DELETE
                dataOutput.writeUTF(fileName + "_part" + (i + 1)); // Partie à supprimer
                dataOutput.flush();

                String response = dataInput.readUTF(); // Réponse du sous-serveur
                if (!"OK".equalsIgnoreCase(response)) {
                    System.err.println("Erreur lors de la suppression de " + fileName + "_part" + (i + 1));
                    allDeleted = false;
                } else {
                    System.out.println("Partie supprimée sur le sous-serveur : " + SUB_SERVER_HOSTS[i] + ":" + SUB_SERVER_PORTS[i]);
                }
            } catch (IOException e) {
                System.err.println("Impossible de se connecter au sous-serveur " + SUB_SERVER_HOSTS[i] + ":" + SUB_SERVER_PORTS[i] + " : " + e.getMessage());
                allDeleted = false;
            }
        }

        if (allDeleted) {
            // Mise à jour de la liste des fichiers
            removeFromFileList(fileName);
        }

        return allDeleted;
    }

    private static void removeFromFileList(String fileName) {
        File fileList = new File(FILE_LIST_PATH);
        List<String> updatedFileList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileList))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ajoute toutes les lignes sauf celle correspondant au fichier à supprimer
                if (!line.trim().equals(fileName)) {
                    updatedFileList.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture de la liste des fichiers : " + e.getMessage());
            return;
        }

        // Réécrit la liste mise à jour dans le fichier
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileList))) {
            for (String file : updatedFileList) {
                writer.write(file);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la mise à jour de la liste des fichiers : " + e.getMessage());
        }
    }

}
