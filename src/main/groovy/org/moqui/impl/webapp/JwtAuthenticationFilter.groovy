package org.moqui.impl.webapp

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.exceptions.JWTVerificationException
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * JWT Authentication Filter for Web Requests
 * Provides centralized JWT authentication for web pages
 */
class JwtAuthenticationFilter implements Filter {
    protected final static Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class)

    private ExecutionContextFactoryImpl ecfi

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
        this.ecfi = (ExecutionContextFactoryImpl) filterConfig.getServletContext().getAttribute("executionContextFactory")
        logger.info("JWT Authentication Filter initialized")
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request
        HttpServletResponse httpResponse = (HttpServletResponse) response

        // Skip JWT check for certain paths
        String requestPath = httpRequest.getRequestURI()
        if (shouldSkipJwtCheck(requestPath)) {
            chain.doFilter(request, response)
            return
        }

        // Extract JWT token from various sources
        String token = extractJwtToken(httpRequest)

        if (token != null) {
            try {
                // Verify and extract user info from JWT
                def userInfo = verifyJwtToken(token)
                if (userInfo.valid) {
                    // Set user context for this request
                    setUserContext(httpRequest, userInfo)
                    logger.debug("JWT authentication successful for user: ${userInfo.username}")
                } else {
                    logger.warn("Invalid JWT token: ${userInfo.message}")
                }
            } catch (Exception e) {
                logger.warn("JWT token verification failed: ${e.message}")
            }
        }

        chain.doFilter(request, response)
    }

    private boolean shouldSkipJwtCheck(String path) {
        // Skip JWT for login pages, static resources, and REST auth endpoints
        return path.startsWith("/rest/s1/auth/") ||
               path.startsWith("/static/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/libs/") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".ico")
    }

    private String extractJwtToken(HttpServletRequest request) {
        // 1. Check Authorization header
        String authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        // 2. Check cookie
        if (request.getCookies() != null) {
            for (cookie in request.getCookies()) {
                if ("jwt_token".equals(cookie.getName())) {
                    return cookie.getValue()
                }
            }
        }

        // 3. Check request parameter (for forms)
        String paramToken = request.getParameter("jwt_token")
        if (paramToken != null) {
            return paramToken
        }

        return null
    }

    private Map verifyJwtToken(String token) {
        try {
            String secret = ecfi.getSystemProperty("moqui.jwt.secret") ?: "moqui-jwt-secret-key-change-in-production-2024"
            Algorithm algorithm = Algorithm.HMAC256(secret)

            DecodedJWT jwt = JWT.require(algorithm)
                .withIssuer("moqui")
                .build()
                .verify(token)

            return [
                valid: true,
                username: jwt.getSubject(),
                merchantId: jwt.getClaim("merchantId").asString(),
                roles: jwt.getClaim("roles").asList(String.class) ?: [],
                permissions: jwt.getClaim("permissions").asList(String.class) ?: []
            ]
        } catch (JWTVerificationException e) {
            return [valid: false, message: e.message]
        }
    }

    private void setUserContext(HttpServletRequest request, Map userInfo) {
        // Set user information as request attributes for use by Moqui
        request.setAttribute("jwt_authenticated", true)
        request.setAttribute("jwt_username", userInfo.username)
        request.setAttribute("jwt_merchantId", userInfo.merchantId)
        request.setAttribute("jwt_roles", userInfo.roles)
        request.setAttribute("jwt_permissions", userInfo.permissions)
    }

    @Override
    void destroy() {
        logger.info("JWT Authentication Filter destroyed")
    }
}