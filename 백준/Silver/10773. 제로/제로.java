import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Stack;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(br.readLine());

        Stack<Integer> stack = new Stack<>();
        for (int i = 0; i < n; i++) {
            int num = Integer.parseInt(br.readLine());
            if (num == 0) {
                if (stack.isEmpty()) {
                    continue;
                }

                stack.pop();
            } else {
                stack.add(num);
            }
        }

        int result = 0;
        for (Integer i : stack) {
            result += i;
        }


        System.out.println(result);
    }
}