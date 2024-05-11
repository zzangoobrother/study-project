import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        StringTokenizer st = new StringTokenizer(br.readLine());
        int n = Integer.parseInt(st.nextToken());
        int m = Integer.parseInt(st.nextToken());

        Set<String> basket = new HashSet<>();
        for (int i = 0; i < n; i++) {
            basket.add(new StringTokenizer(br.readLine()).nextToken());
        }

        int count = 0;
        Set<String> result = new TreeSet<>();
        for (int i = 0; i < m; i++) {
            String target = new StringTokenizer(br.readLine()).nextToken();
            if (basket.contains(target)) {
                count++;
                result.add(target);
            }
        }

        sb.append(count).append("\n");
        for (String s : result) {
            sb.append(s).append("\n");
        }

        System.out.println(sb);
    }
}