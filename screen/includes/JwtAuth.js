/**
 * JWT Authentication Manager for Moqui Web Interface
 * Provides centralized JWT token management for frontend
 */
class JwtAuthManager {
    constructor() {
        this.tokenKey = 'jwt_token';
        this.refreshTokenKey = 'jwt_refresh_token';
        this.baseUrl = window.location.origin;
        this.apiPrefix = '/rest/s1/moqui/auth';

        // Initialize token from storage
        this.token = this.getStoredToken();
        this.refreshToken = this.getStoredRefreshToken();

        // Setup automatic token injection
        this.setupInterceptors();

        // Track forms that require JWT injection
        this.jwtForms = new Set();
        this.refreshTimerId = null;

        // Setup token refresh timer
        this.setupTokenRefresh();

        // Ensure legacy session tokens are removed from AJAX calls and JWT headers are attached
        this.setupAjaxHooks();

        // Keep HTML forms in sync with the current JWT token
        this.setupFormHooks();
    }

    /**
     * Get stored JWT token
     */
    getStoredToken() {
        return localStorage.getItem(this.tokenKey) || sessionStorage.getItem(this.tokenKey);
    }

    /**
     * Get stored refresh token
     */
    getStoredRefreshToken() {
        return localStorage.getItem(this.refreshTokenKey) || sessionStorage.getItem(this.refreshTokenKey);
    }

    /**
     * Store JWT tokens
     */
    storeTokens(accessToken, refreshToken, rememberMe = false) {
        const storage = rememberMe ? localStorage : sessionStorage;

        if (accessToken) {
            storage.setItem(this.tokenKey, accessToken);
            this.token = accessToken;
        }

        if (refreshToken) {
            storage.setItem(this.refreshTokenKey, refreshToken);
            this.refreshToken = refreshToken;
        }

        // Also set as cookie for server-side filter
        if (accessToken) {
            document.cookie = `jwt_token=${accessToken}; path=/; SameSite=Strict`;
        }

        this.dispatchTokenUpdate(this.token);
        this.setupTokenRefresh();
    }

    /**
     * Clear stored tokens
     */
    clearTokens() {
        localStorage.removeItem(this.tokenKey);
        localStorage.removeItem(this.refreshTokenKey);
        sessionStorage.removeItem(this.tokenKey);
        sessionStorage.removeItem(this.refreshTokenKey);

        // Clear cookie
        document.cookie = 'jwt_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';

        this.token = null;
        this.refreshToken = null;

        this.dispatchTokenUpdate(null);
        if (this.refreshTimerId) {
            clearTimeout(this.refreshTimerId);
            this.refreshTimerId = null;
        }
    }

    /**
     * Check if user is authenticated
     */
    isAuthenticated() {
        return !!this.token;
    }

    /**
     * Login with username and password
     */
    async login(username, password, merchantId = 'DEMO_MERCHANT', rememberMe = false) {
        try {
            const response = await fetch(`${this.baseUrl}${this.apiPrefix}/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password,
                    merchantId: merchantId
                })
            });

            const data = await response.json();

            if (data.success) {
                this.storeTokens(data.accessToken, data.refreshToken, rememberMe);
                console.log('JWT login successful');
                return { success: true, data: data };
            } else {
                console.error('JWT login failed:', data.message);
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('JWT login error:', error);
            return { success: false, message: 'Network error during login' };
        }
    }

    /**
     * Logout user
     */
    async logout() {
        try {
            if (this.token) {
                await fetch(`${this.baseUrl}${this.apiPrefix}/logout`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${this.token}`,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ token: this.token })
                });
            }
        } catch (error) {
            console.warn('Logout request failed:', error);
        } finally {
            this.clearTokens();
            window.location.reload(); // Refresh page to clear any cached data
        }
    }

    /**
     * Refresh access token using refresh token
     */
    async refreshAccessToken() {
        if (!this.refreshToken) {
            console.warn('No refresh token available');
            return false;
        }

        try {
            const response = await fetch(`${this.baseUrl}${this.apiPrefix}/refresh`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    refreshToken: this.refreshToken
                })
            });

            const data = await response.json();

            if (data.success) {
                this.storeTokens(data.accessToken, this.refreshToken);
                console.log('JWT token refreshed successfully');
                return true;
            } else {
                console.error('JWT token refresh failed:', data.message);
                this.clearTokens();
                return false;
            }
        } catch (error) {
            console.error('JWT token refresh error:', error);
            this.clearTokens();
            return false;
        }
    }

    /**
     * Setup automatic token injection for all requests
     */
    setupInterceptors() {
        // Intercept XMLHttpRequest
        const originalXHROpen = XMLHttpRequest.prototype.open;
        const originalXHRSend = XMLHttpRequest.prototype.send;
        const self = this;

        XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
            this._jwtUrl = url;
            return originalXHROpen.apply(this, arguments);
        };

        XMLHttpRequest.prototype.send = function(data) {
            if (self.token && this._jwtUrl && !this._jwtUrl.startsWith('http') &&
                !this._jwtUrl.includes('/auth/')) {
                this.setRequestHeader('Authorization', `Bearer ${self.token}`);
            }
            return originalXHRSend.apply(this, arguments);
        };

        // Intercept fetch requests
        const originalFetch = window.fetch;
        window.fetch = function(url, options = {}) {
            if (self.token && typeof url === 'string' && !url.startsWith('http') &&
                !url.includes('/auth/')) {
                options.headers = options.headers || {};
                options.headers['Authorization'] = `Bearer ${self.token}`;
            }
            return originalFetch.apply(this, arguments);
        };
    }

    /**
     * Setup automatic token refresh
     */
    setupTokenRefresh() {
        if (this.refreshTimerId) {
            clearTimeout(this.refreshTimerId);
            this.refreshTimerId = null;
        }

        if (!this.token) return;

        try {
            // Decode JWT to get expiration
            const payload = JSON.parse(atob(this.token.split('.')[1]));
            const exp = payload.exp * 1000; // Convert to milliseconds
            const now = Date.now();
            const timeUntilExpiry = exp - now;
            const refreshTime = timeUntilExpiry - (5 * 60 * 1000); // Refresh 5 minutes before expiry

            if (refreshTime > 0) {
                this.refreshTimerId = setTimeout(() => {
                    this.refreshAccessToken().then(success => {
                        if (success) {
                            this.setupTokenRefresh(); // Setup next refresh
                        }
                    });
                }, refreshTime);
            } else {
                // Token is already expired or about to expire
                this.refreshAccessToken();
            }
        } catch (error) {
            console.warn('Failed to parse JWT token for auto-refresh:', error);
        }
    }


    setupAjaxHooks() {
        if (this.ajaxHooksInitialized) return;
        if (typeof window === 'undefined' || !window.jQuery) return;

        const self = this;
        const $doc = window.jQuery(document);

        $doc.on('ajaxSend.jwtAuth', function(event, jqXHR, settings) {
            const headers = (typeof moqui !== 'undefined' && typeof moqui.getAuthHeaders === 'function') ? moqui.getAuthHeaders() : {};
            if (headers && jqXHR && typeof jqXHR.setRequestHeader === 'function') {
                Object.keys(headers).forEach(function(key) {
                    jqXHR.setRequestHeader(key, headers[key]);
                });
            }

            if (!settings || typeof settings.data === 'undefined' || settings.data === null) return;

            if (typeof settings.data === 'string') {
                settings.data = self.stripLegacySessionToken(settings.data);
            } else if (typeof window.FormData !== 'undefined' && settings.data instanceof window.FormData) {
                settings.data.delete && settings.data.delete('moquiSessionToken');
                settings.data.delete && settings.data.delete('SessionToken');
            } else if (typeof settings.data === 'object') {
                delete settings.data.moquiSessionToken;
                delete settings.data.SessionToken;
            }
        });

        $doc.on('ajaxSuccess.jwtAuth', function(event, jqXHR) {
            if (typeof moqui !== 'undefined' && typeof moqui.extractAndSaveTokensFromResponse === 'function') {
                moqui.extractAndSaveTokensFromResponse(jqXHR);
            }
        });

        this.ajaxHooksInitialized = true;
    }

    stripLegacySessionToken(data) {
        if (typeof data !== 'string' || !data.length) return data;
        const parts = data.split('&').filter(function(part) {
            const key = part.split('=')[0];
            return key !== 'moquiSessionToken' && key !== 'SessionToken' && key.length > 0;
        });
        return parts.join('&');
    }

    setupFormHooks() {
        if (this.formHooksInitialized) return;
        if (typeof document === 'undefined') return;

        const initialiseForms = () => {
            if (typeof document === 'undefined') return;
            if (typeof document.querySelectorAll === 'function') {
                document.querySelectorAll('form').forEach(form => this.wireForm(form));
            }
            this.updateAllJwtForms();
        };

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => initialiseForms(), { once: true });
        } else {
            initialiseForms();
        }

        if (typeof MutationObserver !== 'undefined' && document.body) {
            this.formObserver = new MutationObserver(mutations => {
                mutations.forEach(mutation => {
                    mutation.addedNodes.forEach(node => {
                        if (!(node instanceof Element)) return;
                        if (node.tagName === 'FORM') {
                            this.wireForm(node);
                        } else if (typeof node.querySelectorAll === 'function') {
                            node.querySelectorAll('form').forEach(form => this.wireForm(form));
                        }
                    });
                });
            });
            this.formObserver.observe(document.body, { childList: true, subtree: true });
        }

        this.formHooksInitialized = true;
    }

    wireForm(form) {
        if (!form || form.getAttribute('data-jwt-wired') === 'true') return;

        form.setAttribute('data-jwt-wired', 'true');
        if (!this.jwtForms) this.jwtForms = new Set();
        this.jwtForms.add(form);
        this.removeLegacySessionTokens(form);
        this.ensureJwtInput(form);

        form.addEventListener('submit', () => {
            this.removeLegacySessionTokens(form);
            this.ensureJwtInput(form);
            this.updateAllJwtForms();
        });
    }

    ensureJwtInput(form) {
        if (!form) return null;
        let input = form.querySelector('input[name="jwt_token"][data-jwt-managed="true"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'jwt_token';
            input.setAttribute('data-jwt-managed', 'true');
            form.appendChild(input);
        }
        const token = this.token || this.getStoredToken();
        input.value = token || '';
        return input;
    }

    removeLegacySessionTokens(form) {
        if (!form || typeof form.querySelectorAll !== 'function') return;
        const legacy = form.querySelectorAll('input[name="moquiSessionToken"], input[name="SessionToken"]');
        legacy.forEach(field => {
            if (field && field.parentNode) {
                field.parentNode.removeChild(field);
            }
        });
    }

    updateAllJwtForms() {
        if (!this.jwtForms || typeof document === 'undefined') return;
        const token = this.token || this.getStoredToken();
        const stale = [];
        this.jwtForms.forEach(form => {
            if (!form || !document.body || !document.body.contains(form)) {
                stale.push(form);
                return;
            }
            this.removeLegacySessionTokens(form);
            const input = this.ensureJwtInput(form);
            if (input) input.value = token || '';
        });
        stale.forEach(form => this.jwtForms.delete(form));
    }

    dispatchTokenUpdate(token) {
        this.token = token || null;
        this.updateAllJwtForms();
        if (typeof document === 'undefined') return;
        try {
            document.dispatchEvent(new CustomEvent('moqui-jwt-updated', { detail: { token: this.token } }));
        } catch (error) {
            if (typeof document.createEvent === 'function') {
                const evt = document.createEvent('Event');
                evt.initEvent('moqui-jwt-updated', true, true);
                evt.detail = { token: this.token };
                document.dispatchEvent(evt);
            }
        }
    }

    /**
     * Show login modal/form
     */
    showLoginForm() {
        // Create a simple login modal
        const modal = document.createElement('div');
        modal.id = 'jwt-login-modal';
        modal.innerHTML = `
            <div style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 10000; display: flex; justify-content: center; align-items: center;">
                <div style="background: white; padding: 20px; border-radius: 8px; max-width: 400px; width: 90%;">
                    <h3>请登录</h3>
                    <form id="jwt-login-form">
                        <div style="margin-bottom: 10px;">
                            <label>用户名:</label>
                            <input type="text" id="jwt-username" required style="width: 100%; padding: 5px; margin-top: 5px;">
                        </div>
                        <div style="margin-bottom: 10px;">
                            <label>密码:</label>
                            <input type="password" id="jwt-password" required style="width: 100%; padding: 5px; margin-top: 5px;">
                        </div>
                        <div style="margin-bottom: 15px;">
                            <label>
                                <input type="checkbox" id="jwt-remember"> 记住登录状态
                            </label>
                        </div>
                        <div style="text-align: right;">
                            <button type="button" onclick="this.closest('#jwt-login-modal').remove()">取消</button>
                            <button type="submit" style="margin-left: 10px;">登录</button>
                        </div>
                    </form>
                    <div id="jwt-login-error" style="color: red; margin-top: 10px; display: none;"></div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Handle form submission
        document.getElementById('jwt-login-form').addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('jwt-username').value;
            const password = document.getElementById('jwt-password').value;
            const remember = document.getElementById('jwt-remember').checked;

            const result = await this.login(username, password, 'DEMO_MERCHANT', remember);

            if (result.success) {
                modal.remove();
                window.location.reload(); // Refresh to use new token
            } else {
                const errorDiv = document.getElementById('jwt-login-error');
                errorDiv.textContent = result.message || '登录失败';
                errorDiv.style.display = 'block';
            }
        });
    }

    /**
     * Handle authentication required scenarios
     */
    requireAuth() {
        if (!this.isAuthenticated()) {
            this.showLoginForm();
            return false;
        }
        return true;
    }
}

// Global JWT manager instance
window.jwtAuth = new JwtAuthManager();

// Auto-show login form if not authenticated on protected pages
document.addEventListener('DOMContentLoaded', function() {
    // Check if this is a protected page that requires authentication
    const currentPath = window.location.pathname;
    const protectedPaths = ['/marketplace/', '/mcp/'];

    const isProtectedPage = protectedPaths.some(path => currentPath.includes(path));

    if (isProtectedPage && !window.jwtAuth.isAuthenticated()) {
        // Check if the page shows authorization errors
        const hasAuthError = document.body.textContent.includes('not authorized') ||
                            document.body.textContent.includes('Forbidden');

        if (hasAuthError) {
            window.jwtAuth.showLoginForm();
        }
    }
});

console.log('JWT Authentication Manager loaded successfully');