package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class clientThread extends Thread {
    private Socket clientSocket;
    private byte clientIndex;

    clientThread(Socket clientSocket, byte clientIndex) {
        this.clientSocket = clientSocket;
        this.clientIndex = clientIndex;
    }

    @Override
    public void run() {
        BufferedReader bufferedReader;
        BufferedWriter bufferedWriter;
        try {
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
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
            return;
        } catch (IOException e) {
            e.printStackTrace();
            Server.disconnectClient(clientIndex);
            return;
        }

        String line;
        while (true) {
            try {
                bufferedWriter.write("Elo 3 2 0\n");
                bufferedWriter.flush();

                line = bufferedReader.readLine();
                if (line == null) {
                    clientSocket.close();
                    return;
                }
            }  catch (SocketException e) {
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
                return;
            } catch (IOException e) {
                e.printStackTrace();
                Server.disconnectClient(clientIndex);
                return;
            }
        }
    }
}
