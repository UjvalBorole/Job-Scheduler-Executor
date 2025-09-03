package com.JobService.services.impl;

import com.JobService.entities3.CancelReq;
import com.JobService.entities3.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CancelReqRepository {

    @Qualifier("objectRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PREFIX = "cancelReq:";

    // CREATE or UPDATE
    public void save(CancelReq cancelReq) {
        String key = PREFIX + cancelReq.getId();
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        // Save fields in hash
        hashOps.put(key, "id", cancelReq.getId());
        hashOps.put(key, "name", cancelReq.getName());
        hashOps.put(key, "type", cancelReq.getType().name());

        // Set TTL for hash (24 hours)
        redisTemplate.expire(key, Duration.ofHours(24));

        // Maintain name → id mapping
        String nameKey = PREFIX + "name:" + cancelReq.getName();
        redisTemplate.opsForValue().set(nameKey, cancelReq.getId(), Duration.ofHours(24));
    }


    // GET by id
    public CancelReq findById(String id) {
        String key = PREFIX + id;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        Map<Object, Object> map = hashOps.entries(key);

        if (map == null || map.isEmpty()) {
            return null;
        }

        CancelReq req = new CancelReq();
        req.setId((String) map.get("id"));
        req.setName((String) map.get("name"));
        req.setType(Type.valueOf((String) map.get("type")));
        return req;
    }

    // GET by name
    public CancelReq findByName(String name) {
        String id = (String) redisTemplate.opsForValue().get(PREFIX + "name:" + name);
        if (id == null) return null;
        return findById(id);
    }

    // DELETE
    public void deleteById(String id) {
        CancelReq req = findById(id);
        if (req != null) {
            redisTemplate.delete(PREFIX + id);
            redisTemplate.delete(PREFIX + "name:" + req.getName());
        }
    }
}
