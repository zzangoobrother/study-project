import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(br.readLine());

        Map<Integer, Integer> nums = new HashMap<>();
        StringTokenizer st = new StringTokenizer(br.readLine());
        for (int i = 0; i < n; i++) {
            int target = Integer.parseInt(st.nextToken());
            if (nums.containsKey(target)) {
                nums.put(target, nums.get(target) + 1);
            } else {
                nums.put(target, 1);
            }
        }

        int m = Integer.parseInt(br.readLine());
        st = new StringTokenizer(br.readLine());
        for (int i = 0; i < m; i++) {
            int target = Integer.parseInt(st.nextToken());
            if (nums.containsKey(target)) {
                sb.append(nums.get(target)).append(" ");
            } else {
                sb.append("0").append(" ");
            }
        }

        System.out.println(sb.toString());
    }
}