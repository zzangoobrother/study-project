import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        StringTokenizer st = new StringTokenizer(br.readLine());
        int num = Integer.parseInt(st.nextToken());
        LinkedList<Block> queue = new LinkedList<>();
        for (int i = 0; i < num; i++) {
            st = new StringTokenizer(br.readLine());
            int n = Integer.parseInt(st.nextToken());
            int m = Integer.parseInt(st.nextToken());

            st = new StringTokenizer(br.readLine());
            for (int j = 0; j < n; j++) {
                int importance = Integer.parseInt(st.nextToken());
                queue.offer(new Block(j, importance));
            }

            int count = 0;
            while (!queue.isEmpty()) {
                Block block = queue.poll();
                boolean flag = true;

                for (int j = 0; j < queue.size(); j++) {
                    if (block.importance < queue.get(j).importance) {
                        queue.offer(block);
                        for (int k = 0; k < j; k++) {
                            queue.offer(queue.poll());
                        }

                        flag = false;
                        break;
                    }
                }

                if (!flag) {
                    continue;
                }

                count++;
                if (block.index == m) {
                    break;
                }
            }

            queue.clear();

            sb.append(count).append("\n");
        }

        System.out.println(sb);
    }

    static class Block {
        private int index;
        private int importance;

        public Block(int index, int importance) {
            this.index = index;
            this.importance = importance;
        }
    }
}