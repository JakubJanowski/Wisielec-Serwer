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

    private int my_number;
    private String mylogin;
    private char recived_myletter;


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
        while (!interrupted()) {
            sendMessage(new Message(MessageType.Connect, "Elo 3 2 0\n"));
            message = readMessage();
            switch (message.type) {
                case Disconnect:   // client wants to disconnect
                    System.out.println("Client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " closed connection.");
                    Server.disconnectClient(clientIndex);
                    return;
                case PickLetter:
                    recived_myletter=(char)message.data;

                    if(Server.gameState.players[my_number].hasTurn)
                    {
                        System.out.println("Client "+mylogin+ " guessed letter '"+ recived_myletter+"' with success");
                        System.out.println("Client "+mylogin+ " guessed letter '"+ recived_myletter+"' with failure");

                    }
                    else
                        System.out.println("Client "+mylogin+ "send message with letter not in his turn, his messsage is ignored");


                    return;
                case PickWord:
                    if(Server.dealer == my_number && Server.gameState.players[my_number].hasTurn && Server.gameState.phase == GameState.Phase.ChoosingWord ){
                        String word = ((String)message.data).toUpperCase();
                        System.out.println("Client "+mylogin+ " picked word \""+ word +"\" with success");
                        Server.word = word;
                        Server.gameState.phase = GameState.Phase.Guess;
                        Server.gameState.players[my_number].hasTurn = false;
                        Server.gameState.players[Server.getNextPlayerId(my_number)].hasTurn = true;
                        Server.gameState.word = "";
                        for(int i = 0; i < Server.word.length(); i++){
                            Server.gameState.word += "_";
                        }
                        Server.updateGameState();
                    }
                    else
                        System.out.println("Client "+mylogin+ "send message with word not in his turn, his messsage is ignored");
                case ConnectionError:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  connection error.");
                    Server.disconnectClient(clientIndex);
                    return;
                case Unknown:
                    System.out.println(clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "  uses different application version and communication won't be possible.");
                default:
                    System.out.println("Unknown message type received from client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            }
            System.out.println("Message from client: " + message.type + ": " + message.data);
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
