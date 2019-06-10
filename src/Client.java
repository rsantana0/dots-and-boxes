import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;

public class Client {
    /* Variables required for graphics and logic portion of the game. Grid
       width can be an odd number greater than 3, and both client and 
       server must use the same value. */
    private static final int GRID_WIDTH = 9;
    private JFrame frame = new JFrame("Dots&Boxes");
    private JLabel messageLabel = new JLabel("");
    private Square[] board = new Square[GRID_WIDTH * GRID_WIDTH];
    private char playerColor;

    /* Variables required for network portion of game. Client must use the
       port that the server socket is listening to. */
    private static final int PORT = 8901;
    private BufferedReader inBuffer;
    private PrintWriter outBuffer;
    private Socket socket;
    
    public Client(String serverAddress) {
        /* Open socket and buffers to communicate with the server. */
        try {
            socket = new Socket(serverAddress, PORT);
            inBuffer = new BufferedReader(
                       new InputStreamReader(socket.getInputStream()));
            outBuffer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException error) {
            error.printStackTrace();
        }

        /* Add background panel and the game graphics to the window frame. */
        JPanel boardPanel = new JPanel();
        boardPanel.setBackground(Color.black);
        boardPanel.setLayout(new GridLayout(GRID_WIDTH, GRID_WIDTH));
        for (int i = 0; i < board.length; i++) {
            board[i] = new Square(i);
            boardPanel.add(board[i]);
        }
        frame.getContentPane().add(boardPanel, "Center");
        messageLabel.setBackground(Color.lightGray);
        frame.getContentPane().add(messageLabel, "South");
    }

    private class Square extends JPanel {
        /* This class handles the individual squares (50px by 50px) that
           make up the game's grid. */
        private static final long serialVersionUID = 1L;
        private int position;

        public Square(int pos) {
            this.position = pos;

            MouseAdapter mouseEvent = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    /* Inform the server to determine if the player's
                       move is valid. */
                    outBuffer.println("M " + pos);
                }
            };

            if (pos % 2 != 0) {
                /* Add a mouse listener to appropriate parts of the grid. */
                this.addMouseListener(mouseEvent);
            }
        }

        @Override
        public void paintComponent(Graphics g) {
            int row = (this.position / GRID_WIDTH) + 1;
            Graphics2D gDraw = (Graphics2D) g;

            /* Turn on anti-aliasing so the grid dots look better. */
            gDraw.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);

            if (this.position % 2 != 0) {
                if (row % 2 != 0) {
                    /* Draw a horizontal line on the grid. */
                    gDraw.fillRect(0, 17, 50, 16);
                } else {
                    /* Draw a vertical line on the grid. */
                    gDraw.fillRect(17, 0, 16, 50);
                }
            } else if (row % 2 != 0) {
                /* Draw a dot on the grid. */
                gDraw.setColor(Color.LIGHT_GRAY);
                gDraw.fillOval(10, 10, 30, 30);
                gDraw.fillRect(5, 17, 40, 16);
                gDraw.fillRect(17, 5, 16, 40);
            }
        }
    }

    private void endGame() {
        /* Close socket and window when player quits. */
        outBuffer.println("Q");
        try {
            socket.close();
        } catch (IOException error) {
            error.printStackTrace();
        }
        frame.dispose();
    }

    private boolean playAgain() {
        /* Ask the player if they would like to play again. */
        int response = JOptionPane.showConfirmDialog(frame,
                       "Play again?", messageLabel.getText(),
                       JOptionPane.YES_NO_OPTION);
        frame.dispose();
        return (response == JOptionPane.YES_OPTION);
    }

    private void readMessages() throws IOException {
        Boolean endGame = false;
        String response;
        int position;
        char msgType;
 
        while (!endGame) {
            response = inBuffer.readLine();
            msgType = response.isEmpty() ? ' ' : response.charAt(0);
            switch (msgType) {
                case 'S': /* Grid square has been completed */
                    position = Integer.parseInt(response.substring(4));
                    board[position].setForeground(
                        (response.charAt(2) == 'B') ? Color.blue : Color.red);
                    break;

                case 'V': /* Server message validating player's move. */
                    if (response.charAt(2) == 'Y') {
                        /* Player completed a grid square; can move again. */
                        messageLabel.setText("Valid move, go again.");
                    } else {
                        messageLabel.setText("Opponent's turn, please wait");
                    }
                    /* Set color of the grid line chosen by the player. */
                    position = Integer.parseInt(response.substring(4));
                    board[position].setForeground(
                            (playerColor == 'B') ? Color.blue : Color.red);
                    break;

                case 'O': /* Server message validating opponent's move. */
                    if (response.charAt(2) == 'Y') {
                        /* Opponent completed a grid square; can move again. */
                        messageLabel.setText("Opponent goes again.");
                    } else {
                        messageLabel.setText("Opponent moved, your turn.");
                    }
                    position = Integer.parseInt(response.substring(4));
                    board[position].setForeground(
                            (playerColor == 'B') ? Color.red : Color.blue);
                    break;

                case 'E': /* Game ended and winner is determined by server. */
                    if (response.charAt(2) == playerColor) {
                        messageLabel.setText("YOU WIN!");
                    } else if (response.charAt(2) == 'T') {
                        messageLabel.setText("YOU TIED");
                    } else {
                        messageLabel.setText("YOU LOSE");
                    }
                    endGame = true;
                    break;

                case 'I': /* Generic message received from server. */
                    messageLabel.setText(response.substring(2));
                    break;
            }
        }
    }

    public void play() {
        /* Handle client/server communication while game is in progress. */
        String serverMessage;

        try {
            while (true) {
                /* Get player's color from the server welcome (W) message. */
                serverMessage = inBuffer.readLine() + " ";
                if (serverMessage.charAt(0) == 'W') {
                    playerColor = serverMessage.charAt(2);
                    String colorLabel = (playerColor == 'B') ? "Blue" : "Red";
                    frame.setTitle("Dots & Boxes - " + colorLabel + " Player");
                    break;
                }
            }

            /* Read messages from server; change message label accordingly. */
            readMessages();
        } catch (Exception error) {
            error.printStackTrace();
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }

    public static void main(String[] args) throws Exception{
        /* Determine whether the client should connect to a local server or
           to a server IP address provided by the player. */
        while (true) {
            String serverAddress = (args.length == 0) ? "localhost" : args[0];
            Client player = new Client(serverAddress);
            player.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            player.frame.setSize(GRID_WIDTH*50+10, GRID_WIDTH*50+50);
            player.frame.setVisible(true);
            player.frame.setResizable(false);
            player.play();

            if (!player.playAgain()) {
                player.endGame();
                break;
            }
        }
    }
}