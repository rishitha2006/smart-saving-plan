package com.salarywise.controller;
import com.salarywise.model.User;
import com.salarywise.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
@Controller
public class SettingsController {
 @Autowired UserRepository users;
 @GetMapping("/settings") public String settings(HttpSession s,Model m){User u=(User)s.getAttribute("loggedInUser");if(u==null)return "redirect:/login";m.addAttribute("user",u);return "settings";}
 @PostMapping("/settings/profile") public String update(@RequestParam String name,@RequestParam String address,HttpSession s){
  User u=(User)s.getAttribute("loggedInUser");if(u==null)return "redirect:/login";u.setName(name.trim());u.setAddress(address.trim());users.save(u);s.setAttribute("loggedInUser",u);return "redirect:/settings?success";
 }
}
