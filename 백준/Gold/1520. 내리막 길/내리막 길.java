import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static int[][] arr;
    static int[][] dp;
    static int n, m, result;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new int[n][m];
        dp = new int[n][m];


        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < m; j++) {
                int num = Integer.parseInt(st.nextToken());

                arr[i][j] = num;
                dp[i][j] = -1;
            }
        }

        dp[n-1][m-1] = 1;
        System.out.println(dfs(0, 0));
    }

    public static int dfs(int x, int y) {
        if (dp[y][x] != -1) {
            return dp[y][x];
        }

        int temp = 0;

        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];

            if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                if (arr[y][x] > arr[ny][nx]) {
                    temp += dfs(nx, ny);
                }
            }
        }

        dp[y][x] = temp;
        return dp[y][x];
    }
}