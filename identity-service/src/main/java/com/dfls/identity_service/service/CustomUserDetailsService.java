package com.dfls.identity_service.service;

//import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dfls.identity_service.model.SecurityUser;
import com.dfls.identity_service.model.User;
import com.dfls.identity_service.repository.UserRepository;
import com.dfls.shared.exception.UserNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;

    @Override
    @Cacheable(value = "auth_users", key = "#email")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        log.debug("Cache miss. Fetching user from database for email: {}", email);
        
        // Find user by email
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                log.warn("Login failed: User not found with email: {}", email);
                return new UserNotFoundException("User not found with email: " + email);
            }); 
        
        return new SecurityUser(user);
    }

    //TODO: Hanlde caching

    // // 2. Clear cache if user updates password/roles, forcing a fresh DB read next time
    // @CacheEvict(value = "auth_users", key = "#username")
    // public void handleUserUpdate(String username) {
    //     // Logic to update user in DB goes here
    // }

}