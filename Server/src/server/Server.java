package server;

import shared.GameState;
import shared.Message;
import shared.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final short PORT = 6969;
    private static final byte MAX_CLIENTS = 4;
    private static final Socket[] clientSockets = new Socket[MAX_CLIENTS];
    private static byte nConnectedClients = 0;
    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;
    private static final ClientThread[] clientThreads = new ClientThread[MAX_CLIENTS];
    private static Thread listenerThread = null;
    private static boolean exit = false;
    private static String[] logins = new String[MAX_CLIENTS];
    private static boolean[] hasLoginSet = new boolean[MAX_CLIENTS];

    //
    static GameState gameState;
    static String word;
    static int dealer;
    static int counter = 0;
    static final int NUMBER_OF_TURN = 8;


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
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (Thread.interrupted())
                    return;
                if (nConnectedClients >= 4) {
                    clientSocket.close();
                } else {
                    byte clientIndex;
                    synchronized (LOCK) {
                        clientIndex = getNewClientIndex();
                        hasLoginSet[clientIndex] = false;
                        clientSockets[clientIndex] = clientSocket;
                        clientSocket.setKeepAlive(true);
                        nConnectedClients++;
                    }
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " joined.");
                    clientThreads[clientIndex] = new ClientThread(clientSocket, clientIndex);
                    clientThreads[clientIndex].start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //gameState.hangmanHealth = 7;
        //gameState.phase = GameState.Phase.ChoosingWord;
        //dealer = 0;
    }

    private static void listClients() {
        boolean isAnyoneConnected = false;
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (logins[i] != null) {
                System.out.print(logins[i] + " ");
                if (clientSockets[i] == null) {
                    System.out.println("(disconnected)");
                }
            }
            if (clientSockets[i] != null) {
                isAnyoneConnected = true;
                String status = "";
                if (!hasLoginSet[i] && logins[i] != null)
                    System.out.println();
                if (clientSockets[i].isConnected() && clientSockets[i].isClosed())
                    status = "connected, closed";
                else if (clientSockets[i].isConnected())
                    status = "connected";
                else if (clientSockets[i].isClosed())
                    status = "closed";
                System.out.println(clientSockets[i].getInetAddress() + ":" + clientSockets[i].getPort() + " status: " + status);
            }
        }
        if (!isAnyoneConnected)
            System.out.println("No clients connected.");
    }

    private static byte getNewClientIndex() {
        for (byte i = 0; i < MAX_CLIENTS; i++)
            if (clientSockets[i] == null)
                return i;
        return -1;
    }

    private static byte getNewLoginIndex() {
        for (byte i = 0; i < MAX_CLIENTS; i++)
            if (logins[i] == null)
                return i;
        return -1;
    }

    private static byte getLastLoginIndex(String login) {
        for (byte i = 0; i < MAX_CLIENTS; i++)
            if (logins[i] != null && logins[i].equals(login))
                return i;
        return -1;
    }

    static boolean setLogin(byte clientIndex, String login) {
        synchronized (LOCK) {
            byte loginIndex = getNewLoginIndex();
            byte lastLoginIndex = getLastLoginIndex(login);

            if (loginIndex == -1) {   // server is full
                if (lastLoginIndex == -1) {   // client wasn't logged in before
                    disconnectClient(clientIndex);
                } else {
                    if (clientSockets[lastLoginIndex] != null && lastLoginIndex != clientIndex) {    // login is taken
                        return false;   // inform calling thread to wait for another login
                    } else {    // client reconnected
                        System.out.println(login + " reconnected");
                        if (clientIndex != lastLoginIndex) {
                            logins[lastLoginIndex] = logins[clientIndex];
                            logins[clientIndex] = login;
                        }
                        hasLoginSet[clientIndex] = true;
                    }
                }
            } else {
                if (lastLoginIndex == -1) {   // client wasn't logged in before
                    if (loginIndex == clientIndex) {
                        logins[clientIndex] = login;
                    } else {    // someone else has disconnected and we're leaving entry in logins for him to reconnect
                        logins[loginIndex] = logins[clientIndex];
                        logins[clientIndex] = login;
                    }
                    hasLoginSet[clientIndex] = true;
                } else {
                    if (clientSockets[lastLoginIndex] != null && lastLoginIndex != clientIndex) {    // login is taken
                        return false;   // inform calling thread to wait for another login
                    } else {    // client reconnected
                        System.out.println(login + " reconnected");
                        if (clientIndex != lastLoginIndex) {
                            logins[lastLoginIndex] = logins[clientIndex];
                            logins[clientIndex] = login;
                        }
                        hasLoginSet[clientIndex] = true;
                    }
                }
            }
        }
        return true;
    }

    private static void pingClients() {
        /*while (!exit) {
            Thread.Wait
            for (byte i = 0; i < MAX_CLIENTS; i++) {
                if (clientThreads[i] != null) {
                    clientThreads[i].sendMessage(new Message(MessageType.Ping));
                }
            }
        }*/
    }

    private static void closeServer() {
        exit = true;
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
            //if (logins[clientIndex] != null) {
            //    wasConnected[clientIndex] = true;
            //}
            if (clientThreads[clientIndex] != null) {
                if (!clientThreads[clientIndex].isInterrupted()) {
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

    static void updateGameState() {
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            if (clientThreads[i] != null) {
                clientThreads[i].sendMessage(new Message(MessageType.GameState, gameState));
            }
        }
    }

    static int getNextPlayerId(int id) {
        id++;
        id %= MAX_CLIENTS;
        if (id == dealer)
            return getNextPlayerId(id);
        return id;
    }

    static void setNextDealer() {
        counter++;
        dealer++;
        dealer %= MAX_CLIENTS;

    }
}
