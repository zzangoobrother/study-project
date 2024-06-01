import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;

public class Main {
    static List<Integer> arr;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st;

        while (true) {
            st = new StringTokenizer(br.readLine(), " ");

            int num = Integer.parseInt(st.nextToken());
            if (num == 0) {
                break;
            }

            arr = new ArrayList<>();
            while (st.hasMoreElements()) {
                arr.add(Integer.parseInt(st.nextToken()));
            }

            System.out.println(getArea(num));
        }
    }

    private static long getArea(int num) {
        Stack<Integer> stack = new Stack<>();
        long max = 0;

        for (int i = 0; i < num; i++) {
            while (!stack.isEmpty() && arr.get(stack.peek()) >= arr.get(i)) {
                int height = arr.get(stack.pop());

                long width = stack.isEmpty() ? i : i - 1 - stack.peek();

                max = Math.max(max, height * width);
            }

            stack.push(i);
        }

        while (!stack.isEmpty()) {
            int height = arr.get(stack.pop());

            long width = stack.isEmpty() ? num : num - 1 - stack.peek();
            max = Math.max(max, width * height);
        }

        return max;
    }
}