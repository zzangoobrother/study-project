import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(br.readLine());
        int last = 0;

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            String s = st.nextToken();
            if (s.equals("push")) {
                int target = Integer.parseInt(st.nextToken());
                queue.offer(target);
                last = target;
            } else if (s.equals("pop")) {
                if (queue.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(queue.poll()).append("\n");
                }
            } else if (s.equals("size")) {
                sb.append(queue.size()).append("\n");
            } else if (s.equals("empty")) {
                if (queue.isEmpty()) {
                    sb.append("1").append("\n");
                } else {
                    sb.append("0").append("\n");
                }
            } else if (s.equals("front")) {
                if (queue.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(queue.peek()).append("\n");
                }
            } else {
                if (queue.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(last).append("\n");
                }
            }
        }

        System.out.println(sb.toString());
    }
}