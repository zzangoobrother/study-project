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
    static int n, dis, sharkx, sharky, sharkw = 2, fish, result;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        arr = new int[n][n];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < n; j++) {
                int num = Integer.parseInt(st.nextToken());

                arr[i][j] = num;
                if (arr[i][j] == 9) {
                    sharkx = j;
                    sharky = i;
                    arr[i][j] = 0;
                }
            }
        }

        while (eat()) {}

        System.out.println(result);
    }

    static boolean eat() {
        int minDis = Integer.MAX_VALUE;
        int minx = n, miny = n;
        boolean fount = false;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((j == sharkx && i == sharky) || arr[i][j] == 0) {
                    continue;
                }

                if (arr[i][j] < sharkw) {
                    dis = 0;
                    bfs(j, i, new boolean[n][n]);

                    if (dis == 0) {
                        continue;
                    }

                    fount = true;

                    if (dis < minDis) {
                        minDis = dis;
                        minx = j;
                        miny = i;
                    } else if (dis == minDis) {
                        if (i < miny) {
                            minx = j;
                            miny = i;
                        } else if (i == miny) {
                            if (j < minx) {
                                minx = j;
                            }
                        }
                    }
                }
            }
        }

        if (fount) {
            result += minDis;
            sharkx = minx;
            sharky = miny;
            arr[sharky][sharkx] = 0;
            fish++;
            if (fish == sharkw) {
                fish = 0;
                sharkw++;
            }
        }

        return fount;
    }

    public static void bfs(int x, int y, boolean[][] visit) {
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(sharkx, sharky, 1));
        visit[sharky][sharkx] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();

            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];

                if (nx >= 0 && nx < n && ny >= 0 && ny < n && !visit[ny][nx] && arr[ny][nx] <= sharkw) {
                    visit[ny][nx] = true;
                    queue.offer(new Point(nx, ny, point.count + 1));
                }
            }

            if (visit[y][x]) {
                dis = point.count;
                break;
            }
        }
    }

    static class Point {
        int x;
        int y;
        int count;

        public Point(int x, int y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }
}