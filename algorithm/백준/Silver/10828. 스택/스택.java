import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Stack;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(br.readLine());

        Stack<String> stack = new Stack<>();
        for (int i = 0; i < n; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            String s = st.nextToken();

            if (s.startsWith("push")) {
                String target = st.nextToken();
                stack.push(target);
            } else if (s.startsWith("pop")) {
                if (stack.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    String target = stack.pop();
                    sb.append(target).append("\n");
                }
            } else if (s.startsWith("size")) {
                int length = stack.size();
                sb.append(length).append("\n");
            } else if (s.startsWith("empty")) {
                if (stack.isEmpty()) {
                    sb.append("1").append("\n");
                } else {
                    sb.append("0").append("\n");
                }
            } else {
                if (stack.isEmpty()) {
                    sb.append("-1").append("\n");
                } else {
                    sb.append(stack.peek()).append("\n");
                }
            }
        }

        System.out.println(sb.toString());
    }
}