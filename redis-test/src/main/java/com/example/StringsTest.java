package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.List;

public class StringsTest {

    public static void main(String[] args) {
        System.out.println("Hello world");

        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set("users:300:email", "choi@gmail.com");
                jedis.set("users:300:name", "choi");
                jedis.set("users:300:age", "100");

                String userEmail = jedis.get("users:300:email");
                System.out.println(userEmail);

                List<String> userInfo = jedis.mget("users:300:email", "users:300:name", "users:300:age");
                userInfo.forEach(System.out::println);

//                long counter = jedis.incr("counter");
//                System.out.println(counter);
//
//                counter = jedis.incrBy("counter", 10L);
//                System.out.println(counter);

//                long counter = jedis.decr("counter");
//                System.out.println(counter);

                long counter = jedis.decrBy("counter", 10L);
                System.out.println(counter);

                Pipeline pipelined = jedis.pipelined();
                pipelined.set("users:400:email", "seon@gmail.com");
                pipelined.set("users:400:name", "seon");
                pipelined.set("users:400:age", "20");
                List<Object> objects = pipelined.syncAndReturnAll();
                objects.forEach(i -> System.out.println(i.toString()));
            }
        }
    }
}
