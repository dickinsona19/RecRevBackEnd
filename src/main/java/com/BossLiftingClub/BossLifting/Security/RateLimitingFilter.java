package com.BossLiftingClub.BossLifting.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple rate limiting filter for authentication endpoints
 * Uses sliding window algorithm with per-IP tracking
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    
    // Rate limit configuration
    private static final int MAX_REQUESTS = 5; // Max requests per window
    private static final long WINDOW_SIZE_MS = 60_000; // 1 minute window
    
    // Track requests per IP address
    private final Map<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();
    
    // Cleanup thread to remove old entries
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes
    private long lastCleanup = System.currentTimeMillis();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply rate limiting to authentication endpoints
        if (path.equals("/api/clients/login") || path.equals("/api/clients/signup") || path.equals("/api/clients/refresh")) {
            String clientIp = getClientIpAddress(request);
            RequestWindow window = requestWindows.computeIfAbsent(clientIp, k -> new RequestWindow());
            
            long currentTime = System.currentTimeMillis();
            
            // Cleanup old entries periodically
            if (currentTime - lastCleanup > CLEANUP_INTERVAL_MS) {
                cleanupOldEntries(currentTime);
                lastCleanup = currentTime;
            }
            
            // Check if within rate limit
            if (!window.isAllowed(currentTime)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
    
    private void cleanupOldEntries(long currentTime) {
        requestWindows.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getLastRequestTime() > CLEANUP_INTERVAL_MS
        );
    }
    
    /**
     * Tracks request window for a single IP address
     */
    private static class RequestWindow {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        
        public synchronized boolean isAllowed(long currentTime) {
            long windowStartTime = windowStart.get();
            
            // Reset window if it has expired
            if (currentTime - windowStartTime > WINDOW_SIZE_MS) {
                requestCount.set(0);
                windowStart.set(currentTime);
                requestCount.incrementAndGet();
                return true;
            }
            
            // Check if under limit
            int count = requestCount.incrementAndGet();
            if (count > MAX_REQUESTS) {
                requestCount.decrementAndGet();
                return false;
            }
            
            return true;
        }
        
        public long getLastRequestTime() {
            return windowStart.get();
        }
    }
}

