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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;

    /* 
    @Override
    @Cacheable(value = "auth_users", key = "#username")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("--- HIT DATABASE: Looking up user: " + username + " ---");
        // Find user by username OR email
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with username " + username));
        
        return new SecurityUser(user);
    }
    */
    @Override
    @Cacheable(value = "auth_users", key = "#email")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("--- HIT DATABASE: Looking up user by email: " + email + " ---");
        
        // Find user by email
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        
        return new SecurityUser(user);
    }

    //TODO: Hanlde caching
    // // 2. Clear cache if user updates password/roles, forcing a fresh DB read next time
    // @CacheEvict(value = "auth_users", key = "#username")
    // public void handleUserUpdate(String username) {
    //     // Logic to update user in DB goes here
    // }

}