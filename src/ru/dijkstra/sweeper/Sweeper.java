package ru.dijkstra.sweeper;

import java.io.IOException;

public class Sweeper {
public static void main(String[] args) {
            //Запускаем сервер удалённого управления
            Thread serverThread = new Thread(new SweeperServer(), "Server Thread");
            serverThread.setPriority(Thread.NORM_PRIORITY);
            serverThread.start();
            //Запускаем терминал
            try {
                Runtime.getRuntime().exec("c:\\putty.exe -load \"Sweeper Control\"");
            } catch (IOException ex) { }
}
}