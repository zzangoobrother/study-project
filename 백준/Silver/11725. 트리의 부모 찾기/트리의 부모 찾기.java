import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {
    static List<List<Integer>> arr;
    static boolean[] visit;
    static int n;
    static StringBuilder sb = new StringBuilder();
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());

        arr = new ArrayList<>();
        visit = new boolean[n + 1];

        for (int i = 0; i <= n; i++) {
            arr.add(new ArrayList<>());
        }

        for (int i = 0; i < n - 1; i++) {
            st = new StringTokenizer(br.readLine());

            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());

            arr.get(x).add(y);
            arr.get(y).add(x);
        }

        bfs(1);

        System.out.println(sb);
    }

    public static void bfs(int i) {
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(i);

        visit[i] = true;

        int[] parent = new int[n + 1];
        while (!queue.isEmpty()) {
            Integer index = queue.poll();

            for (int next : arr.get(index)) {
                if (!visit[next]) {
                    visit[next] = true;
                    parent[next] = index;
                    queue.add(next);
                }
            }
        }

        for (int j = 2; j <= n; j++) {
            sb.append(parent[j]).append("\n");
        }
    }
}