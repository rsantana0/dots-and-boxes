import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) throws Exception {
        ServerSocket listener = new ServerSocket(8901);
        System.out.println("Server is running...");
        try {
            while (true) {
                /* Start the game and wait for players to connect. Assign the first
                   player to the color blue and second player to the color red. */
                Game game = new Game();
                Game.Player player1 = game.new Player(listener.accept(), 'B');
                Game.Player player2 = game.new Player(listener.accept(), 'R');
                player1.setOpponent(player2);
                player2.setOpponent(player1);
                game.currentPlayer = player1;
                player1.start();
                player2.start();
            }
        } finally {
            listener.close();
        }
    }
}

class Game {

    private int gridWidth = 9; //must be odd number greater than 3
    private Player[] board = new Player[gridWidth*gridWidth];
    Player currentPlayer;
    private boolean goAgain;
    private int Squares;
    private int bluePoints;
    private int redPoints;

    public String gameWinner() {
        // Find out which player has the most points
        System.out.println("Blue: " + bluePoints + " points");
        System.out.println("Red: " + redPoints + " points");
        if (bluePoints == redPoints) { return "TIE"; }
        else { return (bluePoints > redPoints) ? "BLUE" : "RED"; }
    }

    public int checkSquares() {
        // Count the number of boxes completed so far
        int s = 0;

        int m = (gridWidth-1)/2;
        int i = 1;
        for (int k = 1; k < m*m; k++) {
            if (board[i] != null && board[i+gridWidth-1] != null && board[i+gridWidth+1] != null
                    && board[i+2*gridWidth] != null) {
                s++;
            }
            i = (k % m == 0) ? (i + gridWidth + 3) : (i + 2);
        }

        return s;
    }

    public boolean completedSquare() {
        // Return true if the current player completes a box
        int n = checkSquares();
        if (n > Squares) {
            Squares = n;
            return true;
        }
        return false;
    }

    public boolean boardFilledUp() {
        // Game ends when the board is filled up
        for (int i = 0; i < ((board.length-1)/2); i++) {
            if (board[2*i+1] == null) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean legalMove(int location, Player player) {
        // Determine if the move made by the player is allowed
        if (player == currentPlayer && board[location] == null) {
            board[location] = currentPlayer;
            if (completedSquare()) {
                // Give the player points when they complete boxes and let them have an extra turn
                int x = Squares - (bluePoints + redPoints);
                if (currentPlayer.mark == 'B') { bluePoints += x; }
                else { redPoints += x; }
                goAgain = true;
                currentPlayer.opponent.otherPlayerMoved(location);
            }
            else {
                // End the current player's turn
                goAgain = false;
                currentPlayer = currentPlayer.opponent;
                currentPlayer.otherPlayerMoved(location);
            }
            return true;
        }
        return false;
    }

    class Player extends Thread {
        char mark;
        Player opponent;
        Socket socket;
        BufferedReader input;
        PrintWriter output;

        public Player(Socket socket, char mark) {
            this.socket = socket;
            this.mark = mark;
            try {
                input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);
                output.println("WELCOME " + mark);
                output.println("MESSAGE Waiting for opponent to connect");
            } catch (IOException e) {
                System.out.println("Player died: " + e);
            }
        }

        public void setOpponent(Player opponent) {
            this.opponent = opponent;
        }

        public void otherPlayerMoved(int location) {
            String again = goAgain ? "Y" : "N";
            output.println("OPPONENT_MOVED " + again + location);
            output.println(boardFilledUp() ? gameWinner() : "");
        }

        public void run() {
            Squares = 0;
            bluePoints = 0;
            redPoints = 0;
            goAgain = false;
            try {
                output.println("MESSAGE All players connected");

                if (mark == 'B') {
                    output.println("MESSAGE Your move");
                }

                while (true) {
                    String command = input.readLine();
                    if (command.startsWith("MOVE")) {
                        int location = Integer.parseInt(command.substring(5));
                        if (legalMove(location, this)) {
                            output.println(goAgain ? "VALID_MOVE Y" : "VALID_MOVE N");
                            output.println(boardFilledUp() ? gameWinner() : "");
                        } else {
                            output.println("MESSAGE ?");
                        }
                    } else if (command.startsWith("QUIT")) {
                        return;
                    }
                }
            } catch (IOException e) {
                System.out.println("Player disconnected: " + e);
            } finally {
                try {socket.close();} catch (IOException e) {}
            }
        }
    }
}
