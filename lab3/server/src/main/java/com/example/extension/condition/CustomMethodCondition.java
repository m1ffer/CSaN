package com.example.extension.condition;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

public class CustomMethodCondition implements RequestCondition<CustomMethodCondition> {
    private final String method;

    public CustomMethodCondition(String method) {
        this.method = method;
    }

    @Override
    public CustomMethodCondition combine(CustomMethodCondition other) {
        return new CustomMethodCondition(other.method);
    }

    @Override
    public CustomMethodCondition getMatchingCondition(HttpServletRequest request) {
        if (request.getMethod().equalsIgnoreCase(method)) {
            return this;
        }
        return null;
    }

    @Override
    public int compareTo(CustomMethodCondition other, HttpServletRequest request) {
        return 0;
    }
}
