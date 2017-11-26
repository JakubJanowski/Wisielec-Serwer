package server;

import shared.GameState;
import shared.Message;
import shared.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Dictionary;

public class Server {
    private static final short PORT = 6969;
    private static final byte MAX_CLIENTS = 4;
    private static final Socket[] clientSockets = new Socket[MAX_CLIENTS];
    private static byte nConnectedClients = 0;
    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;
    private static final ClientThread[] clientThreads = new ClientThread[MAX_CLIENTS];
    private static Thread listenerThread = null;

    //
    public static GameState.Player players[];
    public static Dictionary<Character, Boolean> keyboard;
    public static String word;
    public static GameState.Phase phase;
    public static int hangmanHealth;



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
                        case "list":
                            listClients();
                            break;
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
                clientThreads[clientIndex] = new ClientThread(clientSocket, clientIndex);
                clientThreads[clientIndex].start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void listClients() {
        boolean isAnyoneConnected = false;
        for(int i = 0; i < MAX_CLIENTS; i++) {
            if(clientSockets[i] != null) {
                isAnyoneConnected = true;
                String status = "";
                if (clientSockets[i].isConnected() && clientSockets[i].isClosed())
                    status = "connected, closed";
                else if (clientSockets[i].isConnected())
                    status = "connected";
                else if (clientSockets[i].isClosed())
                    status = "closed";
                System.out.println(clientSockets[i].getInetAddress() + ":" + clientSockets[i].getPort() + " status: " + status);
            }
        }
        if(!isAnyoneConnected)
            System.out.println("No clients connected.");
    }

    private static void closeServer() {
        listenerThread.interrupt();
        try {
            new Socket("127.0.0.1", PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            if (clientThreads[i] != null) {
                clientThreads[i].sendMessage(new Message(MessageType.Disconnect));
                clientThreads[i].interrupt();
            }
        }
    }

    static void disconnectClient(byte clientIndex) {
        synchronized (LOCK) {
            if (clientThreads[clientIndex] != null) {
                if(!clientThreads[clientIndex].isInterrupted()) {
                    clientThreads[clientIndex].interrupt();
                }
                clientThreads[clientIndex] = null;
            }
            if (clientSockets[clientIndex] != null) {
                try {
                    System.out.print("Closing connection with client " + clientSockets[clientIndex].getInetAddress() + ":" + clientSockets[clientIndex].getPort() + "...");
                    clientSockets[clientIndex].close();
                    System.out.println(" OK");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientSockets[clientIndex] = null;
            }
            if (nConnectedClients > 0)
                nConnectedClients--;
        }
    }

}
