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

        for (int i = 0; i < n; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            String s = st.nextToken();

            dd(s, sb);
        }

        System.out.println(sb.toString());
    }

    private static void dd(String s, StringBuilder sb) {
        Stack<Character> stack = new Stack<>();
        for (char temp : s.toCharArray()) {
            if ('(' == temp) {
                stack.push(temp);
            } else {
                if (!stack.isEmpty() && stack.peek() == '(') {
                    stack.pop();
                } else {
                    sb.append("NO").append("\n");
                    return;
                }
            }
        }

        if (!stack.isEmpty()) {
            sb.append("NO").append("\n");
            return;
        }

        sb.append("YES").append("\n");
    }
}