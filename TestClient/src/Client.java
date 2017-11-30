import shared.*;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;


public class Client {

    private static final short SERVER_PORT = 6969;
    private static int localPort;
    private static final String SERVER_IP = "127.0.0.1";
    private static Socket serverSocket;
    private static Thread clientThread = null;
    private static boolean exit = false;
    private static ObjectInputStream objectInputStream;
    private static ObjectOutputStream objectOutputStream;
    private static String login = null;

    public static void main(String[] args) {
        try {
            System.out.println("Connecting to server...");
            serverSocket = new Socket(SERVER_IP, SERVER_PORT);
            localPort = serverSocket.getLocalPort();
            System.out.println("Connected to server at " + SERVER_IP + ":" + SERVER_PORT + ", local port: " + localPort);
            System.out.print("\tCreating output stream...");
            objectOutputStream = new ObjectOutputStream(serverSocket.getOutputStream());
            System.out.println(" OK");
            System.out.print("\tCreating input stream...");
            objectInputStream = new ObjectInputStream(serverSocket.getInputStream());   // will hang there if server doesn't accept connection
            System.out.println(" OK");
        } catch (ConnectException e) {
            switch (e.getMessage()) {
                case "Connection refused: connect":
                    System.out.println("Server unavailable.\nTerminating client.");
                    return;
                default:
                    e.printStackTrace();
                    return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        clientThread = new Thread(Client::client);
        clientThread.start();

        BufferedReader consoleBufferedReader = new BufferedReader(new InputStreamReader(System.in));
        do {
            try {
                if (consoleBufferedReader.ready()) {
                    switch (consoleBufferedReader.readLine().toLowerCase()) {
                        case "login":
                            System.out.print("Enter your login: ");
                            login = consoleBufferedReader.readLine();
                            sendMessage(new Message(MessageType.Connect, login));
                            break;
                        case "letter":
                            System.out.print("Enter your word: ");
                            String letter = consoleBufferedReader.readLine();
                            sendMessage(new Message(MessageType.PickLetter, letter.charAt(0)));
                            break;
                        case "word":
                            System.out.print("Enter your word: ");
                            String word = consoleBufferedReader.readLine();
                            sendMessage(new Message(MessageType.PickWord, word));
                            break;
                        case "exit":
                            System.out.println("Closing client...");
                            closeClient();
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
        } while (!exit);
    }

    private static void closeClient() {
        sendMessage(new Message(MessageType.Disconnect, null));
        clientThread.interrupt();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void client() {
        Message message;
        boolean printMessage;
        while (!Thread.interrupted()) {
            message = readMessage();
            if (message == null)    // client should quit
                return;
            printMessage = true;
            switch (message.type) {
                case Connect:  // won't happen in target app
                    break;
                case Disconnect:   // server wants to disconnect
                    exit = true;
                    System.out.println("Server closed connection. Closing client.");
                    return;
                case LoginTaken:
                    break;
                case Ping:
                    sendMessage(new Message(MessageType.Ping));
                    printMessage = false;
                    break;
                case GameState:
                    GameState gameState = (GameState)message.data;
                    System.out.println("Game State:");
                    System.out.println("\tPhase: " + gameState.phase);
                    System.out.println("\tWord: " + gameState.word);
                    System.out.println("\tHangman health: " + gameState.hangmanHealth + "/" + 7);
                    System.out.println("\tPlayers:");
                    for (int i = 0; i < 4; i++) {
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
                    break;
                default:
                    System.out.println("Unknown message type received from server.");
            }
            if (printMessage)
                System.out.println("Message from server: " + message.type + ": " + message.data);
        }
    }

    private static void sendMessage(Message message) {
        try {
            objectOutputStream.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Message readMessage() {
        try {
            return (Message) objectInputStream.readObject();
        } catch (SocketException e) {
            switch (e.getMessage()) {
                case "Socket closed":   // client is exiting
                    return null;
                case "Connection reset":   // server side fault
                    System.out.println("Server is down. Trying to reconnect...");
                    //TODO: reconnect
                    break;
                default:
                    e.printStackTrace();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
