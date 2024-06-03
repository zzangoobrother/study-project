import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {-1, 1, 0, 0};
    static int[] dy = {0, 0, -1, 1};
    static String[][] board;
    static int[][][] mirrors;
    static int h, w;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        w = Integer.parseInt(st.nextToken());
        h = Integer.parseInt(st.nextToken());

        board = new String[h][w];
        mirrors = new int[h][w][4];
        for (int i = 0; i < h; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            String s = st.nextToken();
            String[] split = s.split("");
            for (int j = 0; j < w; j++) {
                Arrays.fill(mirrors[i][j], Integer.MAX_VALUE);
                board[i][j] = split[j];
            }
        }

        System.out.println(bfs(findStart()));
    }

    static Point findStart() {
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                if (board[i][j].equals("C")) {
                    board[i][j] = "S";
                    return new Point(j, i, -1, 0);
                }
            }
        }

        return null;
    }

    public static int bfs(Point target) {
        PriorityQueue<Point> queue = new PriorityQueue<>();
        queue.offer(target);

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            if (board[point.y][point.x].equals("C")) {
                return point.mirror;
            }

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                   continue;
                }

                if (board[ny][nx].equals("*")) {
                    continue;
                }

                if (i != point.dir && point.dir != -1) {
                    if (mirrors[ny][nx][i] > point.mirror + 1) {
                        mirrors[ny][nx][i] = point.mirror + 1;
                        queue.offer(new Point(nx, ny, i, point.mirror + 1));
                    }
                } else {
                    if (mirrors[ny][nx][i] > point.mirror) {
                        mirrors[ny][nx][i] = point.mirror;
                        queue.offer(new Point(nx, ny, i, point.mirror));
                    }
                }
            }
        }

        return -1;
    }

    static class Point implements Comparable<Point> {
        int x;
        int y;
        int dir;
        int mirror;

        public Point(int x, int y, int dir, int mirror) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.mirror = mirror;
        }

        @Override
        public int compareTo(Point o) {
            return this.mirror - o.mirror;
        }
    }
}