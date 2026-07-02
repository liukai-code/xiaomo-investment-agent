package com.itlk.myclaudecode.user.service;

import com.itlk.myclaudecode.user.repository.UserRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class AccountIdGenerator {

    private static final int MAX_RETRIES = 10;
    private static final String PREFIX = "user_";
    private final Random random = new Random();

    @Resource
    private UserRepository userRepository;

    public String generate() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            int number = 100000 + random.nextInt(900000);
            String accountId = PREFIX + number;
            if (!userRepository.existsByAccountId(accountId)) {
                return accountId;
            }
        }
        throw new RuntimeException("无法生成唯一账号ID，请重试");
    }
}
