// API base URL
const API_BASE_URL = '/api';

// Store user credentials
let currentUser = null;

// Function to encode credentials for Basic Auth
function getAuthHeader(username, password) {
    const credentials = btoa(`${username}:${password}`);
    return `Basic ${credentials}`;
}

// Function to check if user is authenticated
function isAuthenticated() {
    return currentUser !== null;
}

// Function to get current user
function getCurrentUser() {
    return currentUser;
}

// Function to login
async function login(username, password) {
    try {
        const response = await fetch(`${API_BASE_URL}/auth/user`, {
            method: 'GET',
            headers: {
                'Authorization': getAuthHeader(username, password)
            }
        });
        
        if (!response.ok) {
            throw new Error('Login failed');
        }
        
        const userData = await response.json();
        currentUser = {
            username,
            password, // Note: In a production app, you'd use a more secure method
            ...userData
        };
        
        // Store in session storage (not recommended for production)
        sessionStorage.setItem('currentUser', JSON.stringify(currentUser));
        
        return userData;
    } catch (error) {
        console.error('Login error:', error);
        throw error;
    }
}

// Function to logout
function logout() {
    currentUser = null;
    sessionStorage.removeItem('currentUser');
    window.location.href = '/';
}

// Function to get auth header for API requests
function getAuthHeaderForRequest() {
    if (!currentUser) {
        const storedUser = sessionStorage.getItem('currentUser');
        if (storedUser) {
            currentUser = JSON.parse(storedUser);
        } else {
            return {};
        }
    }
    
    return {
        'Authorization': getAuthHeader(currentUser.username, currentUser.password)
    };
}

// Initialize auth state on page load
document.addEventListener('DOMContentLoaded', () => {
    const storedUser = sessionStorage.getItem('currentUser');
    if (storedUser) {
        currentUser = JSON.parse(storedUser);
    }
});
