package com.shopaccgame.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final Integer id;
    private final String username;
    private final String role;
}
