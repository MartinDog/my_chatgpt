package com.mychatgpt.service;

import com.mychatgpt.entity.User;
import com.mychatgpt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getOrCreateUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUserId(userId);
                    return userRepository.save(user);
                });
    }

    public boolean existsUser(String userId) {
        return userRepository.existsByUserId(userId);
    }

    public User getUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
    }
}
