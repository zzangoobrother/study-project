package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

public class SortedSetTest {

    public static void main(String[] args) {
        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
            try (Jedis jedis = jedisPool.getResource()) {
                Map<String, Double> scores = new HashMap<>();
                scores.put("user1", 100.0);
                scores.put("user2", 30.0);
                scores.put("user3", 50.0);
                scores.put("user4", 80.0);
                scores.put("user5", 15.0);

                jedis.zadd("game2:scores", scores);

                System.out.println(jedis.zrange("game2:scores", 0, Long.MAX_VALUE));

                System.out.println(jedis.zrangeWithScores("game2:scores", 0, Long.MAX_VALUE));

                System.out.println(jedis.zcard("game2:scores"));

                jedis.zincrby("game2:scores", 100.0, "user5");
                System.out.println(jedis.zrangeWithScores("game2:scores", 0, Long.MAX_VALUE));
            }
        }
    }
}
