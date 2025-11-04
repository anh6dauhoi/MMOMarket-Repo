package com.mmo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FaviconController {

    // Many browsers still request /favicon.ico. Forward it to our existing logo to avoid 404 noise.
    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/images/logo.png";
    }
}

