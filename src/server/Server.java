package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final short PORT = 6969;
    private static final byte MAX_CLIENTS = 4;
    private static final Socket[] clientSockets = new Socket[MAX_CLIENTS];
    private static byte nConnectedClients = 0;
    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        try {
            System.out.println("Starting server...");
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server is running at 127.0.0.1:" + PORT + "\nWaiting for clients to join...");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Terminating server.");
            return;
        }

        new Thread(Server::listenClients).start();

    }

    static void disconnectClient(byte clientIndex) {
        synchronized(LOCK) {
            if (clientSockets[clientIndex] != null) {
                try {
                    clientSockets[clientIndex].close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (nConnectedClients > 0)
                nConnectedClients--;
        }
    }

    private static void listenClients() {
        while (nConnectedClients < 4) {
            try {
                Socket clientSocket = serverSocket.accept();
                byte clientIndex;
                synchronized(LOCK) {
                    clientIndex = nConnectedClients;
                    clientSockets[clientIndex] = clientSocket;
                    nConnectedClients++;
                }
                System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " joined.");
                new clientThread(clientSocket, clientIndex).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
