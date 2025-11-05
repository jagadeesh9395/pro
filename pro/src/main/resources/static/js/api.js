// API base URL
const API_BASE_URL = '/api';

// Helper function to get current user from session storage
function getCurrentUser() {
    const user = sessionStorage.getItem('currentUser');
    return user ? JSON.parse(user) : null;
}

// Helper function to get auth headers
function getAuthHeaders() {
    const user = getCurrentUser();
    if (!user) {
        return {};
    }
    return {
        'Authorization': 'Basic ' + btoa(`${user.username}:${user.password}`)
    };
}

// Wrapper for authenticated fetch
async function authFetch(url, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...getAuthHeaders(),
        ...(options.headers || {})
    };

    const response = await fetch(`${API_BASE_URL}${url}`, {
        ...options,
        headers
    });

    if (response.status === 401) {
        // Unauthorized - redirect to login
        window.location.href = '/auth/login';
        return;
    }

    return response;
}

// Check if user is authenticated
function isAuthenticated() {
    return !!getCurrentUser();
}

// Redirect to login if not authenticated
function requireAuth() {
    if (!isAuthenticated()) {
        window.location.href = '/auth/login';
        return false;
    }
    return true;
}

// Logout function
function logout() {
    sessionStorage.removeItem('currentUser');
    window.location.href = '/auth/login';
}

export { authFetch, isAuthenticated, requireAuth, logout, getCurrentUser };
