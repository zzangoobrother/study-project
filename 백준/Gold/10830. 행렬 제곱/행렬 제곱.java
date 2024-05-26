import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    static int n;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        n = Integer.parseInt(st.nextToken());
        long k = Long.parseLong(st.nextToken());

        long[][] arr = new long[n][n];
        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            for (int j = 0; j < n; j++) {
                int num = Integer.parseInt(st.nextToken());
                arr[i][j] = num % 1000;
            }
        }

        long[][] result = pow(arr, k);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(result[i][j] + " ");
            }
            System.out.println();
        }
    }

    private static long[][] pow(long[][] arr, long exp) {
        if (exp == 1L) {
            return arr;
        }

        long[][] ret = pow(arr, exp / 2);

        ret = multiply(ret, ret);

        if (exp % 2 == 1L) {
            ret = multiply(ret, arr);
        }

        return ret;
    }

    private static long[][] multiply(long[][] arr1, long[][] arr2) {
        long[][] ret = new long[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    ret[i][j] += arr1[i][k] * arr2[k][j];
                    ret[i][j] %= 1000;
                }
            }
        }

        return ret;
    }
}