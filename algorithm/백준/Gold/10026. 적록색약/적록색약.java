import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {
    static int dx[] = {1, 0, -1, 0};
    static int dy[] = {0, 1, 0, -1};
    static String[][] arr1;
    static String[][] arr2;
    static boolean[][] visit;
    static int n, num1 = 0, num2 = 0;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        arr1 = new String[n][n];
        arr2 = new String[n][n];
        visit = new boolean[n][n];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            String[] split = st.nextToken().split("");
            for (int j = 0; j < n; j++) {
                arr1[i][j] = split[j];
                arr2[i][j] = split[j].equals("B") ? "B" : "R";
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!visit[i][j] && arr1[i][j].equals("R")) {
                    bfs1(i, j, "R");
                }

                if (!visit[i][j] && arr1[i][j].equals("B")) {
                    bfs1(i, j, "B");
                }

                if (!visit[i][j] && arr1[i][j].equals("G")) {
                    bfs1(i, j, "G");
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                visit[i][j] = false;
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!visit[i][j] && arr2[i][j].equals("R")) {
                    bfs2(i, j, "R");
                }

                if (!visit[i][j] && arr2[i][j].equals("B")) {
                    bfs2(i, j, "B");
                }
            }
        }

        System.out.println(num1);
        System.out.println(num2);
    }

    public static void bfs1(int x, int y, String color) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y));
        visit[x][y] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < n && arr1[nx][ny].equals(color) && !visit[nx][ny]) {
                    queue.offer(new Point(nx, ny));
                    visit[nx][ny] = true;
                }
            }
        }

        num1++;
    }

    public static void bfs2(int x, int y, String color) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y));
        visit[x][y] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < n && arr2[nx][ny].equals(color) && !visit[nx][ny]) {
                    queue.offer(new Point(nx, ny));
                    visit[nx][ny] = true;
                }
            }
        }

        num2++;
    }

    static class Point {
        int x;
        int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}