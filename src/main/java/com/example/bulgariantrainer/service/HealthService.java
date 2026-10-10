package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    public HealthResponse getHealth() {
        return new HealthResponse("UP", "bulgariantrainer");
    }
}
