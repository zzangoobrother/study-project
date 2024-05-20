import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static int[][] arr;
    static boolean[] visit;
    static int n, m, desX, desY, count = Integer.MAX_VALUE;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        st = new StringTokenizer(br.readLine());
        desX = Integer.parseInt(st.nextToken());
        desY = Integer.parseInt(st.nextToken());

        arr = new int[n+1][n+1];
        visit = new boolean[n+1];

        st = new StringTokenizer(br.readLine());
        m = Integer.parseInt(st.nextToken());
        for (int i = 0; i < m; i++) {
            st = new StringTokenizer(br.readLine());

            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());

            arr[x][y] = arr[y][x] = 1;
        }

        visit[desX] = true;
        dfs(desX, 0);

        System.out.println(count == Integer.MAX_VALUE ? -1 : count);
    }

    public static void dfs(int target, int num) {
        if (target == desY) {
            count = Math.min(count, num);
            return;
        }

        for (int i = 1; i <= n; i++) {
            if (arr[target][i] == 1 && !visit[i]) {
                visit[i] = true;
                dfs(i, num + 1);
            }
        }
    }
}