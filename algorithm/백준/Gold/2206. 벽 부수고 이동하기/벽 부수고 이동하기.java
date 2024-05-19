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
    static boolean[][][] visit;
    static int n, m, result = Integer.MAX_VALUE;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        StringBuilder sb = new StringBuilder();

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new int[n][m];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            String[] split = st.nextToken().split("");
            for (int j = 0; j < m; j++) {
                arr[i][j] = Integer.parseInt(split[j]);
            }
        }

        bfs();

        if (result == Integer.MAX_VALUE) {
            result = -1;
        }

        System.out.println(result);
    }

    public static void bfs() {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(0, 0, 1, false));

        visit = new boolean[n][m][2];

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            if (n-1 == point.y && m-1 == point.x) {
                result = Math.min(point.cnt, result);
                return;
            }

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                    if (arr[ny][nx] == 0) {
                        if (!point.destroyed && !visit[ny][nx][0]) {
                            queue.offer(new Point(nx, ny, point.cnt + 1, false));
                            visit[ny][nx][0] = true;
                        } else if (point.destroyed && !visit[ny][nx][1]) {
                            queue.offer(new Point(nx, ny, point.cnt + 1, true));
                            visit[ny][nx][1] = true;
                        }
                    } else {
                        if (!point.destroyed) {
                            queue.offer(new Point(nx, ny, point.cnt + 1, true));
                            visit[ny][nx][1] = true;
                        }
                    }
                }
            }
        }
    }

    static class Point {
        int x;
        int y;
        int cnt;
        boolean destroyed;

        public Point(int x, int y, int cnt, boolean destroyed) {
            this.x = x;
            this.y = y;
            this.cnt = cnt;
            this.destroyed = destroyed;
        }
    }
}