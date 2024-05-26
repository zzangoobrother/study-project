import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int n = Integer.parseInt(st.nextToken());
        int k = Integer.parseInt(st.nextToken());

        long num = factorial(n);

        long denom = factorial(k) * factorial(n-k) % 1000000007;

        System.out.println(num * pow(denom, 1000000007 - 2) % 1000000007);
    }

    private static long factorial(int n) {
        long num = 1L;
        while (n > 1) {
            num = (num * n) % 1000000007;
            n--;
        }

        return num;
    }

    private static long pow(long base, long expo) {
        if (expo == 1) {
            return base % 1000000007;
        }

        long temp = pow(base, expo / 2);

        if (expo % 2 == 1) {
            return (temp * temp % 1000000007) * base % 1000000007;
        }

        return temp * temp % 1000000007;
    }
}