package com.example.basic;

public class ListTest {

//    public static void main(String[] args) {
//        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
//            try (Jedis jedis = jedisPool.getResource()) {
//                // list
//                // stack
//                jedis.rpush("stack1", "aaaa");
//                jedis.rpush("stack1", "bbbb");
//                jedis.rpush("stack1", "cccc");
//
////                List<String> stack1 = jedis.lrange("stack1", 0, -1);
////                stack1.forEach(System.out::println);
//
//                System.out.println(jedis.rpop("stack1"));
//                System.out.println(jedis.rpop("stack1"));
//                System.out.println(jedis.rpop("stack1"));
//
//                // queue
//                jedis.rpush("queue2", "aaaa");
//                jedis.rpush("queue2", "bbbb");
//                jedis.rpush("queue2", "cccc");
//
//                System.out.println(jedis.lpop("queue2"));
//                System.out.println(jedis.lpop("queue2"));
//                System.out.println(jedis.lpop("queue2"));
//
//                // block brpop, blpop
//                while (true) {
//                    List<String> blpop = jedis.blpop(10, "queue:blocking");
//                    if (blpop != null) {
//                        blpop.forEach(System.out::println);
//                        break;
//                    }
//                }
//            }
//        }
//    }
}
