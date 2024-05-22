import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static int[][] arr;
    static int[][] dp;
    static boolean[][] visit;
    static int n, m, count = 0;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new int[n][m];
        dp = new int[n][m];


        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < m; j++) {
                int num = Integer.parseInt(st.nextToken());

                arr[i][j] = num;
            }
        }

        int result = 0;
        while (true) {
            boolean flag = true;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    if (arr[i][j] != 0) {
                        dfs(j, i);
                        flag = false;
                    }
                }
            }

            if (flag) {
                result = 0;
                break;
            }

            visit = new boolean[n][m];
            count = 0;

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    bfs(j, i);
                }
            }

            result++;

            if (count > 1) {
                break;
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    arr[i][j] = dp[i][j];
                }
            }
        }

        System.out.println(result);
    }

    public static void dfs(int x, int y) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];

            if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                if (arr[ny][nx] == 0) {
                    count++;
                }
            }
        }

        dp[y][x] = Math.max((arr[y][x] - count), 0);
    }

    public static void bfs(int x, int y) {
        if (visit[y][x]) {
            return;
        }

        if (dp[y][x] == 0) {
            return;
        }

        count++;
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y));
        visit[y][x] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                    if (!visit[ny][nx] && dp[ny][nx] != 0) {
                        visit[ny][nx] = true;
                        queue.offer(new Point(nx, ny));
                    }
                }
            }
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