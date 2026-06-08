package com.shopaccgame.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.scheduling.annotation.Scheduled;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, List<Long>> requestHistory = new ConcurrentHashMap<>();
    
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final int MAX_POST_REQUESTS_PER_MINUTE = 20; 
    private static final long WINDOW_SIZE_MS = TimeUnit.MINUTES.toMillis(1);

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupRequestHistory() {
        long now = System.currentTimeMillis();
        requestHistory.entrySet().removeIf(entry -> {
            List<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                timestamps.removeIf(time -> (now - time) > WINDOW_SIZE_MS);
                return timestamps.isEmpty();
            }
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        if (!path.startsWith("/api/") || path.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String authHeader = request.getHeader("Authorization");
        String clientId = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader : clientIp;
        
        long now = System.currentTimeMillis();
        
        boolean isWrite = "POST".equalsIgnoreCase(request.getMethod()) || 
                          "PUT".equalsIgnoreCase(request.getMethod()) || 
                          "DELETE".equalsIgnoreCase(request.getMethod());
                         
        int limit = isWrite ? MAX_POST_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_MINUTE;

        List<Long> timestamps = requestHistory.computeIfAbsent(clientId, k -> Collections.synchronizedList(new ArrayList<>()));
        
        synchronized (timestamps) {
            timestamps.removeIf(time -> (now - time) > WINDOW_SIZE_MS);
            
            if (timestamps.size() >= limit) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"Bạn đã gửi quá nhiều yêu cầu trong thời gian ngắn. Vui lòng thử lại sau ít phút.\"}");
                return;
            }
            
            timestamps.add(now);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
