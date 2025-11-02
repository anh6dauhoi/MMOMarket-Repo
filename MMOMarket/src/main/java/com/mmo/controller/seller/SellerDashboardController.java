package com.mmo.controller.seller;

import com.mmo.entity.User;
import com.mmo.service.UserService;
import com.mmo.service.SellerDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/seller")
public class SellerDashboardController {

    @Autowired
    private UserService userService;

    @Autowired
    private SellerDashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication,
                            @RequestParam(defaultValue = "30") int days) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);

        // Lấy thống kê dashboard
        Map<String, Object> stats = dashboardService.getDashboardStats(seller.getId(), days);

        model.addAttribute("seller", seller);
        model.addAttribute("stats", stats);
        model.addAttribute("selectedDays", days);
        model.addAttribute("activePage", "dashboard");

        return "seller/dashboard";
    }
}