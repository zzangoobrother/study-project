import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int num = 1500000;
        long n = Long.parseLong(st.nextToken()) % num;

        long[] dp = new long[num + 1];
        dp[0] = 0;
        dp[1] = 1;

        for (int i = 2; i <= num; i++) {
            dp[i] = (dp[i - 1] + dp[i - 2]) % 1000000;
        }

        System.out.println(dp[(int) n]);
    }
}