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
    static int[][] dis;
    static int n, m;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new int[n][m];
        dis = new int[n][m];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            String input = st.nextToken();

            String[] split = input.split("");
            for (int j = 0; j < m; j++) {
                arr[i][j] = Integer.parseInt(split[j]);
            }
        }

        dis[0][0] = 1;
        bfs(0, 0);

        System.out.println(dis[n - 1][m-1]);
    }

    public static void bfs(int x, int y) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y));
        arr[0][0] = 0;
        while (!queue.isEmpty()) {
            Point temp = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = temp.x + dx[i];
                int ny = temp.y + dy[i];
                if (nx >= 0 && nx < n && ny >= 0 && ny < m && arr[nx][ny] == 1) {
                    dis[nx][ny] = dis[temp.x][temp.y] + 1;
                    arr[nx][ny] = 0;
                    queue.offer(new Point(nx, ny));
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