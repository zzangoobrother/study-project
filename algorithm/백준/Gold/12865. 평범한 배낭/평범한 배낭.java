import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    static Integer[][] dp;
    static int[] w;
    static int[] v;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        int n = Integer.parseInt(st.nextToken());
        int m = Integer.parseInt(st.nextToken());

        w = new int[n];
        v = new int[n];
        dp = new Integer[n][m + 1];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            w[i] = Integer.parseInt(st.nextToken());
            v[i] = Integer.parseInt(st.nextToken());
        }

        System.out.println(func(n - 1, m));
    }

    static int func(int i, int m) {
        if (i < 0) {
            return 0;
        }

        if (dp[i][m] == null) {
            if (w[i] > m) {
                dp[i][m] = func(i - 1, m);
            } else {
                dp[i][m] = Math.max(func(i - 1, m), func(i - 1, m - w[i]) + v[i]);
            }
        }

        return dp[i][m];
    }
}