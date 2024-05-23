import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Main {
    static List<Point>[] arr;
    static boolean[] visit;
    static int n, max = 0, node;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        arr = new ArrayList[n+1];

        for (int i = 0; i <= n; i++) {
            arr[i] = new ArrayList<>();
        }

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());

            int num1 = Integer.parseInt(st.nextToken());

            while (st.hasMoreTokens()) {
                int num2 = Integer.parseInt(st.nextToken());
                if (num2 == -1) {
                    break;
                }

                int num3 = Integer.parseInt(st.nextToken());

                arr[num1].add(new Point(num2, num3));
            }
        }

        visit = new boolean[n+1];
        bfs(1, 0);

        visit = new boolean[n+1];
        bfs(node, 0);

        System.out.println(max);
    }

    public static void bfs(int target, int len) {
        if (len > max) {
            max = len;
            node = target;
        }

        visit[target] = true;

        for (int i = 0; i < arr[target].size(); i++) {
            Point point = arr[target].get(i);

            if (visit[point.x] == false) {
                bfs(point.x, point.y + len);
                visit[point.x] = true;
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