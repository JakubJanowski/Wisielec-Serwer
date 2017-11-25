package shared;

import java.util.Dictionary;
import java.util.Hashtable;

public class GameState {
    private class Player {
        int points;
        String login;
        boolean isConnected;
        boolean hasTurn;
    }

    private enum Phase {
        ChoosingWord,
        Guess,
        EndGame
    }

    Player players[];
    Dictionary<Character, Boolean> keyboard;
    String word;
    Phase phase;
    int hangmanHealth;

    public GameState() {
        players = new Player[4];
        keyboard = new Hashtable<>(26);
        for(char c = 'A'; c <= 'Z'; c++) {
            keyboard.put(c, false);
        }
    }
}
