package server;

import shared.GameState;
import shared.Message;
import shared.MessageType;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Timestamp;

public class Server {
    private static final short PORT = 6969;
    private static final byte MAX_CLIENTS = 4;
    private static final Socket[] clientSockets = new Socket[MAX_CLIENTS];
    private static byte nConnectedClients = 0;
    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;
    private static final ClientThread[] clientThreads = new ClientThread[MAX_CLIENTS];
    private static Thread listenerThread = null;
    private static Thread pingThread = null;
    private static boolean exit = false;
    private static String[] logins = new String[MAX_CLIENTS];
    private static boolean[] hasLoginSet = new boolean[MAX_CLIENTS];
    private static int pingTimeout = 2000;  // time in milliseconds after which a not responding client is considered disconnected
    private static volatile boolean[] respondedToPing = new boolean[MAX_CLIENTS];
    private static boolean gameStarted = false;

    //
    static GameState gameState;
    static String word;
    static byte dealer;
    static int counter = 0;
    static final int NUMBER_OF_TURN = 8;

    static long turnStartTimestamp;
    private static final long maxTimeForPickingWord = 25 * 1000;//15 sekund
    private static final long maxTimeForPickingLetter = 15 * 1000;
    private static Thread timeLimitThread = null;


    public static void main(String[] args) {

        System.out.println("My IP addreses:");
        try {
            InetAddress inet = InetAddress.getLocalHost();
            InetAddress[] ips = InetAddress.getAllByName(inet.getCanonicalHostName());
            if (ips != null) {
                for (InetAddress ip : ips) {
                    System.out.println(ip);
                }
            }
        } catch (UnknownHostException ignored) {

        }

        for (int i = 0; i < MAX_CLIENTS; i++)
            respondedToPing[i] = true;

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
        pingThread = new Thread(Server::pingClients);
        pingThread.start();


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

    static void turnTaken() {
        turnStartTimestamp = System.currentTimeMillis();
    }

    private static void check_time() {
        long timeNow;
        byte currentPlayer;
        byte nextPlayer;
        while (!exit) {
            timeNow = System.currentTimeMillis();
            if (gameState.phase == GameState.Phase.ChoosingWord && (timeNow - turnStartTimestamp) > maxTimeForPickingWord) {
                currentPlayer = dealer;
                setNextDealer();
                Server.gameState.players[currentPlayer].hasTurn = false;
                Server.gameState.players[Server.dealer].hasTurn = true;
                System.out.println(logins[currentPlayer] + " lost turn, new dealer is " + dealer);

                Server.turnStartTimestamp = System.currentTimeMillis();
                Server.updateGameState();
            } else if (gameState.phase == GameState.Phase.Guess && (timeNow - turnStartTimestamp) > maxTimeForPickingLetter) {
                currentPlayer = getCurrentPlayer();
                nextPlayer = getNextPlayerId(currentPlayer);
                System.out.println(logins[currentPlayer] + " lost turn, " + logins[nextPlayer] + " has turn.");
                Server.gameState.players[currentPlayer].hasTurn = false;
                Server.gameState.players[nextPlayer].hasTurn = true;
                Server.turnStartTimestamp = System.currentTimeMillis();
                Server.updateGameState();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("timeout exception: " + e.getMessage());
            }
        }
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

        if (!gameStarted) {
            if (isEveryoneReady()) {    // if everyone has connected and set their logins
                startGame();
                gameStarted = true;
            }
        }

        return true;
    }

    private static void startGame() {
        System.out.println("Starting game.");
        gameState = new GameState();
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            gameState.players[i].points = 0;
            gameState.players[i].isConnected = isClientConnected(i);
            gameState.players[i].login = logins[i];
            gameState.players[i].hasTurn = false;
        }
        gameState.players[0].hasTurn = true;
        // gameState.keyboard is set in constructor
        // gameState.hangmanHealth is set in constructor
        gameState.phase = GameState.Phase.ChoosingWord;
        dealer = 0;
        Server.turnStartTimestamp = System.currentTimeMillis();
        updateGameState();
        timeLimitThread = new Thread(Server::check_time);
        timeLimitThread.start();
    }

    private static boolean isClientConnected(byte i) {
        return clientSockets[i] != null &&
                clientThreads[i] != null &&
                logins[i] != null &&
                hasLoginSet[i];
    }

    private static boolean isEveryoneReady() {
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            if (!isClientConnected(i))
                return false;
        }
        return true;
    }


    private static void broadcast(Message message) {
        for (byte i = 0; i < MAX_CLIENTS; i++) {
            if (clientThreads[i] != null) {
                clientThreads[i].sendMessage(message);
            }
        }
    }

    private static void pingClients() {
        while (!exit) {
            try {
                Thread.sleep(pingTimeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (byte i = 0; i < MAX_CLIENTS; i++) {
                if (clientThreads[i] != null) {
                    if (!respondedToPing[i]) {
                        System.out.println("Connection with " + getClientString(i) + " timed out.");
                        disconnectClient(i);
                    } else {
                        respondedToPing[i] = false;
                        clientThreads[i].sendMessage(new Message(MessageType.Ping));
                    }
                } else {
                    respondedToPing[i] = true;
                }
            }
        }
    }


    static void pingResponse(int clientIndex) {
        respondedToPing[clientIndex] = true;
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
            if (clientThreads[clientIndex] != null) {
                if (!clientThreads[clientIndex].isInterrupted()) {
                    clientThreads[clientIndex].sendMessage(new Message(MessageType.Disconnect), true);
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
        for(byte i = 0; i < MAX_CLIENTS; i++)
            gameState.players[i].isConnected = isClientConnected(i);
        printGameState();
        broadcast(new Message(MessageType.GameState, gameState));
    }

    private static void printGameState() {
        System.out.println("Game State:");
        System.out.println("\tPhase: " + gameState.phase);
        System.out.println("\tDealer: " + dealer);
        System.out.println("\tWord: " + gameState.word + " / " + word);
        System.out.println("\tHangman health: " + gameState.hangmanHealth + "/" + 7);
        System.out.println("\tPlayers:");
        for (int i = 0; i < MAX_CLIENTS; i++) {
            System.out.println("\t\t" + gameState.players[i].login + ":");
            System.out.println("\t\t\tpoints: " + gameState.players[i].points);
            System.out.println("\t\t\thasTurn: " + gameState.players[i].hasTurn);
            System.out.println("\t\t\tisConnected: " + gameState.players[i].isConnected);
        }
        System.out.print("\tUsed letters:");
        for(char c = 'A'; c <= 'Z'; c++)
            if (!gameState.keyboard.get(c))
                System.out.print(" " + c);
        System.out.println();
    }

    private static byte getCurrentPlayer() {
        for (byte i = 0; i < MAX_CLIENTS; i++)
            if (gameState.players[i].hasTurn)
                return i;
        return MAX_CLIENTS;
    }

    static byte getNextPlayerId(byte id) {

        id++;
        id %= MAX_CLIENTS;
        if (id == dealer || !gameState.players[id].isConnected)
            return getNextPlayerId(id);
        turnStartTimestamp = System.currentTimeMillis();

        return id;
    }

    static void setNextDealer() {
        counter++;
        dealer++;
        dealer %= MAX_CLIENTS;

    }

    private static String getClientString(byte index) {
        if (logins[index] != null && hasLoginSet[index]) {
            return logins[index];
        } else if (clientSockets[index] != null) {
            return clientSockets[index].getInetAddress() + ":" + clientSockets[index].getPort();
        }
        return null;
    }
}
