package com.cloudcomment.persistence;

import com.cloudcomment.service.RegisteredUser;

import java.util.Set;

public interface UserAccountRepository {

    boolean existsByEmail(String email);

    RegisteredUser create(String email, String passwordHash, Set<String> roles);
}
