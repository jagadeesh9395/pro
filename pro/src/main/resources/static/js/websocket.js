/**
 * WebSocket client for handling real-time application status updates
 */
class ApplicationWebSocket {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.subscriptions = new Map();
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 5000; // 5 seconds
        this.connect();
    }

    connect() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        
        this.stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            this.connected = true;
            this.reconnectAttempts = 0;
            
            // Resubscribe to existing subscriptions
            this.subscriptions.forEach((subscription, destination) => {
                this.subscribe(destination, subscription.callback);
            });
            
        }, (error) => {
            console.error('WebSocket connection error:', error);
            this.connected = false;
            this.reconnect();
        });
    }

    subscribe(destination, callback) {
        if (this.connected) {
            const subscription = this.stompClient.subscribe(destination, (message) => {
                const statusUpdate = JSON.parse(message.body);
                callback(statusUpdate);
                this.updateUI(statusUpdate);
            });
            
            this.subscriptions.set(destination, {
                subscription: subscription,
                callback: callback
            });
            
            return subscription;
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
            sub.subscription.unsubscribe();
        }
        this.subscriptions.delete(destination);
    }

    reconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => this.connect(), this.reconnectDelay);
        } else {
            console.error('Max reconnection attempts reached. Please refresh the page to try again.');
        }
    }

    updateUI(statusUpdate) {
        // Update the status badge
        const statusBadge = document.querySelector(`[data-application-id="${statusUpdate.applicationId}"] .status-badge`);
        if (statusBadge) {
            const statusText = this.formatStatusText(statusUpdate.newStatus);
            statusBadge.textContent = statusText;
            statusBadge.className = `status-badge status-${statusUpdate.newStatus.toLowerCase().replace('_', '-')}`;
        }

        // Show a notification
        this.showNotification(
            `Application Status Updated`, 
            `Status changed from ${this.formatStatusText(statusUpdate.oldStatus)} to ${this.formatStatusText(statusUpdate.newStatus)}`,
            this.getStatusColor(statusUpdate.newStatus)
        );

        // Update the status history
        this.updateStatusHistory(statusUpdate);
    }

    formatStatusText(status) {
        // Convert status from UPPER_SNAKE_CASE to Title Case
        if (!status) return '';
        return status.toLowerCase()
                   .split('_')
                   .map(word => word.charAt(0).toUpperCase() + word.slice(1))
                   .join(' ');
    }

    getStatusColor(status) {
        const colors = {
            'APPLIED': '#74b9ff',
            'UNDER_REVIEW': '#00b894',
            'SHORTLISTED': '#fdcb6e',
            'INTERVIEW_SCHEDULED': '#00cec9',
            'INTERVIEWING': '#0984e3',
            'OFFER_EXTENDED': '#fab1a0',
            'HIRED': '#55efc4',
            'REJECTED': '#d63031',
            'WITHDRAWN': '#636e72'
        };
        return colors[status] || '#333333';
    }

    showNotification(title, message, color) {
        // Check if Toastr is available
        if (window.toastr) {
            toastr.info(message, title);
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

    updateStatusHistory(statusUpdate) {
        // Update the status history list
        const historyList = document.querySelector(`[data-application-id="${statusUpdate.applicationId}"] .status-history`);
        if (historyList) {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            
            const formattedDate = new Date(statusUpdate.timestamp).toLocaleString();
            const statusText = this.formatStatusText(statusUpdate.newStatus);
            
            historyItem.innerHTML = `
                <span class="timestamp">${formattedDate}</span>
                <span class="status ${statusUpdate.newStatus.toLowerCase().replace('_', '-')}">
                    ${statusText}
                </span>
                ${statusUpdate.notes ? `<div class="notes">${statusUpdate.notes}</div>` : ''}
                <div class="updated-by">Updated by: ${statusUpdate.updatedBy || 'System'}</div>
            `;
            
            // Insert at the beginning of the list
            if (historyList.firstChild) {
                historyList.insertBefore(historyItem, historyList.firstChild);
            } else {
                historyList.appendChild(historyItem);
            }
        }
    }
}

// Initialize WebSocket when the document is ready
document.addEventListener('DOMContentLoaded', () => {
    // Only initialize if SockJS and Stomp are available
    if (window.SockJS && window.Stomp) {
        window.appWebSocket = new ApplicationWebSocket();
        
        // Subscribe to user-specific updates if we have a user ID
        const candidateId = document.body.dataset.candidateId;
        if (candidateId) {
            window.appWebSocket.subscribe(`/user/${candidateId}/queue/status-updates`, (update) => {
                console.log('Status update received for candidate:', update);
            });
        }
        
        // Subscribe to application-specific updates if we have an application ID
        const applicationId = document.body.dataset.applicationId;
        if (applicationId) {
            window.appWebSocket.subscribe(`/topic/application/${applicationId}/status-updates`, (update) => {
                console.log('Application status update:', update);
            });
        }
    } else {
        console.warn('SockJS and/or Stomp not available. Real-time updates disabled.');
    }
});
