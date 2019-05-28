import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.Socket;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class Client {

    private int gridWidth = 9; //must be odd number
    private JFrame frame = new JFrame("Project");
    private JLabel messageLabel = new JLabel("");

    private Square[] board = new Square[gridWidth*gridWidth];
    private Square currentSquare;

    private static int PORT = 8901;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private char mark;

    public Client(String serverAddress) throws Exception {

        socket = new Socket(serverAddress, PORT);
        in = new BufferedReader(new InputStreamReader(
                socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        messageLabel.setBackground(Color.lightGray);
        frame.getContentPane().add(messageLabel, "South");

        JPanel boardPanel = new JPanel();
        boardPanel.setBackground(Color.black);
        boardPanel.setLayout(new GridLayout(gridWidth, gridWidth, 1, 1));
        for (int i = 0; i < board.length; i++) {
            final int j = i;
            board[i] = new Square(i);
            if (i % 2 != 0) {
                board[i].addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        currentSquare = board[j];
                        out.println("MOVE " + j);}});
            }
            boardPanel.add(board[i]);
        }
        frame.getContentPane().add(boardPanel, "Center");
    }

    public void play() throws Exception {
        String response;
        try {
            response = in.readLine();
            if (response.startsWith("WELCOME")) {
                mark = response.charAt(8);
                String p = (mark == 'B') ? "Blue" : "Red";
                frame.setTitle("Dots and Boxes - " + p + " Player");
            }
            while (true) {
                response = in.readLine();
                if (response.startsWith("VALID_MOVE")) {
                    if (response.substring(11).equals("Y")) { messageLabel.setText("Valid move, go again"); }
                    else { messageLabel.setText("Opponent's turn, please wait"); }
                    currentSquare.setBackground((mark == 'B') ? Color.blue : Color.red);
                } else if (response.startsWith("OPPONENT_MOVED")) {
                    int loc = Integer.parseInt(response.substring(16));
                    board[loc].setBackground((mark == 'B') ? Color.red : Color.blue);
                    if (response.substring(15,16).equals("Y")) { messageLabel.setText("Opponent goes again"); }
                    else { messageLabel.setText("Opponent moved, your turn"); }
                } else if ((response.startsWith("BLUE") && mark == 'B') || (response.startsWith("RED") && mark == 'R')) {
                    messageLabel.setText("YOU WIN!");
                    break;
                } else if ((response.startsWith("BLUE") && mark == 'R') || (response.startsWith("RED") && mark == 'B')) {
                    messageLabel.setText("YOU LOSE");
                    break;
                } else if (response.startsWith("TIE")) {
                    messageLabel.setText("YOU TIED");
                    break;
                } else if (response.startsWith("MESSAGE")) {
                    messageLabel.setText(response.substring(8));
                }
            }
            out.println("QUIT");
        }
        finally {
            socket.close();
        }
    }

    private boolean wantsToPlayAgain() {
        int response = JOptionPane.showConfirmDialog(frame,
                "Want to play again?",
                messageLabel.getText(),
                JOptionPane.YES_NO_OPTION);
        frame.dispose();
        return response == JOptionPane.YES_OPTION;
    }

    class Square extends JPanel {
        JLabel dotLabel = new JLabel(new ImageIcon("graphics/dot.png"));
        JLabel vlineLabel = new JLabel(new ImageIcon("graphics/vline.png"));
        JLabel hlineLabel = new JLabel(new ImageIcon("graphics/hline.png"));

        public Square(int i) {
            int row = (i / gridWidth) + 1;
            if((row % 2 != 0) && (i % 2 == 0)) {
                this.add(dotLabel);
            }
            else if((row % 2 != 0) && (i % 2 != 0)) {
                this.add(hlineLabel);
            }
            else if((row % 2 == 0) && (i % 2 != 0)){
                this.add(vlineLabel);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        while (true) {
            String serverAddress = (args.length == 0) ? "localhost" : args[0];
            Client client = new Client(serverAddress);
            client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            client.frame.setSize(400, 400);
            client.frame.setVisible(true);
            client.frame.setResizable(false);
            client.play();
            if (!client.wantsToPlayAgain()) {
                break;
            }
        }
    }
}
