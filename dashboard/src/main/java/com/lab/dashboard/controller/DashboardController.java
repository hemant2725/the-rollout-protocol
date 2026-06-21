package com.lab.dashboard.controller;

import com.lab.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("title", "Progressive Delivery Lab - Mission Control");
        return "dashboard";
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> apiData() {
        return dashboardService.getDashboardData();
    }
}
