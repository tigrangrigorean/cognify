package com.cognify.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance rate limit. Use shared storage before horizontal scaling. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Window(long start, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().startsWith("/api/auth/")) {
      chain.doFilter(req, res);
      return;
    }
    long now = System.currentTimeMillis();
    windows.entrySet().removeIf(e -> now - e.getValue().start() >= 60000);
    Window window =
        windows.compute(
            req.getRemoteAddr(),
            (key, old) ->
                old == null || now - old.start() >= 60000
                    ? new Window(now, 1)
                    : new Window(old.start(), old.count() + 1));
    if (window.count() > 30) {
      res.setStatus(429);
      res.setHeader("Retry-After", "60");
      res.setContentType("application/problem+json");
      res.getWriter().write("{\"status\":429,\"detail\":\"Too many authentication requests\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}
