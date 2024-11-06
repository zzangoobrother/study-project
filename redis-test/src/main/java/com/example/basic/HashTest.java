package com.example.basic;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

public class HashTest {

    public static void main(String[] args) {
        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
            try (Jedis jedis = jedisPool.getResource()) {
                // hash
                jedis.hset("users:2:info", "name", "greg2");

                Map<String, String> map = new HashMap<>();
                map.put("email", "greg2@gmail.com");
                map.put("phone", "010-1234-12344");

                jedis.hset("users:2:info", map);

                jedis.hdel("users:2:info", "phone");

                System.out.println(jedis.hget("users:2:info", "email"));
                System.out.println(jedis.hgetAll("users:2:info"));

                jedis.hincrBy("users:2:info", "visits", 1);
            }
        }
    }
}
