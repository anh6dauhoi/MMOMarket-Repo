package com.mmo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        // returns the Thymeleaf template at templates/customer/aboutus.html
        return "customer/aboutus";
    }
}
