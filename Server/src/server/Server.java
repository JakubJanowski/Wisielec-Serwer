package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    private static final short PORT = 6969;
    private static final byte MAX_CLIENTS = 4;
    private static final Socket[] clientSockets = new Socket[MAX_CLIENTS];
    private static byte nConnectedClients = 0;
    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;
    private static final Thread[] clientThreads = new Thread[MAX_CLIENTS];
    private static Thread listenerThread = null;

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

        listenerThread = new Thread(Server::listenClients);
        listenerThread.start();

        BufferedReader consoleBufferedReader = new BufferedReader(new InputStreamReader(System.in));
        do {
            try {
                if (consoleBufferedReader.ready()) {
                    switch (consoleBufferedReader.readLine().toLowerCase()) {
                        case "exit":
                            System.out.println("Terminating server.");
                            closeServer();
                            return;
                        default:
                            System.out.println("Unknown command.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (true);
    }

    private static void closeServer() {
        listenerThread.interrupt();
        try {
            new Socket("127.0.0.1", PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            if (clientThreads[i] != null)
                clientThreads[i].interrupt();
            if (clientSockets[i] != null) {
                try {
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSockets[i].getOutputStream()));
                    bufferedWriter.write("close\n");
                    bufferedWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //disconnectClient(i);
            }
        }
    }

    static void disconnectClient(byte clientIndex) {
        synchronized (LOCK) {
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
                if (Thread.interrupted()) {
                    return;
                }
                byte clientIndex;
                synchronized (LOCK) {
                    clientIndex = nConnectedClients;
                    clientSocket.setKeepAlive(true);
                    clientSockets[clientIndex] = clientSocket;
                    nConnectedClients++;
                }
                System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " joined.");
                clientThreads[clientIndex] = new clientThread(clientSocket, clientIndex);
                clientThreads[clientIndex].start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
