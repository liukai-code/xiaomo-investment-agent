package com.itlk.myclaudecode.auth.event;

import lombok.Getter;

@Getter
public class UserRegisteredEvent {

    private final Long userId;

    public UserRegisteredEvent(Long userId) {
        this.userId = userId;
    }
}
