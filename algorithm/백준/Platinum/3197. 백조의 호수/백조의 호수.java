import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static String[][] arr;
    static boolean[][] visit;
    static Queue<Point> melt = new LinkedList<>();
    static Queue<Point> swan = new LinkedList<>();
    static int n, m, swanX, swanY, desX, dexY, count = 0;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        StringBuilder sb = new StringBuilder();

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new String[n][m];
        visit = new boolean[n][m];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            String[] split = st.nextToken().split("");
            for (int j = 0; j < m; j++) {
                arr[i][j] = split[j];
                if ("L".equals(split[j])) {
                    if (swanX == desX && swanY == dexY) {
                        swanX = j;
                        swanY = i;
                    } else {
                        desX = j;
                        dexY = i;
                    }
                }

                if (!split[j].equals("X")) {
                    melt.offer(new Point(j, i));
                }
            }
        }


        swan.offer(new Point(swanX, swanY));
        visit[swanY][swanX] = true;

        while (!dfs()) {
            melt();
            count++;
        }

        System.out.println(count);
    }

    static void melt() {
        int size = melt.size();

        for (int i = 0; i < size; i++) {
            Point point = melt.poll();

            for (int j = 0; j < 4; j++) {
                int nx = point.x + dx[j];
                int ny = point.y + dy[j];

                if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                    if (arr[ny][nx].equals("X")) {
                        arr[ny][nx] = ".";
                        melt.offer(new Point(nx, ny));
                    }
                }
            }
        }
    }

    static boolean dfs() {
        Queue<Point> queue = new LinkedList<>();

        while (!swan.isEmpty()) {
            Point point = swan.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < m && ny >= 0 && ny < n && !visit[ny][nx] ) {
                    visit[ny][nx] = true;
                    if (nx == desX && ny == dexY) {
                        return true;
                    } else if (arr[ny][nx].equals("X")) {
                        queue.offer(new Point(nx, ny));
                    } else {
                        swan.offer(new Point(nx, ny));
                    }
                }
            }
        }

        swan = queue;
        return false;
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