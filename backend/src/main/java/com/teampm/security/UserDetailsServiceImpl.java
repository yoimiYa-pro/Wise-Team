package com.teampm.security;

import com.teampm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var u = userMapper.findByUsername(username);
        if (u == null || u.getStatus() == null || u.getStatus() == 0) {
            throw new UsernameNotFoundException(username);
        }
        return new UserPrincipal(u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole());
    }
}
