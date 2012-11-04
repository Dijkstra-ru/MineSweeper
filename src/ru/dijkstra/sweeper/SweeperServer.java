package ru.dijkstra.sweeper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Простой сокет-сервер для вывода отчётов.
 */
public class SweeperServer extends Thread {
    final static int SERVER_PORT = 9604;
    private static boolean buisy = false;
    private static ServerSocket ssock;
    public static Thread clientThread;
    public final static int FIELD_WIDTH = 30;
    public final static int FIELD_HEIGHT = 20;
    public final static int MINES = 120;

    public void setBuisy() { buisy = true; }
    public void setFree() { buisy = false; }

    @Override
    public void run() {
        try {
            ssock = new ServerSocket(SERVER_PORT);
            while (true) {
                SweeperGame sweeperGame = new SweeperGame(FIELD_WIDTH, FIELD_HEIGHT, MINES);
                // ВАЖНО: это не единственный вызов конструктора игры.
                // Второй вызов находится в SweeperGui
                // Вынужденный архитектурный костыль
                Socket sock = ssock.accept();
                if (buisy) {
                    // Возможно принять только одно соединение
                    ssock.close();
                    continue;
                }
                buisy = true;
                clientThread = new Thread(new RemoteReadThread(sock, sweeperGame),
                                                    "Client" + sock.getLocalAddress());
                clientThread.setDaemon(true);
                clientThread.setPriority(Thread.NORM_PRIORITY);
                clientThread.start();
            }

        } catch (Exception ex) {
            System.out.println(ex);
        } finally {
            if (ssock != null) {
                try {
                    ssock.close();
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        }

    }
}

class RemoteReadThread extends Thread {
    private Socket sock = null;
    private boolean isCloseRecieved = false;
    private OutputStream outputStream;
    private static SweeperServer parent;
    private static SweeperGame sweeper;
    private PipedReader pr;

    public RemoteReadThread(Socket socket, SweeperGame sweeperGame) {
        sock = socket;
        sweeper = sweeperGame;
        try {
            pr = new PipedReader(sweeper.getWriter());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            outputStream = sock.getOutputStream();
            outputStream.write("200 Read thread on line\r\n".getBytes());
            Thread GUIThread = new Thread(new SweeperGui(sweeper), "GUI Thread");
            GUIThread.setPriority(Thread.NORM_PRIORITY);
            GUIThread.start();
            do {
                if (pr.ready())
                   outputStream.write((char) pr.read());

            } while (!isCloseRecieved);
            sock.close();
        } catch (Exception error) { error.printStackTrace(); parent.setFree(); }
    }
}