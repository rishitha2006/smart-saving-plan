package com.salarywise.service;

import com.salarywise.model.User;
import com.salarywise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    public String register(String name, String emailAddress, String username, String password, String address) {
        String e = emailAddress == null ? "" : emailAddress.trim().toLowerCase();
        if (name == null || name.isBlank()) return "Full name is required.";
        if (e.isBlank()) return "Email address is required.";
        if (password == null || password.length() < 6) return "Password must contain at least 6 characters.";
        if (users.existsByEmailIgnoreCase(e)) return "An account with this email already exists. Please login.";
        if (username != null && !username.isBlank() && users.existsByUsernameIgnoreCase(username.trim()))
            return "Username is already taken.";

        User u = new User();
        u.setName(name.trim());
        u.setEmail(e);
        u.setUsername(username == null || username.isBlank() ? e : username.trim());
        u.setPassword(encoder.encode(password));
        u.setAddress(address == null ? "" : address.trim());
        u.setOnboardingComplete(false);
        u.setSalaryGrowthRate(5);
        u.setInvestmentReturnRate(8);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
        return "SUCCESS";
    }

    public User login(String login, String password) {
        if (login == null || password == null) return null;
        Optional<User> op = users.findByEmailIgnoreCase(login.trim());
        if (op.isEmpty()) op = users.findByUsernameIgnoreCase(login.trim());
        if (op.isEmpty() || !encoder.matches(password, op.get().getPassword())) return null;
        return op.get();
    }
}
