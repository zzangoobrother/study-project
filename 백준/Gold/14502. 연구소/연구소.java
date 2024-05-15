import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {
    static int dx[] = {1, 0, -1, 0};
    static int dy[] = {0, 1, 0, -1};
    static int[][] arr;
    static int n, m, result = Integer.MIN_VALUE;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new int[n][m];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < m; j++) {
                arr[i][j] = Integer.parseInt(st.nextToken());
            }
        }

        dfs(0);

        System.out.println(result);
    }

    public static void dfs(int wall) {
        if (wall == 3) {
            bfs();
            return;
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (arr[i][j] == 0) {
                    arr[i][j] = 1;
                    dfs(wall + 1);
                    arr[i][j] = 0;
                }
            }
        }
    }

    public static void bfs() {
        Queue<Point> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (arr[i][j] == 2) {
                    queue.offer(new Point(i, j));
                }
            }
        }

        int[][] copyArr = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                copyArr[i][j] = arr[i][j];
            }
        }

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < m && copyArr[nx][ny] == 0) {
                    queue.offer(new Point(nx, ny));
                    copyArr[nx][ny] = 2;
                }
            }
        }

        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (copyArr[i][j] == 0) {
                    count++;
                }
            }
        }

        result = Math.max(count, result);
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