package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserApiController {
    private final AuthService authService;

    public UserApiController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        User u = authService.findById(id);
        if (u == null || u.isDelete()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        String name = (u.getFullName() == null || u.getFullName().isBlank()) ? ("User #" + u.getId()) : u.getFullName();
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "fullName", name,
                "role", u.getRole()
        ));
    }
}
