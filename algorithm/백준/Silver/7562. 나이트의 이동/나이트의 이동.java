import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {
    static int[] dx = {-1, -2, -2, -1, 1, 2,  2, 1};
    static int[] dy = {-2, -1, 1, 2, 2, 1, -1, -2};
    static int[][] arr;
    static boolean[][] visit;
    static int n;
    static int nowx, nowy, desx, desy;
    static StringBuilder sb = new StringBuilder();

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        StringBuilder sb = new StringBuilder();

        int num = Integer.parseInt(st.nextToken());

        for (int i = 0; i < num; i++) {
            st = new StringTokenizer(br.readLine());
            n = Integer.parseInt(st.nextToken());

            arr = new int[n][n];
            visit = new boolean[n][n];

            st = new StringTokenizer(br.readLine());
            nowy = Integer.parseInt(st.nextToken());
            nowx = Integer.parseInt(st.nextToken());

            st = new StringTokenizer(br.readLine());
            desy = Integer.parseInt(st.nextToken());
            desx = Integer.parseInt(st.nextToken());

            if (nowx == desx && nowy == desy) {
                sb.append(0).append("\n");
            } else {
                sb.append(bfs(nowx, nowy)).append("\n");
            }
        }

        System.out.println(sb);
    }

    public static int bfs(int x, int y) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(x, y, 0));

        visit[y][x] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            if (desx == point.x && desy == point.y) {
                return point.cnt;
            }

            for (int i = 0; i < 8; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < n && !visit[ny][nx]) {
                    visit[ny][nx] = true;
                    queue.offer(new Point(nx, ny, point.cnt + 1));
                }
            }
        }

        return 0;
    }

    static class Point {
        int x;
        int y;
        int cnt;

        public Point(int x, int y, int cnt) {
            this.x = x;
            this.y = y;
            this.cnt = cnt;
        }
    }
}