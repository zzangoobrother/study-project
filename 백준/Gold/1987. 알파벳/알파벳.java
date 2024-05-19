import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Main {
    static int[] dx = {1, 0, -1, 0};
    static int[] dy = {0, 1, 0, -1};
    static Character[][] arr;
    static boolean[][] visit;
    static int n, m, result = Integer.MIN_VALUE;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        n = Integer.parseInt(st.nextToken());
        m = Integer.parseInt(st.nextToken());

        arr = new Character[n][m];
        visit = new boolean[n][m];

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            String s = st.nextToken();
            for (int j = 0; j < m; j++) {
                arr[i][j] = s.charAt(j);
            }
        }

        visit[0][0] = true;
        dfs(0, 0, 0, new ArrayList<>());

        System.out.println(result+1);
    }

    public static void dfs(int x, int y, int count, List<Character> road) {
        road.add(arr[y][x]);
        result = Math.max(result, count);
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];

            if (nx >= 0 && nx < m && ny >= 0 && ny < n) {
                if (!visit[ny][nx] && !road.contains(arr[ny][nx])) {
                    visit[ny][nx] = true;
                    dfs(nx, ny, count+1, road);
                    visit[ny][nx] = false;

                    road.remove(road.size()-1);
                }
            }
        }
    }
}