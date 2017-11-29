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
            if(interrupted())
                return;
            message = readMessage();
            switch (message.type) {
                case Connect:
                    login = message.data.toString();
                    result = Server.setLogin(clientIndex, login);
                    if (!result) {
                        sendMessage(new Message(MessageType.LoginTaken));
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
                    login = message.data.toString();
                    Server.setLogin(clientIndex, login);
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
                    char received_myletter = (char) message.data;
                    if (Server.gameState.players[clientIndex].hasTurn && Server.gameState.phase == GameState.Phase.Guess) {
                        if (Server.word.contains(Character.toString(received_myletter))) {
                            System.out.println("Client " + login + " guessed letter '" + received_myletter + "' with success");

                            for (int i = 0; i < Server.word.length(); i++)
                                if (Server.word.charAt(i) == received_myletter) {
                                    String first = Server.gameState.word.substring(0, i);
                                    String third = Server.gameState.word.substring(i + 1);//TODO Nie wiem czy nie bedzie wyjatek przy odgadywaniu ostatniej litery
                                    Server.gameState.word = first + received_myletter + third;
                                }
                            Server.gameState.keyboard.remove(received_myletter);
                            Server.gameState.keyboard.put(received_myletter, true);//
                            if (Server.word.contains("_"))//sprawdz czy koniec
                            {
                                Server.gameState.players[clientIndex].hasTurn = true;
                                Server.updateGameState();
                            } else//haslo cale zostalo zgadniete
                            {
                                Server.gameState.players[clientIndex].points += 1;
                                Server.gameState.phase = GameState.Phase.ChoosingWord;
                                Server.gameState.players[clientIndex].hasTurn = false;
                                Server.gameState.players[Server.dealer].hasTurn = true;
                                Server.setNextDealer();
                                if (Server.counter == Server.NUMBER_OF_TURN)//koniec calej rozgrywki
                                {
                                    Server.gameState.phase = GameState.Phase.EndGame;
                                }
                                Server.updateGameState();
                            }

                        } else
                            System.out.println("Client " + login + " guessed letter '" + received_myletter + "' with failure");
                        Server.gameState.hangmanHealth -= 1;
                        if (Server.gameState.hangmanHealth == 0) {
                            Server.gameState.players[Server.dealer].points += 1;
                            Server.setNextDealer();
                            Server.gameState.players[clientIndex].hasTurn = false;
                            Server.gameState.players[Server.dealer].hasTurn = true;
                            Server.gameState.phase = GameState.Phase.ChoosingWord;
                            if (Server.counter == Server.NUMBER_OF_TURN)//calkowity koniec rozgrywki
                            {
                                Server.gameState.phase = GameState.Phase.EndGame;
                            }
                            Server.updateGameState();
                        } else {
                            Server.gameState.players[Server.getNextPlayerId(clientIndex)].hasTurn = true;
                            Server.gameState.players[clientIndex].hasTurn = false;
                            Server.updateGameState();
                        }
                    } else
                        System.out.println("Client " + login + " has send message with letter not in his turn, his messsage is ignored");


                    break;
                case PickWord:
                    if (Server.dealer == clientIndex && Server.gameState.players[clientIndex].hasTurn && Server.gameState.phase == GameState.Phase.ChoosingWord) {
                        String word = ((String) message.data).toUpperCase();
                        System.out.println("Client " + login + " picked word \"" + word + "\" with success");
                        Server.word = word;
                        Server.gameState.phase = GameState.Phase.Guess;
                        Server.gameState.players[clientIndex].hasTurn = false;
                        Server.gameState.players[Server.getNextPlayerId(clientIndex)].hasTurn = true;
                        StringBuilder stringBuilder = new StringBuilder(Server.word.length());
                        for (int i = 0; i < Server.word.length(); i++) {
                            stringBuilder.append('_');
                        }
                        Server.gameState.word = stringBuilder.toString();
                        Server.updateGameState();
                    } else
                        System.out.println("Client " + login + "send message with word not in his turn, his messsage is ignored");
                    break;
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
    }

    void sendMessage(Message message, boolean suppress) {
        try {
            objectOutputStream.writeObject(message);
        } catch (IOException e) {
            if(!suppress)
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
