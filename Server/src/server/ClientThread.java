package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

import shared.*;

public class ClientThread extends Thread {
    private Socket clientSocket;
    private byte clientIndex;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    private String login;


    ClientThread(Socket clientSocket, byte clientIndex) {
        this.clientSocket = clientSocket;
        this.clientIndex = clientIndex;
        try {
            System.out.print("\tCreating output stream...");
            objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            System.out.println(" OK");
            System.out.print("\tCreating input stream...");
            objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
            System.out.println(" OK");
        } catch (SocketException e) {
            switch (e.getMessage()) {
                case "Connection reset":
                    System.out.println("Server reset connection for client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    break;
                case "Connection reset by peer":
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " disconnected.");
                    break;
                default:
                    e.printStackTrace();
            }
            Server.disconnectClient(clientIndex);
        } catch (IOException e) {
            e.printStackTrace();
            Server.disconnectClient(clientIndex);
        }
    }

    @Override
    public void run() {
        Message message;
        boolean result = false;
        sendMessage(new Message(MessageType.Connect, "Elo 3 2 0\n"));

        while (!result) {
            if (interrupted())
                return;
            message = readMessage();
            switch (message.type) {
                case Connect:
                    login = message.data.toString();
                    result = Server.setLogin(clientIndex, login);
                    if (!result) {
                        sendMessage(new Message(MessageType.LoginTaken));
                    } else {
                        System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " set his login to " + login);
                    }
                    break;
                case ConnectionError:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  connection error.");
                    Server.disconnectClient(clientIndex);
                    return;
                case Disconnect:   // client wants to disconnect
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " closed connection.");
                    Server.disconnectClient(clientIndex);
                    return;
                case Ping:
                    Server.pingResponse(clientIndex);
                    break;
                case Unknown:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  uses different application version and communication won't be possible.");
                default:
                    System.out.println("Unknown message type received from client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            }
            //System.out.println("Message from client: " + message.type + ": " + message.data);
        }

        // game logic
        while (!interrupted()) {
            message = readMessage();
            switch (message.type) {
                case Connect:
                    // should not happen
                    break;
                case ConnectionError:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  connection error.");
                    Server.disconnectClient(clientIndex);
                    return;
                case Disconnect:   // client wants to disconnect
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " closed connection.");
                    Server.disconnectClient(clientIndex);
                    return;
                case PickLetter:
                    char letter = Character.toUpperCase((char) message.data);
                    if (Server.gameState.players[clientIndex].hasTurn && Server.gameState.phase == GameState.Phase.Guess) {
                        if (Server.word.contains(Character.toString(letter))) {
                            System.out.println(login + " guessed letter '" + letter + "' with success");

                            for (int i = 0; i < Server.word.length(); i++) {
                                if (Server.word.charAt(i) == letter) {
                                    String first = Server.gameState.word.substring(0, i);
                                    String third = Server.gameState.word.substring(i + 1);//TODO Nie wiem czy nie bedzie wyjatek przy odgadywaniu ostatniej litery
                                    Server.gameState.word = first + letter + third;
                                }
                            }
                            ///Server.gameState.keyboard.remove(letter);
                            Server.gameState.keyboard.put(letter, true);//
                            if (Server.gameState.word.contains("_")) {   // sprawdz czy koniec
                                Server.gameState.players[clientIndex].hasTurn = true;
                                Server.turnStartTimestamp = System.currentTimeMillis();
                            } else {    // haslo cale zostalo zgadniete
                                Server.gameState.players[clientIndex].points += 1;
                                Server.gameState.phase = GameState.Phase.ChoosingWord;
                                Server.gameState.players[clientIndex].hasTurn = false;
                                Server.gameState.players[Server.dealer].hasTurn = true;
                                Server.setNextDealer();
                                if (Server.counter == Server.NUMBER_OF_TURN)    // koniec calej rozgrywki
                                    Server.gameState.phase = GameState.Phase.EndGame;
                                Server.turnStartTimestamp = System.currentTimeMillis();
                            }
                        } else {
                            System.out.println(login + " guessed letter '" + letter + "' with failure");
                            Server.gameState.hangmanHealth -= 1;
                            if (Server.gameState.hangmanHealth == 0) {
                                Server.gameState.players[Server.dealer].points += 1;
                                Server.setNextDealer();
                                Server.gameState.players[clientIndex].hasTurn = false;
                                Server.gameState.players[Server.dealer].hasTurn = true;
                                Server.gameState.phase = GameState.Phase.ChoosingWord;
                                if (Server.counter == Server.NUMBER_OF_TURN)    // calkowity koniec rozgrywki
                                    Server.gameState.phase = GameState.Phase.EndGame;
                            } else {
                                Server.gameState.players[Server.getNextPlayerId(clientIndex)].hasTurn = true;
                                Server.gameState.players[clientIndex].hasTurn = false;
                            }
                        }
                        Server.updateGameState();
                        Server.turnTaken();
                    } else {
                        System.out.println(login + " sent letter not in his turn. Ignoring message.");
                    }


                    break;
                case PickWord:
                    if (Server.dealer == clientIndex && Server.gameState.players[clientIndex].hasTurn && Server.gameState.phase == GameState.Phase.ChoosingWord) {
                        String word = ((String) message.data).toUpperCase();
                        System.out.println(login + " picked word: " + word);
                        Server.word = word;
                        Server.gameState.phase = GameState.Phase.Guess;
                        Server.gameState.players[clientIndex].hasTurn = false;
                        Server.gameState.players[Server.getNextPlayerId(clientIndex)].hasTurn = true;
                        StringBuilder stringBuilder = new StringBuilder(Server.word.length());
                        for (int i = 0; i < Server.word.length(); i++)
                            stringBuilder.append('_');
                        Server.gameState.word = stringBuilder.toString();
                        Server.updateGameState();
                        Server.turnTaken();
                    } else {
                        System.out.println(login + " sent word not in his turn. Ignoring message.");
                    }
                    break;
                case Ping:
                    Server.pingResponse(clientIndex);
                    break;
                case Unknown:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  uses different application version. Communication won't be possible.");
                default:
                    System.out.println("Unknown message type received from client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            }
            //System.out.println("Message from client: " + message.type + ": " + message.data);
        }
    }

    void sendMessage(Message message, boolean suppress) {
        try {
            objectOutputStream.writeObject(message);
        } catch (IOException e) {
            if (!suppress)
                e.printStackTrace();
        }
    }

    void sendMessage(Message message) {
        try {
            objectOutputStream.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Message readMessage() {
        try {
            return (Message) objectInputStream.readObject();
        } catch (SocketException e) {
            switch (e.getMessage()) {
                case "Connection reset":
                    System.out.println("Server reset connection for client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    break;
                case "Connection reset by peer":
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " disconnected.");
                    break;
                case "Socket closed":
                    System.out.println("Closed socket for " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    break;
                default:
                    e.printStackTrace();
            }
            return new Message(MessageType.ConnectionError);
        } catch (IOException e) {
            //e.printStackTrace();
            return new Message(MessageType.ConnectionError);
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
            return new Message(MessageType.Unknown);
        }
    }
}
