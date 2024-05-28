import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static String[][] arr;
    static boolean[][] visit;
    static int r;
    static int c;
    static int k;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        r = Integer.parseInt(st.nextToken());
        c = Integer.parseInt(st.nextToken());
        arr = new String[r][c];

        for (int i = 0; i < r; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            String[] split = st.nextToken().split("");
            for (int j = 0; j < c; j++) {
                arr[i][j] = split[j];
            }
        }

        st = new StringTokenizer(br.readLine(), " ");
        k = Integer.parseInt(st.nextToken());

        boolean flag = true;
        st = new StringTokenizer(br.readLine(), " ");
        for (int i = 0; i < k; i++) {
            int n = Integer.parseInt(st.nextToken());

            if (flag) {
                for (int j = 0; j < c; j++) {
                    if ("x".equals(arr[r-n][j])) {
                        arr[r-n][j] = ".";
                        break;
                    }
                }
            } else {
                for (int j = c - 1; j >= 0; j--) {
                    if ("x".equals(arr[r-n][j])) {
                        arr[r-n][j] = ".";
                        break;
                    }
                }
            }

            flag = !flag;

            bfs();
        }

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                System.out.print(arr[i][j]);
            }
            System.out.println();
        }
    }

    private static void bfs() {
        Queue<Point> queue = new LinkedList<>();
        List<Point> points = new ArrayList<>();
        visit = new boolean[r][c];

        for (int i = 0; i < c; i++) {
            if (arr[r-1][i].equals(".") || visit[r-1][i]) {
                continue;
            }

            visit[r-1][i] = true;
            queue.offer(new Point(i, r-1));

            while (!queue.isEmpty()) {
                Point point = queue.poll();

                for (int j = 0; j < 4; j++) {
                    int nx = point.x + dx[j];
                    int ny = point.y + dy[j];

                    if (nx < 0 || nx >= c || ny < 0 || ny >= r || visit[ny][nx] || arr[ny][nx].equals(".")) {
                        continue;
                    }

                    visit[ny][nx] = true;
                    queue.offer(new Point(nx, ny));
                }
            }
        }

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                if (!visit[i][j] && arr[i][j].equals("x")) {
                    arr[i][j] = ".";
                    points.add(new Point(j, i));
                }
            }
        }

        if (points.isEmpty()) {
            return;
        }

        boolean down = true;
        while (down) {
            for (Point point : points) {
                int ny = point.y + 1;
                int nx = point.x;

                if (nx < 0 || nx >= c || ny < 0 || ny >= r || arr[ny][nx].equals("x")) {
                    down = false;
                    break;
                }
            }

            if (down) {
                for (Point point : points) {
                    point.y++;
                }
            }
        }

        for (Point point : points) {
            arr[point.y][point.x] = "x";
        }
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