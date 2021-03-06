/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Ray Santana
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/*
   W [B|R]        -  Welcome message with assigned color.
   V [Y|N] [pos]  -  Validation message with position and extra turn.
   O [Y|N] [pos]  -  Opponent's move with position and extra turn
   E [B|R|T]      -  End game message with winner color (or T for tie).
   I [message]    -  Information sent by server for client to display.
   M [pos]        -  Move made by player; message sent by client.
   S [B|R] [pos]  -  Square completed; include position and player color.
   Q [B|R]        -  Quit message (with player color) sent by client.
*/

public class Server {
    private static final int PORT = 8901; /* chosen arbitrarily */

    public static void main(String[] args) {
        /* Open the server socket and wait for players to connect. Assign the
           first player to color blue and second player to the color red.
           Multiple games can be running at the same time, but each instance
           can only have two players playing. */
        ServerSocket listener = null;      
        try {
            listener = new ServerSocket(PORT);
            System.out.println("Server has started...");
            while (true) {
                Game game = new Game();
                Game.Player player1 = game.new Player(listener.accept(), 'B');
                Game.Player player2 = game.new Player(listener.accept(), 'R');
                player1.setOpponent(player2);
                player2.setOpponent(player1);
                game.currentPlayer = player1; /* Player 1 goes first */
                player1.start();
                player2.start();
            }
        } catch (Exception error) {
            error.printStackTrace();
        } finally {
            try {
                listener.close();
            } catch (IOException e) {};
        } 
    }
}

class Game {
    /* This class handles the logic for each instance of the game that
       is currently running on the server. Grid length can be an odd
       number greater than 3 (ideally 5 < L < 21), and both client 
       and server must use the same value. */
    private static final int GRID_LENGTH = 7;
    private char[] board = new char[GRID_LENGTH*GRID_LENGTH];
    private int bluePoints = 0;
    private int redPoints = 0;
    Player currentPlayer;

    public char gameWinner() {
        /* Find out which player has the most points. */
        System.out.println("\n***Game Ended***");
        System.out.println("Blue: " + bluePoints + " points");
        System.out.println("Red: " + redPoints + " points");
        System.out.println("****************\n");
        if (bluePoints == redPoints) { 
            return 'T';
        } else { 
            return (bluePoints > redPoints) ? 'B' : 'R';
        }
    }

    public int countCompletedSquares(int pos, Player player) {
        /* Count the number grid squares that have been completed. */
        int row = pos / GRID_LENGTH;
        int numCompletedSquares = 0;

        if (row % 2 == 0) {
            /* The player chose a horizontal grid line. */
            if (row != 0) {
                /* Check if the grid square above is complete. */
                if (board[pos-2*GRID_LENGTH] != 0
                 && board[pos-GRID_LENGTH+1] != 0
                 && board[pos-GRID_LENGTH-1] != 0) {
                    ++numCompletedSquares;
                    player.squareCompleted(pos-GRID_LENGTH);
                }
            } 
            if (row != (GRID_LENGTH-1)) {
                /* Check if the grid square below is complete. */
                if (board[pos+2*GRID_LENGTH] != 0
                 && board[pos+GRID_LENGTH+1] != 0
                 && board[pos+GRID_LENGTH-1] != 0) {
                    ++numCompletedSquares;
                    player.squareCompleted(pos+GRID_LENGTH);
                }
            } 
        } else {
            /* The player chose a vertical grid line. */
            int col = pos % GRID_LENGTH;

            if (col != 0) {
                /* Check if the grid square on the left is complete. */
                if (board[pos-2] != 0
                 && board[pos-GRID_LENGTH-1] != 0
                 && board[pos+GRID_LENGTH-1] != 0) {
                    ++numCompletedSquares;
                    player.squareCompleted(pos-1);
                }
            }
            if (col != (GRID_LENGTH-1)) {
                /* Check if the grid square on the right is complete. */
                if (board[pos+2] != 0
                 && board[pos+GRID_LENGTH+1] != 0
                 && board[pos-GRID_LENGTH+1] != 0) {
                     ++numCompletedSquares;
                     player.squareCompleted(pos+1);
                 }
            }
        }

        return numCompletedSquares;
    }

    public boolean boardFilledUp() {
        /* Check if grid is complete and game should end. */
        int totalPossibleSquares = (GRID_LENGTH / 2) * (GRID_LENGTH / 2);
        return (totalPossibleSquares == (bluePoints + redPoints));
    }

    public synchronized boolean legalMove(int position, Player player) {
        /* Determine if the move made by the player is allowed. */
        if ((player == currentPlayer) && (board[position] == 0)) {
            board[position] = currentPlayer.playerColor;
            
            int numSquares = countCompletedSquares(position, player);
            if (numSquares > 0) {
                /* Add the number of completed squares to the current
                   player's score. */
                currentPlayer.thisPlayerMoved(position, true);
                if (player.playerColor == 'B') {
                    bluePoints += numSquares;
                } else {
                    redPoints += numSquares;
                }
            } else {
                /* Finish current player's turn and let the opponent play. */
                currentPlayer.thisPlayerMoved(position, false);
                currentPlayer = currentPlayer.opponent;
            }

            return true;
        }

        return false;
    }

    class Player extends Thread {
        /* Use a thread to handle messages to and from each client. */
        private BufferedReader input;
        private PrintWriter output;
        private Socket socket;
        private Player opponent;
        private char playerColor;

        public Player(Socket socket, char pColor) {
            /* Open the socket to the client and send welcome message. */
            this.socket = socket;
            this.playerColor = pColor;
            try {
                input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);
                output.println("W " + playerColor);
                output.println("I Waiting for opponent to connect...");
            } catch (IOException e) {
                System.out.println("Couldn't connect to player :(");
                e.printStackTrace();
            }
        }

        public void setOpponent(Player opponent) {
            this.opponent = opponent;
        }

        public void thisPlayerMoved(int position, boolean goAgain) {
            /* Notify clients if the current player has made a valid move, and
               if they get an extra turn. */
            String again = goAgain ? "Y" : "N";
            output.println("V " + again + " " + position);
            opponent.output.println("O " + again + " " + position);
        }

        public void squareCompleted(int position) {
            /* Notify clients the position and color associated with a
               completed grid square. */
            String message = "S " + playerColor + " " + position;
            output.println(message);
            opponent.output.println(message);
        }

        @Override
        public void run() {
            String command;
            try {
                /* Inform clients that both players have connected.
                   Blue goes first. */
                output.println("I All players connected");
                if (playerColor == 'B') {
                    output.println("I Your move");
                }

                while (true) {
                    command = input.readLine();
                    command = command.isEmpty() ? " " : command;
                    if (command.charAt(0) == 'M') {
                        /* Check if current player's move is valid and if the
                           game has ended. */
                        int position = Integer.parseInt(command.substring(2));
                        if (legalMove(position, this)) {
                            if (boardFilledUp()) {
                                char winner = gameWinner();
                                output.println("E " + winner);
                                opponent.output.println("E " + winner);
                            }
                        } else {
                            output.println("I Invalid move");
                        }                        
                    } else if (command.charAt(0) == 'Q') {
                        /* Player has quit the game. */
                        if (command.charAt(2) == playerColor) {
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Player disconnected :(");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }
    }
}
