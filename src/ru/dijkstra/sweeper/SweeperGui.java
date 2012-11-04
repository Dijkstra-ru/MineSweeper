package ru.dijkstra.sweeper;

import java.io.IOException;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureLoader;
import org.newdawn.slick.util.ResourceLoader;
import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL11.*;

/**
 * Реализует простой графический интерфейс
 */
public class SweeperGui extends Thread {
    private static Texture tileTextures;
    private static SweeperGame sweeperGame;
    private final static int TILE_SIZE = 24;
    final int DISPLAY_WIDTH;
    final int DISPLAY_HEIGHT;

    SweeperGui(SweeperGame sweeperGame) {
        SweeperGui.sweeperGame = sweeperGame;
        DISPLAY_WIDTH = sweeperGame.getSizeX() * TILE_SIZE;
        DISPLAY_HEIGHT = sweeperGame.getSizeY() * TILE_SIZE;
    }

    /**
     * Операции по работе с графикой, которые невозможно вынести в конструктор,
     * но необходимо выполнить при инициплизации
     */
    private void initGraphics() {
        try {
            Display.setDisplayMode(new DisplayMode(DISPLAY_WIDTH, DISPLAY_HEIGHT));
            Display.create();
            Display.setVSyncEnabled(true);
            loadResources();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glViewport(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, DISPLAY_WIDTH, DISPLAY_HEIGHT, 0, 1, -1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } catch (LWJGLException ex) { }
    }
    /** Подгружает текстуру */
    private static void loadResources() {
        try {
            tileTextures = TextureLoader.getTexture("PNG",
                    ResourceLoader.getResourceAsStream("res/tiles.png"));
        } catch (IOException e) { }
    }
    /** Изображает один тайл изображения*/
    private static void drawPiece(Piece piece, int x, int y) {
        // TODO: не проводить расчёты при каждой перерисовке
        final float TEXTURE_TILE_SIZE_HEIGHT = tileTextures.getHeight();
        final float TEXTURE_TILE_SIZE_WIDTH = tileTextures.getWidth() / Piece.values().length;

        // Координаты для OpenGL
        final float tx1 = TEXTURE_TILE_SIZE_WIDTH * piece.TILE_MAP_INDEX;
        final float tx2 = TEXTURE_TILE_SIZE_WIDTH * (piece.TILE_MAP_INDEX +1);
        final float ty1 = 0;
        final float ty2 = TEXTURE_TILE_SIZE_HEIGHT;

        final float vx1 = 0;
        final float vx2 = TILE_SIZE;
        final float vy1 = 0;
        final float vy2 = TILE_SIZE;

        // Шаманство
        tileTextures.bind();
        glPushMatrix();
        glTranslatef(x * TILE_SIZE, y * TILE_SIZE, 0);
        glBegin(GL_QUADS);
        {
            glTexCoord2f(tx1, ty1);
            glVertex2f(vx1, vy1);
            glTexCoord2f(tx2, ty1);
            glVertex2f(vx2, vy1);
            glTexCoord2f(tx2, ty2);
            glVertex2f(vx2, vy2);
            glTexCoord2f(tx1, ty2);
            glVertex2f(vx1, vy2);
        }
        glEnd();
        glPopMatrix();
    }

    /** Рисует поле */
    private static void drawField(Field field) {
        int maxX = field.getWidth();
        int maxY = field.getHeight();
        for (int k = 0; k < maxY; k++)
            for (int i = 0; i < maxX; i++)
                drawPiece(field.getPiece(i, k), i, k);
    }

    @Override
    public void run() {
        initGraphics();
        try {
            // Основной цикл
            while (!Display.isCloseRequested()) {
                drawField(sweeperGame.getVisibleField());
                Display.update();
                drawField(sweeperGame.getVisibleField());

                boolean isMouseDown0 = false;
                boolean isMouseDown1 = false;

                do {
                    Display.setTitle(Mouse.getX() / TILE_SIZE + "," +
                            (DISPLAY_HEIGHT - Mouse.getY()) / TILE_SIZE);
                    if (Keyboard.isKeyDown(Keyboard.KEY_1)){
                        sweeperGame.findObviousMines();
                        Thread.sleep(1000);
                    }

                    if (Keyboard.isKeyDown(Keyboard.KEY_2)) {
                        sweeperGame.findObviousPolls();
                        Thread.sleep(1000);
                    }
                    if (Keyboard.isKeyDown(Keyboard.KEY_3)) {
                        sweeperGame.deductAssumingMine(Mouse.getX() / TILE_SIZE,
                                (DISPLAY_HEIGHT - Mouse.getY()) / TILE_SIZE);
                        Thread.sleep(1000);
                    }

                     if (Keyboard.isKeyDown(Keyboard.KEY_4)) {
                        sweeperGame.deductAssumingNoMine(Mouse.getX() / TILE_SIZE,
                                (DISPLAY_HEIGHT - Mouse.getY()) / TILE_SIZE);
                        Thread.sleep(1000);
                    }

                    if (!Mouse.isButtonDown(0) && isMouseDown0) {
                        int x = Mouse.getX();
                        int y = DISPLAY_HEIGHT - Mouse.getY();
                        sweeperGame.guess(x / TILE_SIZE, y / TILE_SIZE);
                        if (!Mouse.isButtonDown(1)) {
                            sweeperGame.pollNeighbors(x / TILE_SIZE, y / TILE_SIZE);
                        }
                    }
                    if (!Mouse.isButtonDown(1) && isMouseDown1) {
                        int x = Mouse.getX();
                        int y = DISPLAY_HEIGHT - Mouse.getY();
                        sweeperGame.placeFlag(x / TILE_SIZE, y / TILE_SIZE);
                        if (!Mouse.isButtonDown(0)) {
                            sweeperGame.pollNeighbors(x / TILE_SIZE, y / TILE_SIZE);
                        }
                    }
                    isMouseDown0 = Mouse.isButtonDown(0);
                    isMouseDown1 = Mouse.isButtonDown(1);
                    Display.update();
                    drawField(sweeperGame.getVisibleField());
                    Display.sync(30);
                    if (Display.isCloseRequested())
                        System.exit(0);
                } while (sweeperGame.getGameState() != GameState.GS_WIN &&
                         sweeperGame.getGameState() != GameState.GS_GAME_OVER);
                drawField(sweeperGame.getVisibleField());
                Display.update();
                drawField(sweeperGame.getVisibleField());
                do {
                    Display.setTitle(Mouse.getX() / TILE_SIZE + "," +
                            (DISPLAY_HEIGHT - Mouse.getY()) / TILE_SIZE);
                    Display.update();
                    Display.sync(30);
                    if (Display.isCloseRequested())
                        System.exit(0);
                } while (!Mouse.isButtonDown(0));
                sweeperGame = new SweeperGame(SweeperServer.FIELD_WIDTH ,
                        SweeperServer.FIELD_HEIGHT, SweeperServer.MINES);
                //Грязно и плохо, но смена подхода требует смены архитектуры
            }
            Display.destroy();
        } catch (Exception e) {
           e.printStackTrace();
        }
    }
}
