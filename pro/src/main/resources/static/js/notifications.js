// Initialize when the document is ready
document.addEventListener('DOMContentLoaded', function() {
    // Only initialize if we're on a page with notifications
    if (!document.querySelector('.notification-container')) return;
    
    console.log('Initializing notification system...');
    
    // Load initial data
    loadUnreadCount();
    loadNotifications();
    
    // Connect to WebSocket
    connectWebSocket();
    
    // Poll for unread count every minute
    setInterval(loadUnreadCount, 60000);
    
    console.log('Notification system initialized');
});

// Use event delegation for dynamic elements
document.addEventListener('click', function(e) {
    const container = document.querySelector('.notification-container');
    if (!container) return;
    
    // Handle mark all as read
    if (e.target.closest('#mark-all-read')) {
        e.preventDefault();
        e.stopPropagation();
        markAllAsRead();
        return false;
    }
    
    // Handle refresh
    if (e.target.closest('#refresh-notifications')) {
        e.preventDefault();
        e.stopPropagation();
        loadNotifications();
        return false;
    }
    
    // Handle individual notification clicks
    const notificationLink = e.target.closest('.notification-item[data-id]');
    if (notificationLink) {
        const notificationId = notificationLink.getAttribute('data-id');
        markAsRead(notificationId);
    }
});

function loadUnreadCount() {
    fetch('/api/notifications/unread-count', {
        credentials: 'same-origin',
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
    })
    .then(data => {
        const badge = document.getElementById('notification-badge');
        if (badge) {
            if (data.count > 0) {
                badge.style.display = 'inline-block';
                badge.textContent = data.count;
            } else {
                badge.style.display = 'none';
            }
        }
    })
    .catch(error => console.error('Error loading unread count:', error));
}

function loadNotifications() {
    const notificationList = document.getElementById('notification-list');
    if (!notificationList) return;
    
    notificationList.innerHTML = '<li class="p-3 text-center text-muted">Loading notifications...</li>';
    
    fetch('/api/notifications', {
        credentials: 'same-origin',
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
    })
    .then(data => {
        let notifications = [];
        if (Array.isArray(data)) {
            notifications = data;
        } else if (data && Array.isArray(data.content)) {
            // Handle Spring Data Page response
            notifications = data.content;
        } else {
            console.error('Expected array of notifications but got:', data);
            notificationList.innerHTML = '<li class="p-3 text-center text-danger">Error processing notifications</li>';
            return;
        }

        if (notifications.length === 0) {
            notificationList.innerHTML = '<li class="p-3 text-center text-muted">No notifications</li>';
            return;
        }
        
        notificationList.innerHTML = notifications.map(notification => `
            <li class="notification-item p-3 border-bottom ${!notification.read ? 'bg-light' : ''}" 
                data-id="${notification.id}" 
                style="cursor: pointer;">
                <div class="d-flex justify-content-between">
                    <div class="me-3">
                        <div class="fw-medium">${notification.title || 'Notification'}</div>
                        <div class="small text-muted">${notification.message || ''}</div>
                    </div>
                    <div class="text-end">
                        <small class="text-muted">${new Date(notification.createdAt).toLocaleString()}</small>
                        ${!notification.read ? '<span class="badge bg-primary ms-2">New</span>' : ''}
                    </div>
                </div>
            </li>
        `).join('');
    })
    .catch(error => {
        console.error('Error loading notifications:', error);
        notificationList.innerHTML = '<li class="p-3 text-center text-danger">Error loading notifications</li>';
    });
}

function markAsRead(id) {
    fetch(`/api/notifications/${id}/read`, {
        method: 'POST',
        headers: {
            'X-Requested-With': 'XMLHttpRequest',
            'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]')?.content || ''
        },
        credentials: 'same-origin'
    })
    .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        loadUnreadCount();
        loadNotifications();
    })
    .catch(error => console.error('Error marking notification as read:', error));
}

function markAllAsRead() {
    console.log('Marking all notifications as read...');
    fetch('/api/notifications/mark-all-read', { 
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]')?.content || ''
        },
        credentials: 'same-origin'
    })
    .then(async response => {
        console.log('Mark all read response status:', response.status);
        const contentType = response.headers.get('content-type');
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Error response:', errorText);
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // Only try to parse as JSON if the response has content
        if (contentType && contentType.includes('application/json')) {
            return response.json();
        } else {
            console.log('No JSON response, assuming success');
            return {};
        }
    })
    .then(() => {
        console.log('Successfully marked all as read');
        loadUnreadCount();
        loadNotifications();
    })
    .catch(error => {
        console.error('Error marking all notifications as read:', error);
        alert('Failed to mark notifications as read. Please try again.');
    });
}

function connectWebSocket() {
    if (!window.StompJs) {
        console.error('STOMP client not loaded. Make sure the STOMP library is included before this script.');
        return;
    }
    
    try {
        const socket = new SockJS('/ws');
        const stompClient = window.StompJs.over(socket);
        stompClient.debug = null; // Disable debug logs

        stompClient.connect({}, function(frame) {
            console.log('WebSocket connected successfully');
            // Subscribe to user-specific notifications
            stompClient.subscribe('/user/queue/notifications', function(notification) {
                console.log('Received notification:', notification);
                loadUnreadCount();
                loadNotifications();
            });
        }, function(error) {
            console.error('WebSocket connection error:', error);
            // Retry connection after 5 seconds
            setTimeout(connectWebSocket, 5000);
        });
    } catch (error) {
        console.error('Error initializing WebSocket:', error);
    }
}
