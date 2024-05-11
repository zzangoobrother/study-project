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

        StringTokenizer st = new StringTokenizer(br.readLine());
        int len = Integer.parseInt(st.nextToken());
        int num = Integer.parseInt(st.nextToken());

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < len; i++) {
            queue.offer(i + 1);
        }

        int count = 1;
        sb.append("<");
        while (true) {
            if (queue.isEmpty()) {
                break;
            }

            if (count == num) {
                count = 1;
                Integer target = queue.poll();
                sb.append(target).append(", ");
                continue;
            }

            count++;
            Integer target = queue.poll();
            queue.offer(target);
        }

        String substring = sb.substring(0, sb.length() - 2);

        System.out.println(substring + ">");
    }
}