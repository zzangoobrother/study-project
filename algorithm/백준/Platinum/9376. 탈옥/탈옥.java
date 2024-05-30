import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Main {

    static int[] dx = {0, 1, 0, -1};
    static int[] dy = {1, 0, -1, 0};
    static char[][] arr;
    static boolean[][] visit;
    static int[][] sangun, prison1, prison2;
    static int h, w;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        StringBuilder sb = new StringBuilder();

        int count = Integer.parseInt(st.nextToken());

        for (int k = 0; k < count; k++) {
            st = new StringTokenizer(br.readLine(), " ");
            h = Integer.parseInt(st.nextToken());
            w = Integer.parseInt(st.nextToken());
            List<Point> humans = new ArrayList<>();
            arr = new char[h+2][w+2];
            humans.add(new Point(0, 0, 0));
            for (int i = 0; i < h; i++) {
                String input = new StringTokenizer(br.readLine(), " ").nextToken();
                for (int j = 0; j < w; j++) {
                    arr[i+1][j+1] = input.charAt(j);
                    if (input.charAt(j) == '$') {
                        humans.add(new Point(j+1, i+1, 0));
                    }
                }
            }

            sangun = bfs(humans.get(0).x, humans.get(0).y);
            prison1 = bfs(humans.get(1).x, humans.get(1).y);
            prison2 = bfs(humans.get(2).x, humans.get(2).y);

            sb.append(minDoor()).append("\n");
        }

        System.out.println(sb);
    }

    private static int[][] bfs(int x, int y) {
        PriorityQueue<Point> queue = new PriorityQueue<>();
        queue.offer(new Point(x, y, 0));
        visit = new boolean[h+2][w+2];
        int[][] result = new int[h+2][w+2];
        visit[y][x] = true;

        while (!queue.isEmpty()) {
            Point point = queue.poll();
            result[point.y][point.x] = point.count;
            for (int i = 0; i < 4; i++) {
                int nx = point.x + dx[i];
                int ny = point.y + dy[i];
                if (nx >= 0 && nx < w+2 && ny >= 0 && ny < h+2 && !visit[ny][nx] && arr[ny][nx] != '*') {
                    if (arr[ny][nx] == '#') {
                        queue.offer(new Point(nx, ny, point.count + 1));
                    } else {
                        queue.offer(new Point(nx, ny, point.count));
                    }
                    visit[ny][nx] = true;
                }
            }
        }

        return result;
    }

    private static int minDoor() {
        int result = Integer.MAX_VALUE;
        for (int i = 1; i < h+1; i++) {
            for (int j = 1; j < w+1; j++) {
                if (arr[i][j] == '*') {
                    continue;
                }

                int sum = sangun[i][j] + prison1[i][j] + prison2[i][j];
                if (arr[i][j] == '#') {
                    sum -= 2;
                }

                if (result > sum && visit[i][j]) {
                    result = sum;
                }
            }
        }

        return result;
    }

    static class Point implements Comparable<Point> {
        int x;
        int y;
        int count;

        public Point(int x, int y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }

        @Override
        public int compareTo(Point o) {
            return this.count - o.count;
        }
    }
}