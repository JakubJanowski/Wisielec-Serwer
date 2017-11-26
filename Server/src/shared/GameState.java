package shared;

import java.util.Dictionary;
import java.util.Hashtable;



public class GameState {
    public class Player {
        public int points;
        public String login;
        public boolean isConnected;
        public boolean hasTurn;
    }

    public enum Phase {
        ChoosingWord,
        Guess,
        EndGame
    }

    public Player players[];
    public Dictionary<Character, Boolean> keyboard;
    public String word;
    public Phase phase;
    public int hangmanHealth;

    public GameState() {
        players = new Player[4];
        keyboard = new Hashtable<>(26);
        for(char c = 'A'; c <= 'Z'; c++) {
            keyboard.put(c, false);
        }
    }
}
