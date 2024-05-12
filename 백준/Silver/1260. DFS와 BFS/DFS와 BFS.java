import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {
    static boolean[] visit;
    static int[][] arr;
    static int N, M, V;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        N = Integer.parseInt(st.nextToken());
        M = Integer.parseInt(st.nextToken());
        V = Integer.parseInt(st.nextToken());

        arr = new int[N + 1][N + 1];
        visit = new boolean[N+1];

        for (int i = 0; i < M; i++) {
            st = new StringTokenizer(br.readLine());
            int first = Integer.parseInt(st.nextToken());
            int second = Integer.parseInt(st.nextToken());

            arr[first][second] = arr[second][first] = 1;
        }

        StringBuilder sb = new StringBuilder();
        dfs(V, sb);

        System.out.println(sb);

        visit = new boolean[N+1];
        sb = new StringBuilder();

        bfs(V, sb);
        System.out.println(sb);
    }

    public static void dfs(int target, StringBuilder sb) {
        visit[target] = true;
        sb.append(target).append(" ");

        for (int i = 0; i <= N; i++) {
            if (arr[target][i] == 1 && !visit[i]) {
                dfs(i, sb);
            }
        }
    }

    public static void bfs(int target, StringBuilder sb) {
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(target);
        visit[target] = true;

        while (!queue.isEmpty()) {
            Integer start = queue.poll();
            sb.append(start).append(" ");

            for (int i = 1; i <= N; i++) {
                if (arr[start][i] == 1 && !visit[i]) {
                    queue.offer(i);
                    visit[i] = true;
                }
            }
        }
    }
}