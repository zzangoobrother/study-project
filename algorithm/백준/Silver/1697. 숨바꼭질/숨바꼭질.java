import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {
    static boolean[] visit = new boolean[100001];
    static int n, k, result;
    static Queue<Integer> queue = new LinkedList<>();

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        k = Integer.parseInt(st.nextToken());

        if (n == k) {
            System.out.println(0);
            return;
        }

        visit[n] = true;
        queue.offer(n);

        bfs();

        System.out.println(result);
    }

    public static void bfs() {
        int size;
        while (true) {
            result++;
            size = queue.size();
            for (int i = 0; i < size; i++) {
                Integer num = queue.poll();

                int a = num + 1;
                int b = num - 1;
                int c = 2 * num;

                if (a == k || b == k || c == k) {
                    return;
                } else {
                    if (a >= 0 && a <= 100000 && !visit[a]) {
                        visit[a] = true;
                        queue.offer(a);
                    }

                    if (b >= 0 && b <= 100000 && !visit[b]) {
                        visit[b] = true;
                        queue.offer(b);
                    }

                    if (c >= 0 && c <= 100000 && !visit[c]) {
                        visit[c] = true;
                        queue.offer(c);
                    }
                }
            }
        }
    }
}