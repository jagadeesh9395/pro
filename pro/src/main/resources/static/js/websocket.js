/**
 * WebSocket client for handling real-time application status updates and notifications
 */
class ApplicationWebSocket {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.subscriptions = new Map();
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 3000; // Start with 3 seconds
        this.notificationCallbacks = [];
        this.connect();
    }

    connect() {
        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = () => {}; // Disable debug logging
            
            this.stompClient.connect({}, (frame) => {
                console.log('WebSocket connected successfully');
                this.connected = true;
                this.reconnectAttempts = 0;
                
                // Resubscribe to existing subscriptions
                this.subscriptions.forEach((subscription, destination) => {
                    this.subscribe(destination, subscription.callback);
                });
                
                // Notify all registered callbacks of successful connection
                this.notificationCallbacks.forEach(callback => {
                    callback({ type: 'connected' });
                });
                
            }, (error) => {
                console.error('WebSocket connection error:', error);
                this.connected = false;
                this.reconnect();
            });
            
            // Handle connection close
            socket.onclose = () => {
                this.connected = false;
                console.log('WebSocket connection closed');
                this.reconnect();
            };
            
        } catch (error) {
            console.error('Error initializing WebSocket:', error);
            this.reconnect();
        }
    }

    subscribe(destination, callback) {
        if (this.connected && this.stompClient) {
            try {
                const subscription = this.stompClient.subscribe(destination, (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log('Received message on', destination, data);
                        callback(data);
                        this.notifyCallbacks(data);
                    } catch (e) {
                        console.error('Error processing message:', e);
                    }
                });
                
                this.subscriptions.set(destination, {
                    subscription: subscription,
                    callback: callback
                });
                
                console.log('Subscribed to', destination);
                return subscription;
            } catch (error) {
                console.error('Error subscribing to', destination, error);
                return null;
            }
        } else {
            // Queue the subscription for when we reconnect
            this.subscriptions.set(destination, { callback: callback });
            if (this.reconnectAttempts === 0) {
                this.connect();
            }
            return null;
        }
    }

    unsubscribe(destination) {
        const sub = this.subscriptions.get(destination);
        if (sub && sub.subscription) {
            try {
                sub.subscription.unsubscribe();
                console.log('Unsubscribed from', destination);
            } catch (error) {
                console.error('Error unsubscribing from', destination, error);
            }
        }
        this.subscriptions.delete(destination);
    }

    reconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            const delay = Math.min(this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts), 30000); // Max 30s delay
            console.log(`Attempting to reconnect in ${delay/1000} seconds (${this.reconnectAttempts + 1}/${this.maxReconnectAttempts})...`);
            
            // Notify callbacks of reconnection attempt
            this.notificationCallbacks.forEach(callback => {
                callback({ 
                    type: 'reconnecting', 
                    attempt: this.reconnectAttempts + 1,
                    maxAttempts: this.maxReconnectAttempts,
                    nextAttemptIn: delay/1000
                });
            });
            
            setTimeout(() => {
                this.reconnectAttempts++;
                this.connect();
            }, delay);
        } else {
            console.error('Max reconnection attempts reached. Please refresh the page to try again.');
            this.notificationCallbacks.forEach(callback => {
                callback({ 
                    type: 'disconnected',
                    reason: 'Max reconnection attempts reached',
                    timestamp: new Date().toISOString()
                });
            });
        }
    }

    onNotification(callback) {
        if (typeof callback === 'function') {
            this.notificationCallbacks.push(callback);
        }
        return this;
    }

    offNotification(callback) {
        const index = this.notificationCallbacks.indexOf(callback);
        if (index > -1) {
            this.notificationCallbacks.splice(index, 1);
        }
        return this;
    }

    notifyCallbacks(data) {
        this.notificationCallbacks.forEach(callback => {
            try {
                callback({ type: 'notification', data });
            } catch (e) {
                console.error('Error in notification callback:', e);
            }
        });
    }
}

// Initialize WebSocket when the document is ready
document.addEventListener('DOMContentLoaded', () => {
    // Only initialize if SockJS and Stomp are available
    if (window.SockJS && window.Stomp) {
        // Initialize WebSocket connection
        window.appWebSocket = new ApplicationWebSocket();
        
        // Get user information from the page
        const candidateId = document.body.dataset.candidateId;
        const recruiterId = document.body.dataset.recruiterId;
        const applicationId = document.body.dataset.applicationId;
        
        // Subscribe to user-specific notifications
        if (candidateId) {
            const userQueue = `/user/${candidateId}/queue/status-updates`;
            window.appWebSocket.subscribe(userQueue, (update) => {
                console.log('Status update for candidate:', update);
                showStatusUpdateNotification(update);
                
                // Update the UI if we're on a relevant page
                updateApplicationStatusUI(update);
            });
        }
        
        // Subscribe to recruiter-specific notifications
        if (recruiterId) {
            const recruiterQueue = `/user/${recruiterId}/queue/notifications`;
            window.appWebSocket.subscribe(recruiterQueue, (notification) => {
                console.log('Notification for recruiter:', notification);
                showRecruiterNotification(notification);
            });
        }
        
        // Subscribe to application-specific updates if we have an application ID
        if (applicationId) {
            const appTopic = `/topic/application/${applicationId}/status-updates`;
            window.appWebSocket.subscribe(appTopic, (update) => {
                console.log('Application status update:', update);
                showStatusUpdateNotification(update);
                updateApplicationStatusUI(update);
            });
        }
        
        // Handle connection status changes
        window.appWebSocket.onNotification((event) => {
            console.log('WebSocket notification:', event);
            
            if (event.type === 'connected') {
                console.log('Successfully connected to WebSocket server');
                // Refresh notifications on connect
                if (window.refreshNotifications) {
                    window.refreshNotifications();
                }
            } else if (event.type === 'reconnecting') {
                console.log(`Reconnecting... (${event.attempt}/${event.maxAttempts})`);
            } else if (event.type === 'disconnected') {
                console.error('Disconnected from WebSocket server');
            } else if (event.type === 'notification') {
                console.log('Received notification:', event.data);
            }
        });
        
        // Expose refresh function
        window.refreshNotifications = function() {
            if (window.loadNotifications) {
                window.loadNotifications();
            }
            if (window.loadUnreadCount) {
                window.loadUnreadCount();
            }
        };
        
    } else {
        console.warn('SockJS and/or Stomp not available. Real-time updates disabled.');
    }
});

// Helper functions for showing notifications
function showStatusUpdateNotification(update) {
    const statusText = formatStatusText(update.newStatus);
    const title = 'Application Status Update';
    const message = `Your application status has been updated to: ${statusText}`;
    
    showToast(title, message, 'info');
    
    // Update the notification badge if it exists
    const badge = document.getElementById('notification-badge');
    if (badge) {
        const currentCount = parseInt(badge.textContent) || 0;
        badge.textContent = currentCount + 1;
        badge.style.display = 'block';
    }
    
    // Play notification sound if available
    playNotificationSound();
}

function showRecruiterNotification(notification) {
    const title = notification.title || 'New Notification';
    const message = notification.message || 'You have a new notification';
    
    showToast(title, message, 'info');
    
    // Update the notification badge if it exists
    const badge = document.getElementById('notification-badge');
    if (badge) {
        const currentCount = parseInt(badge.textContent) || 0;
        badge.textContent = currentCount + 1;
        badge.style.display = 'block';
    }
    
    // Play notification sound if available
    playNotificationSound();
}

function updateApplicationStatusUI(update) {
    // Update status badge if we're on the application details page
    const statusBadge = document.querySelector(`[data-application-id="${update.applicationId}"] .status-badge`);
    if (statusBadge) {
        const statusText = formatStatusText(update.newStatus);
        statusBadge.textContent = statusText;
        statusBadge.className = `status-badge status-${update.newStatus.toLowerCase().replace('_', '-')}`;
    }
    
    // Update status history if we're on the application details page
    updateStatusHistory(update);
}

function updateStatusHistory(update) {
    const historyList = document.querySelector(`[data-application-id="${update.applicationId}"] .status-history`);
    if (historyList) {
        const historyItem = document.createElement('div');
        historyItem.className = 'history-item';
        
        const formattedDate = new Date(update.timestamp || new Date()).toLocaleString();
        const statusText = formatStatusText(update.newStatus);
        
        historyItem.innerHTML = `
            <span class="timestamp">${formattedDate}</span>
            <span class="status ${update.newStatus.toLowerCase().replace('_', '-')}">
                ${statusText}
            </span>
            ${update.notes ? `<div class="notes">${update.notes}</div>` : ''}
            <div class="updated-by">Updated by: ${update.updatedBy || 'System'}</div>
        `;
        
        // Insert at the beginning of the list
        if (historyList.firstChild) {
            historyList.insertBefore(historyItem, historyList.firstChild);
        } else {
            historyList.appendChild(historyItem);
        }
    }
}

function formatStatusText(status) {
    if (!status) return '';
    return status.toLowerCase()
               .split('_')
               .map(word => word.charAt(0).toUpperCase() + word.slice(1))
               .join(' ');
}

function showToast(title, message, type = 'info') {
    // Check if Toastr is available
    if (window.toastr) {
        toastr[type](message, title);
    } 
    // Fallback to browser notification if available
    else if (window.Notification && Notification.permission === 'granted') {
        new Notification(title, { body: message });
    }
    // Fallback to console
    else {
        console.log(`[${title}] ${message}`);
    }
}

function playNotificationSound() {
    // You can add a subtle notification sound here if desired
    // Example: new Audio('/sounds/notification.mp3').play().catch(() => {});
}
