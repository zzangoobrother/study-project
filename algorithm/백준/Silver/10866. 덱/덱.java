import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(br.readLine());

        Deque<String> deque = new LinkedList<>();

        for (int i = 0; i < n; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            String s = st.nextToken();
            if (s.equals("push_front")) {
                deque.offerFirst(st.nextToken());
            } else if (s.equals("push_back")) {
                deque.offerLast(st.nextToken());
            } else if (s.equals("pop_front")) {
                if (deque.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    String target = deque.poll();
                    sb.append(target).append("\n");
                }
            } else if (s.equals("pop_back")) {
                if (deque.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    String target = deque.pollLast();
                    sb.append(target).append("\n");
                }
            } else if (s.equals("size")) {
                sb.append(deque.size()).append("\n");
            } else if (s.equals("empty")) {
                if (deque.isEmpty()) {
                    sb.append("1").append("\n");
                } else {
                    sb.append("0").append("\n");
                }
            } else if (s.equals("front")) {
                if (deque.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(deque.peek()).append("\n");
                }
            } else {
                if (deque.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(deque.peekLast()).append("\n");
                }
            }
        }

        System.out.println(sb.toString());
    }
}