import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        StringBuilder sb = new StringBuilder();

        int n = Integer.parseInt(st.nextToken());

        Queue<Integer> maxQ = new PriorityQueue<>(Comparator.reverseOrder());
        Queue<Integer> minQ = new PriorityQueue<>();

        for (int i = 0; i < n; i++) {
            st = new StringTokenizer(br.readLine());
            int num = Integer.parseInt(st.nextToken());

            if (maxQ.size() == minQ.size()) {
                maxQ.offer(num);

                if (!minQ.isEmpty() && maxQ.peek() > minQ.peek()) {
                    minQ.offer(maxQ.poll());
                    maxQ.offer(minQ.poll());
                }
            } else {
                minQ.offer(num);

                if (maxQ.peek() > minQ.peek()) {
                    minQ.offer(maxQ.poll());
                    maxQ.offer(minQ.poll());
                }
            }

            sb.append(maxQ.peek()).append("\n");
        }

        System.out.println(sb);
    }
}