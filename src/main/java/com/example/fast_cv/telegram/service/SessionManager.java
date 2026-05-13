package com.example.fast_cv.telegram.service;

import com.example.fast_cv.telegram.model.UserSession;

import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final ConcurrentHashMap<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession get(Long userId){
        return sessions.computeIfAbsent(userId,id -> new UserSession());
    }

}
