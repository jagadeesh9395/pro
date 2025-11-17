// Wait for the DOM to be fully loaded
document.addEventListener('DOMContentLoaded', function() {
    // Modal elements and instances
    let educationModal = null;
    let deleteModal = null;
    let educationModalElement = null;
    let deleteModalElement = null;
    let addEducationBtn = null;
    
    // Function to initialize modals
    function initializeModals() {
        // Get modal elements
        educationModalElement = document.getElementById('educationModal');
        deleteModalElement = document.getElementById('deleteEducationModal');
        
        if (!educationModalElement) {
            console.error('Education modal element not found');
            return false;
        }
        
        if (!deleteModalElement) {
            console.error('Delete modal element not found');
            return false;
        }
        
        // Initialize education modal
        if (educationModalElement) {
            try {
                educationModal = new bootstrap.Modal(educationModalElement, {
                    backdrop: true,
                    keyboard: true,
                    focus: true
                });
            } catch (e) {
                console.error('Failed to initialize education modal:', e);
            }
        }
        
        // Initialize delete modal
        if (deleteModalElement) {
            try {
                deleteModal = new bootstrap.Modal(deleteModalElement, {
                    backdrop: true,
                    keyboard: true,
                    focus: true
                });
            } catch (e) {
                console.error('Failed to initialize delete modal:', e);
            }
        }
    }
    
    // Initialize modals and get elements
    initializeModals();
    
    // Get the Add Education button by ID
    addEducationBtn = document.getElementById('addEducationBtn');
    
    // Set up event listeners
    setupEventListeners();
    
    // Function to show a modal
    function showModal(modal, modalElement) {
        if (!modalElement) {
            console.error('Modal element not found');
            return;
        }
        
        try {
            // Try to get existing instance first
            const existingModal = bootstrap.Modal.getInstance(modalElement);
            if (existingModal) {
                existingModal.show();
            } else if (modal) {
                // If no instance exists but we have a modal, show it
                modal.show();
            } else {
                // Last resort: create a new modal instance
                new bootstrap.Modal(modalElement, {
                    backdrop: true,
                    keyboard: true,
                    focus: true
                }).show();
            }
        } catch (e) {
            console.error('Error showing modal:', e);
        }
    }
    
    // Function to hide a modal
    function hideModal(modal, modalElement) {
        if (!modalElement) {
            console.error('Modal element not found');
            return;
        }
        
        try {
            // Try to get existing instance first
            const existingModal = bootstrap.Modal.getInstance(modalElement);
            if (existingModal) {
                existingModal.hide();
            } else if (modal) {
                // If no instance exists but we have a modal, hide it
                modal.hide();
            }
        } catch (e) {
            console.error('Error hiding modal:', e);
        }
    }
    
    // Setup event listeners
    function setupEventListeners() {
        // Add click handler for Add Education button
        if (addEducationBtn) {
            addEducationBtn.addEventListener('click', function(e) {
                e.preventDefault();
                
                const title = document.getElementById('educationModalLabel');
                const idInput = document.getElementById('educationId');
                const form = document.getElementById('educationForm');
                
                if (title) title.textContent = 'Add Education';
                if (idInput) idInput.value = '';
                if (form) form.reset();
                
                // Initialize the currently studying state
                const endDateInput = document.getElementById('endDate');
                const currentlyStudyingCheckbox = document.getElementById('currentlyStudying');
                if (endDateInput && currentlyStudyingCheckbox) {
                    endDateInput.disabled = currentlyStudyingCheckbox.checked;
                }
                
                showModal(educationModal, educationModalElement);
            });
        } else {
            console.warn('Add Education button not found');
        }
        
        // Setup currently studying checkbox - using event delegation for dynamic elements
        document.addEventListener('change', function(e) {
            if (e.target && e.target.id === 'currentlyStudying') {
                const endDateInput = document.getElementById('endDate');
                if (endDateInput) {
                    endDateInput.disabled = e.target.checked;
                    if (e.target.checked) {
                        endDateInput.value = '';
                    }
                }
            }
        });
    }
    
    // Initialize end date state
    const currentlyStudyingCheckbox = document.getElementById('currentlyStudying');
    const endDateInput = document.getElementById('endDate');
    if (currentlyStudyingCheckbox && endDateInput) {
        endDateInput.disabled = currentlyStudyingCheckbox.checked;
    }
    
    // Load education data when the page loads
    loadEducationData();

    // Helper function to get headers with CSRF token
    function getHeaders() {
        // Get CSRF token from meta tag in the HTML head
        const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        
        const headers = new Headers({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        });
        
        // Add CSRF token if available
        if (token && header) {
            headers.append(header, token);
        }
        
        return headers;
    }

    // Load education data
    function loadEducationData() {
        fetch('/api/education', {
            method: 'GET',
            headers: getHeaders()
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load education data');
            }
            return response.json();
        })
        .then(educationList => {
            const educationListContainer = document.getElementById('educationList');
            const noEducationMessage = document.getElementById('noEducationMessage');
            
            if (educationList && educationList.length > 0) {
                if (noEducationMessage) noEducationMessage.classList.add('d-none');
                if (educationListContainer) educationListContainer.innerHTML = '';
                
                educationList.forEach(edu => {
                    const educationItem = createEducationItem(edu);
                    educationListContainer.appendChild(educationItem);
                });
                
                // Add event listeners for edit and delete buttons
                addEducationEventListeners();
            } else {
                noEducationMessage.classList.remove('d-none');
            }
        })
        .catch(error => {
            console.error('Error loading education data:', error);
            showAlert('Failed to load education data. Please refresh the page.', 'danger');
        });
    }
    
    // Create education item HTML with field labels and matching experience card style
    function createEducationItem(education) {
        const formatDate = (dateString) => {
            if (!dateString) return 'Not specified';
            return new Date(dateString).toLocaleDateString('en-US', { 
                year: 'numeric', 
                month: 'long'
            });
        };
        
        const startDate = formatDate(education.startDate);
        const endDate = education.currentlyStudying ? 'Present' : formatDate(education.endDate);
        const duration = `${startDate} - ${endDate}`;
        
        // Calculate duration in years and months
        const getDuration = (start, end) => {
            if (!start) return '';
            const startDate = new Date(start);
            const endDate = end === 'Present' ? new Date() : new Date(end);
            
            let months = (endDate.getFullYear() - startDate.getFullYear()) * 12;
            months -= startDate.getMonth();
            months += endDate.getMonth();
            
            const years = Math.floor(months / 12);
            const remainingMonths = months % 12;
            
            let duration = [];
            if (years > 0) duration.push(`${years} ${years === 1 ? 'yr' : 'yrs'}`);
            if (remainingMonths > 0) duration.push(`${remainingMonths} ${remainingMonths === 1 ? 'mo' : 'mos'}`);
            
            return duration.length > 0 ? `• ${duration.join(' ')}` : '';
        };
        
        const durationText = getDuration(education.startDate, education.currentlyStudying ? 'Present' : education.endDate);
        
        const educationItem = document.createElement('div');
        educationItem.className = 'card education-card mb-4 border-0 shadow-sm';
        educationItem.setAttribute('data-id', education.id);
        
        educationItem.innerHTML = `
            <div class="card-body p-4">
                <div class="d-flex justify-content-between align-items-start">
                    <div class="w-100">
                        <!-- Header with Degree and Institution -->
                        <div class="d-flex justify-content-between align-items-start mb-3">
                            <div>
                                <h4 class="mb-1 fw-bold">${education.degree || 'No Degree Specified'}</h4>
                                <div class="d-flex align-items-center flex-wrap">
                                    <span class="text-primary fw-medium me-3">
                                        <i class="bi bi-building me-1"></i>${education.institution || 'Not specified'}
                                    </span>
                                    <span class="text-muted small">
                                        <i class="bi bi-calendar3 me-1"></i>${duration} ${durationText}
                                    </span>
                                </div>
                            </div>
                            <div class="btn-group">
                                <button type="button" class="btn btn-sm btn-outline-primary edit-education" 
                                    data-id="${education.id}" title="Edit">
                                    <i class="bi bi-pencil"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-outline-danger delete-education" 
                                    data-id="${education.id}" title="Delete">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                        </div>
                        
                        <!-- Education Details -->
                        <div class="row g-3 mb-3">
                            <div class="col-md-6">
                                <div class="d-flex align-items-center text-muted">
                                    <span class="bg-light rounded-circle p-2 me-2">
                                        <i class="bi bi-book text-primary"></i>
                                    </span>
                                    <div>
                                        <div class="small text-muted">Field of Study</div>
                                        <div class="fw-medium">${education.fieldOfStudy || 'Not specified'}</div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-md-6">
                                <div class="d-flex align-items-center text-muted">
                                    <span class="bg-light rounded-circle p-2 me-2">
                                        <i class="bi ${education.currentlyStudying ? 'bi-check-circle' : 'bi-calendar-check'} text-primary"></i>
                                    </span>
                                    <div>
                                        <div class="small text-muted">${education.currentlyStudying ? 'Currently Studying' : 'Completion Status'}</div>
                                        <div class="fw-medium">${education.currentlyStudying ? 'In Progress' : 'Completed'}</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Description -->
                        ${education.description ? `
                        <div class="bg-light p-3 rounded-3">
                            <h6 class="text-muted mb-2">
                                <i class="bi bi-card-text text-primary me-2"></i>Description
                            </h6>
                            <div class="ms-3">
                                <p class="mb-0">${education.description.replace(/\n/g, '<br>')}</p>
                            </div>
                        </div>` : ''}
                    </div>
                </div>
            </div>
            
            <style>
                .education-card {
                    transition: transform 0.2s ease, box-shadow 0.2s ease;
                    border-left: 4px solid #6f42c1 !important;
                }
                .education-card:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1) !important;
                }
                .education-card .btn-group .btn {
                    opacity: 0.7;
                    transition: opacity 0.2s ease;
                }
                .education-card:hover .btn-group .btn {
                    opacity: 1;
                }
            </style>
        `;
        
        return educationItem;
    }
    
    // Add event listeners for edit and delete buttons
    function addEducationEventListeners() {
        // Handle edit education
        document.querySelectorAll('.edit-education').forEach(button => {
            button.addEventListener('click', function() {
                const educationId = this.getAttribute('data-id');
                fetch(`/api/education/${educationId}`, {
                    headers: getHeaders()
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Failed to fetch education details');
                    }
                    return response.json();
                })
                .then(education => {
                    // Set form values
                    document.getElementById('educationId').value = education.id;
                    document.getElementById('institution').value = education.institution || '';
                    document.getElementById('degree').value = education.degree || '';
                    document.getElementById('fieldOfStudy').value = education.fieldOfStudy || '';
                    document.getElementById('description').value = education.description || '';
                    
                    // Format dates for input[type=month]
                    if (education.startDate) {
                        const startDate = new Date(education.startDate);
                        const formattedStartDate = startDate.toISOString().substring(0, 7);
                        document.getElementById('startDate').value = formattedStartDate;
                    }
                    
                    if (education.endDate) {
                        const endDate = new Date(education.endDate);
                        const formattedEndDate = endDate.toISOString().substring(0, 7);
                        document.getElementById('endDate').value = formattedEndDate;
                    }
                    
                    // Set currently studying
                    const currentlyStudying = education.currentlyStudying || false;
                    document.getElementById('currentlyStudying').checked = currentlyStudying;
                    document.getElementById('endDate').disabled = currentlyStudying;
                    
                    // Update modal title
                    document.getElementById('educationModalLabel').textContent = 'Edit Education';
                    
                    // Get the modal element and show it
                    const educationModalElement = document.getElementById('educationModal');
                    if (educationModalElement) {
                        // Initialize modal if not already done
                        if (!educationModal) {
                            educationModal = new bootstrap.Modal(educationModalElement, {
                                backdrop: true,
                                keyboard: true,
                                focus: true
                            });
                        }
                        educationModal.show();
                    } else {
                        console.error('Education modal element not found');
                    }
                })
                .catch(error => {
                    console.error('Error fetching education:', error);
                    showAlert('Failed to load education details. Please try again.', 'danger');
                });
            });
        });
        
        // Handle delete education
        document.querySelectorAll('.delete-education').forEach(button => {
            button.addEventListener('click', function() {
                const educationId = this.getAttribute('data-id');
                document.getElementById('deleteEducationId').value = educationId;
                
                const deleteModalElement = document.getElementById('deleteEducationModal');
                showModal(deleteModal, deleteModalElement);
            });
        });
    }

    // Handle save education (both add and update)
    const saveEducationBtn = document.getElementById('saveEducationBtn');
    if (saveEducationBtn) {
        saveEducationBtn.addEventListener('click', function(e) {
            e.preventDefault(); // Prevent form submission
            
            // Get form data
            const educationId = document.getElementById('educationId').value;
            const isEdit = !!educationId;
            
            // Format dates for the API
            const formatDateForApi = (dateString) => {
                if (!dateString) return null;
                const [year, month] = dateString.split('-');
                return `${year}-${month.padStart(2, '0')}-01`; // Use first day of month for LocalDate
            };

            const educationData = {
                institution: document.getElementById('institution').value.trim(),
                degree: document.getElementById('degree').value.trim(),
                fieldOfStudy: document.getElementById('fieldOfStudy').value.trim(),
                startDate: formatDateForApi(document.getElementById('startDate').value),
                endDate: document.getElementById('currentlyStudying').checked ? 
                    null : formatDateForApi(document.getElementById('endDate').value),
                currentlyStudying: document.getElementById('currentlyStudying').checked,
                description: document.getElementById('description').value.trim()
            };
            
            // Validate required fields
            if (!educationData.institution || !educationData.degree || !educationData.fieldOfStudy || !educationData.startDate) {
                showAlert('Please fill in all required fields', 'danger');
                return;
            }
            
            const url = isEdit ? `/api/education/${educationId}` : '/api/education';
            const method = isEdit ? 'PUT' : 'POST';
            
            fetch(url, {
                method: method,
                headers: getHeaders(),
                body: JSON.stringify(educationData)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(err => {
                        throw new Error(err.message || 'Failed to save education');
                    });
                }
                return response.json();
            })
            .then(data => {
                // Close the modal
                const educationModalElement = document.getElementById('educationModal');
                const modalInstance = bootstrap.Modal.getInstance(educationModalElement);
                if (modalInstance) {
                    modalInstance.hide();
                }
                
                // Remove modal backdrop if it exists
                const modalBackdrop = document.querySelector('.modal-backdrop');
                if (modalBackdrop) {
                    modalBackdrop.remove();
                }
                
                // Remove modal-open class from body
                document.body.classList.remove('modal-open');
                document.body.style.overflow = '';
                document.body.style.paddingRight = '';
                
                // Force a hard refresh of the page to ensure all data is up to date
                window.location.reload(true);
            })
            .catch(error => {
                console.error('Error saving education:', error);
                showAlert(error.message || 'Failed to save education. Please try again.', 'danger');
            });
        });
    }
    
    // Handle delete confirmation
    const confirmDeleteEducation = document.getElementById('confirmDeleteEducation');
    if (confirmDeleteEducation) {
        confirmDeleteEducation.addEventListener('click', function() {
            const educationId = document.getElementById('deleteEducationId').value;
            
            fetch(`/api/education/${educationId}`, {
                method: 'DELETE',
                headers: getHeaders()
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to delete education');
                }
                
                // Close the delete modal
                const deleteModalElement = document.getElementById('deleteEducationModal');
                hideModal(deleteModal, deleteModalElement);
                
                // Show success message
                showAlert('Education deleted successfully!', 'success');
                
                // Remove the education item from the UI
                const educationItem = document.querySelector(`[data-id="${educationId}"]`);
                if (educationItem) {
                    educationItem.remove();
                }
                
                // Show no education message if no items left
                const educationList = document.getElementById('educationList');
                const noEducationMessage = document.getElementById('noEducationMessage');
                if (educationList && educationList.children.length === 0 && noEducationMessage) {
                    noEducationMessage.classList.remove('d-none');
                }
            })
            .catch(error => {
                console.error('Error deleting education:', error);
                showAlert('Failed to delete education. Please try again.', 'danger');
            });
        });
    }

    // Reset form when modal is hidden
    if (educationModalElement) {
        educationModalElement.addEventListener('hidden.bs.modal', function () {
            const form = document.getElementById('educationForm');
            if (form) {
                form.reset();
                document.getElementById('educationId').value = '';
                document.getElementById('educationModalLabel').textContent = 'Add Education';
                document.getElementById('endDate').disabled = false;
                
                // Clear validation
                const formInputs = form.querySelectorAll('.is-invalid');
                formInputs.forEach(input => input.classList.remove('is-invalid'));
            }
        });
    }

    // Helper function to show alerts
    function showAlert(message, type) {
        // Remove any existing alerts
        document.querySelectorAll('.alert-dismissible').forEach(alert => {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        });
        
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
        alertDiv.role = 'alert';
        alertDiv.innerHTML = `
            <i class="bi ${type === 'success' ? 'bi-check-circle' : 'bi-exclamation-triangle'} me-2"></i>
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        `;
        
        const container = document.querySelector('.container.py-5');
        if (container) {
            container.insertBefore(alertDiv, container.firstChild);
            
            // Auto-dismiss after 5 seconds
            setTimeout(() => {
                const alert = bootstrap.Alert.getOrCreateInstance(alertDiv);
                if (alert) alert.close();
            }, 5000);
        }
    }
});
