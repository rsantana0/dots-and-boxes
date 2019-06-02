import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;

public class Client {
    /* Grid width can be an odd number greater than 3.
       Both client and server must use the same value. */
    private static final int GRID_WIDTH = 9;
    private JFrame frame = new JFrame("Dots&Boxes");
    private JLabel messageLabel = new JLabel("");
    private char playerColor;

    private final int PORT = 8901;
    private Socket socket;
    private BufferedReader inBuffer;
    private PrintWriter outBuffer;

    private Square board[] = new Square[GRID_WIDTH * GRID_WIDTH];
    private Square currentSquare;
    
    public Client(String serverAddress) throws Exception {
        socket = new Socket(serverAddress, PORT);
        inBuffer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outBuffer = new PrintWriter(socket.getOutputStream(), true);

        messageLabel.setBackground(Color.lightGray);
        frame.getContentPane().add(messageLabel, "South");

        JPanel boardPanel = new JPanel();
        boardPanel.setBackground(Color.black);
        boardPanel.setLayout(new GridLayout(GRID_WIDTH, GRID_WIDTH));

        for (int i = 0; i < board.length; i++) {
            board[i] = new Square(i);
            boardPanel.add(board[i]);
        }

        frame.getContentPane().add(boardPanel, "Center");
    }

    private boolean playAgain() {
        int response = JOptionPane.showConfirmDialog(frame,
                       "Want to play again?",
                       messageLabel.getText(),
                       JOptionPane.YES_NO_OPTION);
        frame.dispose();
        return (response == JOptionPane.YES_OPTION);
    }

    private class Square extends JPanel {
        private int position;

        public Square(int pos) {
            this.position = pos;

            MouseAdapter mouseEvent = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    currentSquare = board[pos];
                    outBuffer.println("MOVE " + pos);
                }
            };

            if (pos % 2 != 0) {
                this.addMouseListener(mouseEvent);
            }
        }

        @Override
        public void paintComponent(Graphics g) {
            int row = (this.position / GRID_WIDTH) + 1;
            Graphics2D gDraw = (Graphics2D) g;

            gDraw.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);

            if ((row % 2 != 0) && (this.position % 2 == 0)) {
                gDraw.setColor(Color.LIGHT_GRAY);
                gDraw.fillOval(10, 10, 30, 30);
                gDraw.fillRect(5, 17, 40, 16);
                gDraw.fillRect(17, 5, 16, 40);
            }
            else if ((row % 2 != 0) && (this.position % 2 != 0)) {
                //Rectangle hRec = new Rectangle(0, 17, 50, 16);
                //gDraw.fill(hRec); //change graphics class
                gDraw.fillRect(0, 17, 50, 16);
            }
            else if ((row % 2 == 0) && (this.position % 2 != 0)) {
                //Rectangle vRec = new Rectangle(17, 0, 16, 50);
                //gDraw.fill(vRec);
                gDraw.fillRect(17, 0, 16, 50);
            }
        }
    }

    public void play() throws Exception {
        String response;
        try {
            response = inBuffer.readLine();
            if (response.startsWith("WELCOME")) {
                playerColor = response.charAt(8);
                String colorLabel = (playerColor == 'B') ? "Blue" : "Red";
                frame.setTitle("Dots and Boxes - " + colorLabel + " Player");
            }

            while (true) {
                response = inBuffer.readLine();
                if (response.startsWith("VALID_MOVE")) {
                    if (response.substring(11).equals("Y")) { messageLabel.setText("Valid move, go again"); }
                    else { messageLabel.setText("Opponent's turn, please wait"); }
                    currentSquare.setForeground((playerColor == 'B') ? Color.blue : Color.red);
                } else if (response.startsWith("OPPONENT_MOVED")) {
                    int opponentMove = Integer.parseInt(response.substring(16));
                    board[opponentMove].setForeground((playerColor == 'B') ? Color.red : Color.blue);
                    if (response.substring(15,16).equals("Y")) { messageLabel.setText("Opponent goes again"); }
                    else { messageLabel.setText("Opponent moved, your turn"); }
                } else if ((response.startsWith("BLUE") && playerColor == 'B') || (response.startsWith("RED") && playerColor == 'R')) {
                    messageLabel.setText("YOU WIN!");
                    break;
                } else if ((response.startsWith("BLUE") && playerColor == 'R') || (response.startsWith("RED") && playerColor == 'B')) {
                    messageLabel.setText("YOU LOSE");
                    break;
                } else if (response.startsWith("TIE")) {
                    messageLabel.setText("YOU TIED");
                    break;
                } else if (response.startsWith("MESSAGE")) {
                    messageLabel.setText(response.substring(8));
                }
            }
            outBuffer.println("QUIT");
        }
        finally {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception{
        String serverAddress = (args.length == 0) ? "localhost" : args[0];
        Client player = new Client(serverAddress);
        player.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        player.frame.setSize(GRID_WIDTH*50+10, GRID_WIDTH*50+50);
        player.frame.setVisible(true);
        player.frame.setResizable(false);
    
        do {
            player.play();
        } while (player.playAgain());
    }
}