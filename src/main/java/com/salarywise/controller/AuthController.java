package com.salarywise.controller;

import com.salarywise.model.User;
import com.salarywise.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    @Autowired AuthService auth;

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/signup")
    public String signup(HttpSession s) {
        if(s.getAttribute("loggedInUser") != null) return "redirect:/dashboard";
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String name,
                         @RequestParam String email,
                         @RequestParam(required = false) String username,
                         @RequestParam String password,
                         @RequestParam(required = false) String address,
                         HttpSession s, Model m) {
        String result = auth.register(name, email, username, password, address);
        if (!"SUCCESS".equals(result)) {
            m.addAttribute("error", result);
            return "signup";
        }

        // First registration goes straight into the personalized financial questionnaire.
        User u = auth.login(email, password);
        s.setAttribute("loggedInUser", u);
        return "redirect:/onboarding";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession s) {
        if(s.getAttribute("loggedInUser") != null) return "redirect:/dashboard";
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String login,
                        @RequestParam String password,
                        HttpSession s, Model m) {
        User u = auth.login(login, password);
        if (u == null) {
            m.addAttribute("error", "Invalid email/username or password.");
            return "login";
        }
        s.setAttribute("loggedInUser", u);
        return u.isOnboardingComplete() ? "redirect:/dashboard" : "redirect:/onboarding";
    }

    @GetMapping("/logout")
    public String logout(HttpSession s) {
        s.invalidate();
        return "redirect:/";
    }
}
