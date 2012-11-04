package ru.dijkstra.sweeper;
/**
 * Файл классов игровой логики
 * Функции дедукции
 */
import java.io.IOException;
import java.io.PipedWriter;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Random;

/** Элемент игрового поля.
 * Да, вообще-то номер текстуры в атласе тут не к месту.
 * Но я не смог придумать лучшего выхода.
 */
enum Piece {
    P_UNKNOWN(0, '~'),
    P_FLAG(1, 'F'),
    P_MINE_FOUND(2, '+'),
    P_MINE_MISTAKE(3, '-'),
    P_MINE(4, '*'),
    P_0(5, ' ', 0),
    P_1(6, '1', 1),
    P_2(7, '2', 2),
    P_3(8, '3', 3),
    P_4(9, '4', 4),
    P_5(10, '5', 5),
    P_6(11, '6', 6),
    P_7(12, '7', 7),
    P_8(13, '8', 8);
    final int TILE_MAP_INDEX;       // Номер текстуры
    final char CHAR_REPRESENTATION; // Символ тайла для toString
    int DIGIT_REPRESENTATION;       // Число мин в окресности, для упрощения
    Piece(int i, char c) {          // математики
        TILE_MAP_INDEX = i;
        CHAR_REPRESENTATION = c;
        DIGIT_REPRESENTATION = -1;
    }

    Piece(int i, char c, int d) {
        this(i, c);
        DIGIT_REPRESENTATION = d;
    }
}

/** Текущее состояние игры */
enum GameState { GS_GAME_OVER, GS_WIN, GS_ONGOING, GS_AWAITING }

/** Двойка координат */
class Point {
    private int x, y;
    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public int getX() { return x; }
    public int getY() { return y; }
}

/** Соседние клетки */
class Hood implements Iterable<Point> {
    public static Hood getInstance() {
        if (instance == null) {
            instance = new Hood();
        }
        return instance;
    }
    private Hood() { /*Запрещено создание экземпляров*/ }
    private static Hood instance;
    private static Point[] storage = {new Point(-1, -1), new Point(+0, -1), new Point(+1, -1),
        new Point(-1, +0), new Point(+1, +0),
        new Point(-1, +1), new Point(+0, +1), new Point(+1, +1),};
    @Override
    public Iterator<Point> iterator() {
        return new Iterator<Point>() {
            int cursor = 0;
            @Override
            public boolean hasNext() { return cursor < storage.length; }
            @Override
            public Point next() { return storage[cursor++]; }
            @Override
            public void remove() { throw new UnsupportedOperationException(); }
        };
    }
}

/** Игровое поле */
class Field {
    private Piece storage[][];
    private Random random = new Random();
    private int xSize, ySize;

    /** Конструктор пустых полей */
    Field(int xSize, int ySize) {
        this.xSize = xSize;
        this.ySize = ySize;
        storage = new Piece[xSize][ySize];
        for (int k = 0; k < ySize; k++)
            for (int i = 0; i < xSize; i++)
                storage[i][k] = Piece.P_UNKNOWN;
    }

    /**
     * Конструктор полей с минами
     * @param playerClickX Абсцисса точки, где нельзя располагать мину
     * @param playerClickY Ордината точки, где нельзя располагать мину
     * Мины будут расположены в случайном порядке так, чтобы игрок не
     * проиграл в первый ход
     */
    Field(int xSize, int ySize, int mines, int playerClickX, int playerClickY) {
        storage = new Piece[xSize][ySize];
        assert mines < xSize * ySize : "Нет места для размещения всех мин";
        for (int i = 0; i < mines; i++) {
            int mineXPos, mineYPos;
            do {
                mineXPos = random.nextInt(xSize);
                mineYPos = random.nextInt(ySize);
            } while (storage[mineXPos][mineYPos] == Piece.P_MINE
                    && mineXPos == playerClickX && mineYPos == playerClickY);
            storage[mineXPos][mineYPos] = Piece.P_MINE;
        }
        for (int k = 0; k < ySize; k++) {
            for (int i = 0; i < xSize; i++) {
                if (storage[i][k] == Piece.P_MINE) {
                    continue;
                }
                int minesInHood = 0;
                for (Point p : Hood.getInstance()) {
                    int x = i + p.getX();
                    int y = k + p.getY();
                    if (x >= xSize || y >= ySize || x < 0 || y < 0) {
                        continue;
                    }
                    if (storage[x][y] == Piece.P_MINE) {
                        minesInHood++;
                    }
                }
                for (Piece p : Piece.values()) {
                    if (p.DIGIT_REPRESENTATION == minesInHood) {
                        storage[i][k] = p;
                    }
                }
            }
        }
    }

    /**
     * Конструктор сходных полей
     * @param field поле для копирования
     */
    Field(Field field) {
        this.xSize = field.getWidth();
        this.ySize = field.getHeight();
        this.storage = new Piece[xSize][ySize];
        for (int j = 0; j < ySize; j++)
            for (int i = 0; i < xSize; i++)
                this.storage[i][j] = field.getPiece(i, j);
    }

    /**
     * @return Отображение поля в текст
     * Согласно параметрам в Piece.CHAR_REPRESENTATION
     * В коце строк \r\n
     */
    @Override
    public String toString() {
        String result = "";
        for (int k = 0; k < ySize; k++) {
            for (int i = 0; i < xSize; i++) {
                result += storage[i][k].CHAR_REPRESENTATION;
            }
            result += "\r\n";
        }
        return result;
    }
    public Piece[][] getStorage()                     { return storage;       }
    public Piece     getPiece(int x, int y)           { return storage[x][y]; }
    public void      setPiece(int x, int y, Piece p)  { storage[x][y] = p;    }
    public int       getWidth()                       { return xSize;         }
    public int       getHeight()                      { return ySize;         }
}

/**
 * Класс, описывающий игру
 */
class SweeperGame {
    private Field visibleField; // Поля
    private Field hiddenField;
    private int sizeX, sizeY;   // Размеры
    private int totalMines;     // Статистика
    private int totalFlags = 0;
    private int unopenedPieces;
    private GameState gameState;
    private static PipedWriter writer = new PipedWriter();

    /** Начать новую игру */
    SweeperGame(int xSize, int ySize, int mines) {
        visibleField = new Field(xSize, ySize);
        sizeX = xSize;
        sizeY = ySize;
        unopenedPieces = sizeX * sizeY;
        totalMines = mines;
        gameState = GameState.GS_AWAITING;

    }

    public PipedWriter   getWriter()        { return writer;       }
    public Field         getVisibleField()  { return visibleField; }
    public GameState     getGameState()     { return gameState;    }
    public int           getSizeX()         { return sizeX;        }
    public int           getSizeY()         { return sizeY;        }

    // Кривовата функция
    private static long currTime;
    private static SimpleDateFormat sdt = new SimpleDateFormat("H:mm:ss ");
    private void log(String s) {
        try {
            currTime = System.currentTimeMillis();
            // Ожидается, что сервер уже синхронизировался с этим пайпом
            // Иначе вылетит всё это дело к чёртовой матери
            writer.write(sdt.format(currTime) + s + "\r\n");
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    /** Аналог левого клика по полю */
    public void guess(int x, int y) {
        if (x < 0 || y < 0 || x >= sizeX || y >= sizeY
                || visibleField.getPiece(x, y) != Piece.P_UNKNOWN)
            return;
        switch (gameState) {
            case GS_AWAITING:
                hiddenField = new Field(sizeX, sizeY, totalMines, x, y);
                gameState = GameState.GS_ONGOING;
                log("700 New Game Started");
                guess(x, y);
                break;
            case GS_GAME_OVER:
            case GS_WIN:
                return;
            case GS_ONGOING:
                switch (hiddenField.getPiece(x, y)) {
                    case P_MINE:
                        gameState = GameState.GS_GAME_OVER;
                        revealField();
                        log("900 Game over");
                        break;
                    default:
                        unopenedPieces--;
                        Piece piece = hiddenField.getPiece(x, y);
                        visibleField.setPiece(x, y, piece);
                        if (isVictory()) {
                            gameState = GameState.GS_WIN;
                            revealField();
                            log("800 You win!");
                        }
                        if (piece == Piece.P_0) {
                            for (Point p : Hood.getInstance())
                                guess(x + p.getX(), y + p.getY());
                        }
                }
        }
    }

    /** Аналог правого клика по полю */
    public void placeFlag(int x, int y) {
        if (gameState != GameState.GS_ONGOING)
            return;
        if (x < 0 || y < 0 || x >= sizeX || y >= sizeY)
            return;
        Piece p = visibleField.getPiece(x, y);
        if (p == Piece.P_FLAG) {
            visibleField.setPiece(x, y, Piece.P_UNKNOWN);
            totalFlags--;
            log("703 Flag aborted");
        }
        if (p == Piece.P_UNKNOWN) {
            visibleField.setPiece(x, y, Piece.P_FLAG);
            totalFlags++;
            log("703 Flag placed");
        }
    }

    /** Аналог двойного клика */
    public void pollNeighbors(int x, int y) {
        if (x < 0 || y < 0 || x >= sizeX || y >= sizeY)
            return;
        if (gameState != GameState.GS_ONGOING)
            return;
        Piece piece = visibleField.getPiece(x, y);
        if (piece.DIGIT_REPRESENTATION == -1) // Не опрашивать поля без цифр
            return;                           // Кому бы это пришло в голову?
        int flagsInHood = 0;
        for (Point p : Hood.getInstance()) {
            int flagX = x + p.getX();
            int flagY = y + p.getY();
            if (flagX >= sizeX || flagY >= sizeY || flagX < 0 || flagY < 0)
                continue;
            if (visibleField.getPiece(flagX, flagY) == Piece.P_FLAG)
                flagsInHood++;
        }
        if (piece.DIGIT_REPRESENTATION == flagsInHood)
            for (Point p : Hood.getInstance())
                guess(x + p.getX(), y + p.getY());
    }

    // Сам стесняюсь своих процедурных наклонностей
    /** Проверяет условие победы */
    private boolean isVictory() {
        if (unopenedPieces == totalFlags)
            return true;
        if (unopenedPieces == totalMines)
            return true;
        return false;
    }

    /** Показывает игроку игровое поле, демонстрирует ошибки */
    // TODO: Показывать игроку мину, на которую он имел неудачу наткнуться
    private void revealField() {
        log("705 Revealing Field...");
        for (int j = 0; j < sizeY; j++)
            for (int i = 0; i < sizeX; i++) {
                switch (visibleField.getPiece(i, j)) {
                    case P_FLAG:
                        if (hiddenField.getPiece(i, j) == Piece.P_MINE)
                            visibleField.setPiece(i, j, Piece.P_MINE_FOUND);
                         else
                            visibleField.setPiece(i, j, Piece.P_MINE_MISTAKE);
                        break;
                    case P_UNKNOWN:
                        visibleField.setPiece(i, j, hiddenField.getPiece(i, j));
                        break;
                    default:
                        continue;
                }
            }
    }

    // Блок процедур, посвящённых схеме дедукции

    /**
     * Ищет клетки-числа с количеством близких неоткрытых клеток, равным числу
     */
    public void findObviousMines() {
        log("[AI]: Searching for obvious mines");
        for (int j = 0; j < sizeY; j++)
            for (int i = 0; i < sizeX; i++)
                if (visibleField.getPiece(i, j).DIGIT_REPRESENTATION > 0) {
                    int invisibles = 0;
                    for (Point p : Hood.getInstance()) {
                        int x = i + p.getX();
                        int y = j + p.getY();
                        if (x >= sizeX || y >= sizeY || x < 0 || y < 0)
                            continue;
                        if (visibleField.getPiece(x, y) == Piece.P_UNKNOWN)
                            invisibles++;
                        if (visibleField.getPiece(x, y) == Piece.P_FLAG)
                            invisibles++;
                    }
                    if (invisibles == visibleField.getPiece(i, j).DIGIT_REPRESENTATION) {
                        for (Point p : Hood.getInstance()) {
                            int x = i + p.getX();
                            int y = j + p.getY();
                            if (x >= sizeX || y >= sizeY || x < 0 || y < 0)
                            continue;
                            if (visibleField.getPiece(x,y) == Piece.P_UNKNOWN)
                                placeFlag(i + p.getX(), j + p.getY());
                        }
                    }
                }
    }

    /*
     * Ищет клетки-числа с количеством близких флагов, равным числу
     */
    public void findObviousPolls() {
        log("[AI]: Searching for obvious polls");
        for (int j = 0; j < sizeY; j++) {
            for (int i = 0; i < sizeX; i++) {
                if (visibleField.getPiece(i, j).DIGIT_REPRESENTATION > 0) {
                    int flags = 0;
                    for (Point p : Hood.getInstance()) {
                        int x = i + p.getX();
                        int y = j + p.getY();
                        if (x >= sizeX || y >= sizeY || x < 0 || y < 0)
                            continue;
                        if (visibleField.getPiece(x, y) == Piece.P_FLAG)
                            flags++;
                    }
                    if (flags == visibleField.getPiece(i, j).DIGIT_REPRESENTATION)
                        pollNeighbors(i, j);
                }
            }
        }
    }

    /**
     * Строит цепочу высказываний, предполагая наличие мины в данной точке.
     * Если находит противоречие, открывает клетку
     * @param x абсцисса точки
     * @param y ордината точки
     */
    public void deductAssumingMine(int x, int y) {
        Field tempField = new Field(visibleField);
        log("[AI] ***");
        log("[AI] Assuming mine in " + x + "," + y + ";");
        tempField.setPiece(x, y, Piece.P_FLAG);
        for (Point p : Hood.getInstance()) {
            int ax = x + p.getX();
            int ay = y + p.getY();
            if (ax >= sizeX || ay >= sizeY || ax < 0 || ay < 0)
                continue;
            if (!testPiece(ax, ay, tempField)) {
                log("[AI] So, there is no mine in initial point " + x + "," + y);
                log("[AI] So, guessing " + x + "," + y);
                guess(x, y);
                return;
            }
        }
        log("[AI] Can not make a conclusion");
    }

    /**
     * Строит цепочу высказываний, предполагая отсутствие мины в данной точке.
     * Если находит противоречие, устанавливает флаг
     * @param x абсцисса точки
     * @param y ордината точки
     */
    public void deductAssumingNoMine(int x, int y) {
        Field tempField = new Field(visibleField);
        log("[AI] ***");
        log("[AI] Assuming no mine in " + x + "," + y + ";");
        tempField.setPiece(x, y, Piece.P_0);
        for (Point p : Hood.getInstance()) {
            int ax = x + p.getX();
            int ay = y + p.getY();
            if (ax >= sizeX || ay >= sizeY || ax < 0 || ay < 0)
                continue;
            if (!testPiece(ax, ay, tempField)) {
                log("[AI] So, there is mine in initial point " + x + "," + y);
                log("[AI] So, placing a flag in " + x + "," + y);
                placeFlag(x, y);
                return;
            }
        }
        log("[AI] Can not make a conclusion");
    }

    /**
     * Рекурсивно проверяет поле в поисках противоречий
     * @param x абсцисса точки, с которой необходимо начать поиск
     * @param y ордината точки, с которой необходимо начать поиск
     * @param field поле для поиска. Внимание, поле для поиска будет изменено!
     * @return true в случае отсутствия противоречий, в глубине дерева рекурсии
     * @return false в случае их наличия
     */
    private boolean testPiece(int x, int y, Field field) {
        if ((field.getPiece(x, y) == Piece.P_0)
                || (field.getPiece(x, y) == Piece.P_UNKNOWN)
                || (field.getPiece(x, y) == Piece.P_FLAG))
            return true;
        if (field.getPiece(x, y).DIGIT_REPRESENTATION > 0) {
            int flags = 0;
            int unknowns = 0;
            for (Point p : Hood.getInstance()) {
                int ax = x + p.getX();
                int ay = y + p.getY();
                if (ax >= sizeX || ay >= sizeY || ax < 0 || ay < 0)
                    continue;
                if (field.getPiece(ax, ay) == Piece.P_FLAG)
                    flags++;
                if (field.getPiece(ax, ay) == Piece.P_UNKNOWN)
                    unknowns++;
            }
            if (flags == field.getPiece(x, y).DIGIT_REPRESENTATION) {
                for (Point ap : Hood.getInstance()) {
                    int bx = x + ap.getX();
                    int by = y + ap.getY();
                    if (bx >= sizeX || by >= sizeY || bx < 0 || by < 0)
                        continue;
                    if (field.getPiece(bx, by) == Piece.P_UNKNOWN) {
                        log("[AI] So, there is no mine in " + bx + "," + by);
                        field.setPiece(bx, by, Piece.P_0);
                    }
                }
            }
            if ((unknowns == field.getPiece(x, y).DIGIT_REPRESENTATION) && (flags == 0)) {
                for (Point ap : Hood.getInstance()) {
                    int bx = x + ap.getX();
                    int by = y + ap.getY();
                    if (bx >= sizeX || by >= sizeY || bx < 0 || by < 0)
                        continue;
                    if (field.getPiece(bx, by) == Piece.P_UNKNOWN) {
                        log("[AI] So, there is mine in " + bx + "," + by);
                        field.setPiece(bx, by, Piece.P_FLAG);
                    }
                }
            }
            if (flags > field.getPiece(x, y).DIGIT_REPRESENTATION) {
                log("[AI] So, there is contradiction: too many flags");
                return false;
            }
            if ((unknowns + flags) < field.getPiece(x, y).DIGIT_REPRESENTATION) {
                log("[AI] So, there is contradiction: too small amount of valid pieces");
                return false;
            }
            field.setPiece(x, y, Piece.P_0);
        }
        for (Point p : Hood.getInstance()) {
            int ax = x + p.getX();
            int ay = y + p.getY();
            if (ax >= sizeX || ay >= sizeY || ax < 0 || ay < 0)
                continue;
            if (!testPiece(ax, ay, field))
                return false;
        }
        return true;
    }
}