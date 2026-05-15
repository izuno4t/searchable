package com.searchable.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page placeholder; the real dashboard is implemented in Batch D.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(final Model model) {
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageTitle", "Dashboard");
        return "home";
    }
}
