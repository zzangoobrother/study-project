package algorithm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int count = Integer.parseInt(st.nextToken());

        for (int i = 0; i < count; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            int num = Integer.parseInt(st.nextToken());

            int[] novel = new int[num + 1];
            int[] sum = new int[num + 1];
            int[][] dp = new int[num + 1][num + 1];

            st = new StringTokenizer(br.readLine(), " ");
            for (int j = 1; j <= num; j++) {
                novel[j] = Integer.parseInt(st.nextToken());
                sum[j] = sum[j - 1] + novel[j];
            }

            for (int j = 1; j <= num; j++) {
                for (int from = 1; from + j <= num; from++) {
                    int to = from + j;
                    dp[from][to] = Integer.MAX_VALUE;
                    for (int divide = from; divide < to; divide++) {
                        dp[from][to] = Math.min(dp[from][to], dp[from][divide] + dp[divide + 1][to] + sum[to] - sum[from - 1]);
                    }
                }
            }

            System.out.println(dp[1][num]);
        }
    }
}
