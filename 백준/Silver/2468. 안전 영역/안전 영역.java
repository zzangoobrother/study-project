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
    static boolean[][] visit;
    static int n, result = Integer.MIN_VALUE, temp = 0;
    static int max = 0;
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        arr = new int[n][n];
        visit = new boolean[n][n];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < n; j++) {
                int num = Integer.parseInt(st.nextToken());
                arr[i][j] = num;
                max = Math.max(max, num);
            }
        }

        for (int i = 0; i <= max; i++) {
            temp = 0;
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    if (!visit[j][k] && arr[j][k] > i) {
                        bfs(j, k, i);
                    }
                }
            }

            result = Math.max(result, temp);

            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    visit[j][k] = false;
                }
            }
        }

        System.out.println(result);
    }

    public static void bfs(int x, int y, int hight) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y));
        visit[x][y] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < n && arr[nx][ny] > hight && !visit[nx][ny]) {
                    queue.offer(new Point(nx, ny));
                    visit[nx][ny] = true;
                }
            }
        }

        temp++;
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