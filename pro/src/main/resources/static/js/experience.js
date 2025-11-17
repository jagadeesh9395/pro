// Wait for the DOM to be fully loaded
document.addEventListener('DOMContentLoaded', function() {
    // Modal elements and instances
    let experienceModal = null;
    let deleteModal = null;
    let experienceModalElement = null;
    let deleteModalElement = null;
    let addExperienceBtn = null;
    
    // Function to initialize modals
    function initializeModals() {
        // Get modal elements
        experienceModalElement = document.getElementById('experienceModal');
        deleteModalElement = document.getElementById('deleteExperienceModal');
        
        if (!experienceModalElement) {
            console.error('Experience modal element not found');
            return false;
        }
        
        if (!deleteModalElement) {
            console.error('Delete modal element not found');
            return false;
        }
        
        // Initialize experience modal
        if (experienceModalElement) {
            try {
                experienceModal = new bootstrap.Modal(experienceModalElement, {
                    backdrop: true,
                    keyboard: true,
                    focus: true
                });
            } catch (e) {
                console.error('Failed to initialize experience modal:', e);
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
    
    // Get the Add Experience button by ID
    addExperienceBtn = document.getElementById('addExperienceBtn');
    
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
        // Add click handler for Add Experience button
        if (addExperienceBtn) {
            addExperienceBtn.addEventListener('click', function(e) {
                e.preventDefault();
                
                const title = document.getElementById('experienceModalLabel');
                const idInput = document.getElementById('experienceId');
                const form = document.getElementById('experienceForm');
                
                if (title) title.textContent = 'Add Work Experience';
                if (idInput) idInput.value = '';
                if (form) form.reset();
                
                // Initialize the currently working state
                const endDateInput = document.getElementById('endDateExp');
                const currentlyWorkingCheckbox = document.getElementById('currentlyWorking');
                if (endDateInput && currentlyWorkingCheckbox) {
                    endDateInput.disabled = currentlyWorkingCheckbox.checked;
                }
                
                showModal(experienceModal, experienceModalElement);
            });
        } else {
            console.warn('Add Experience button not found');
        }
        
        // Setup currently working checkbox - using event delegation for dynamic elements
        document.addEventListener('change', function(e) {
            if (e.target && e.target.id === 'currentlyWorking') {
                const endDateInput = document.getElementById('endDateExp');
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
    const currentlyWorkingCheckbox = document.getElementById('currentlyWorking');
    const endDateInput = document.getElementById('endDateExp');
    if (currentlyWorkingCheckbox && endDateInput) {
        endDateInput.disabled = currentlyWorkingCheckbox.checked;
    }
    
    // Load experience data when the page loads
    loadExperienceData();

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

    // Load experience data
    function loadExperienceData() {
        fetch('/api/experience', {
            method: 'GET',
            headers: getHeaders()
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load experience data');
            }
            return response.json();
        })
        .then(experienceList => {
            const experienceListContainer = document.getElementById('experienceList');
            const noExperienceMessage = document.getElementById('noExperienceMessage');
            
            if (experienceList && experienceList.length > 0) {
                if (noExperienceMessage) noExperienceMessage.classList.add('d-none');
                if (experienceListContainer) experienceListContainer.innerHTML = '';
                
                experienceList.forEach(exp => {
                    const expItem = createExperienceItem(exp);
                    if (experienceListContainer) {
                        experienceListContainer.insertAdjacentHTML('beforeend', expItem);
                    }
                });
                
                // Add event listeners for the new elements
                addExperienceEventListeners();
            } else {
                if (noExperienceMessage) noExperienceMessage.classList.remove('d-none');
            }
        })
        .catch(error => {
            console.error('Error loading experience data:', error);
            showAlert('Failed to load work experience. Please try again later.', 'danger');
        });
    }

    // Create experience item HTML with field labels and key-value format
    function createExperienceItem(exp) {
        const startDate = exp.startDate ? new Date(exp.startDate).toLocaleDateString('en-US', { 
            year: 'numeric', 
            month: 'short' 
        }) : 'N/A';
        
        let endDate = 'Present';
        if (exp.endDate) {
            endDate = new Date(exp.endDate).toLocaleDateString('en-US', { 
                year: 'numeric', 
                month: 'short' 
            });
        }
        
        const employmentTypeMap = {
            'FULL_TIME': 'Full-time',
            'PART_TIME': 'Part-time',
            'CONTRACT': 'Contract',
            'FREELANCE': 'Freelance',
            'INTERNSHIP': 'Internship',
            'APPRENTICESHIP': 'Apprenticeship'
        };
        
        const employmentType = employmentTypeMap[exp.employmentType] || exp.employmentType || 'Not specified';
        
        return `
            <div class="card mb-3" id="exp-${exp.id}">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div class="flex-grow-1 me-3">
                            <h5 class="mb-3">${exp.position || 'Position not specified'}</h5>
                            
                            <div class="row g-2 mb-2">
                                <div class="col-md-6">
                                    <div class="d-flex">
                                        <span class="text-muted me-2 fw-medium">Company:</span>
                                        <span>${exp.company || 'Not specified'}</span>
                                    </div>
                                </div>
                                <div class="col-md-6">
                                    <div class="d-flex">
                                        <span class="text-muted me-2 fw-medium">Employment Type:</span>
                                        <span>${employmentType}</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="row g-2 mb-2">
                                <div class="col-md-6">
                                    <div class="d-flex">
                                        <span class="text-muted me-2 fw-medium">Duration:</span>
                                        <span>${startDate} - ${endDate}</span>
                                    </div>
                                </div>
                                ${exp.location ? `
                                <div class="col-md-6">
                                    <div class="d-flex">
                                        <span class="text-muted me-2 fw-medium">Location:</span>
                                        <span><i class="bi bi-geo-alt"></i> ${exp.location}</span>
                                    </div>
                                </div>` : ''}
                            </div>
                            
                            ${exp.description ? `
                            <div class="mt-3">
                                <div class="d-flex">
                                    <span class="text-muted me-2 fw-medium">Description:</span>
                                </div>
                                <div class="ms-4">
                                    <p class="mb-0">${exp.description.replace(/\n/g, '<br>')}</p>
                                </div>
                            </div>` : ''}
                        </div>
                        
                        <div class="dropdown">
                            <button class="btn btn-sm btn-outline-secondary dropdown-toggle" type="button" 
                                    id="expDropdown${exp.id}" data-bs-toggle="dropdown" aria-expanded="false">
                                <i class="bi bi-three-dots-vertical"></i>
                            </button>
                            <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="expDropdown${exp.id}">
                                <li>
                                    <a class="dropdown-item edit-exp" href="#" data-id="${exp.id}">
                                        <i class="bi bi-pencil me-2"></i>Edit
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item text-danger delete-exp" href="#" data-id="${exp.id}">
                                        <i class="bi bi-trash me-2"></i>Delete
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Add event listeners for edit and delete buttons
    function addExperienceEventListeners() {
        // Edit buttons
        document.querySelectorAll('.edit-exp').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                const expId = this.getAttribute('data-id');
                if (expId) {
                    editExperience(expId);
                }
            });
        });
        
        // Delete buttons
        document.querySelectorAll('.delete-exp').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                const expId = this.getAttribute('data-id');
                if (expId) {
                    document.getElementById('deleteExperienceId').value = expId;
                    showModal(deleteModal, deleteModalElement);
                }
            });
        });
    }
    
    // Edit experience
    function editExperience(expId) {
        fetch(`/api/experience/${expId}`, {
            method: 'GET',
            headers: getHeaders()
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch experience details');
            }
            return response.json();
        })
        .then(exp => {
            const title = document.getElementById('experienceModalLabel');
            const form = document.getElementById('experienceForm');
            
            if (title) title.textContent = 'Edit Work Experience';
            if (form) {
                document.getElementById('experienceId').value = exp.id || '';
                document.getElementById('company').value = exp.company || '';
                document.getElementById('position').value = exp.position || '';
                document.getElementById('employmentType').value = exp.employmentType || 'FULL_TIME';
                document.getElementById('location').value = exp.location || '';
                
                // Format dates for input[type="month"]
                if (exp.startDate) {
                    const startDate = new Date(exp.startDate);
                    document.getElementById('startDateExp').value = startDate.toISOString().slice(0, 7);
                }
                
                const currentlyWorkingCheckbox = document.getElementById('currentlyWorking');
                const endDateInput = document.getElementById('endDateExp');
                
                if (exp.endDate) {
                    const endDate = new Date(exp.endDate);
                    endDateInput.value = endDate.toISOString().slice(0, 7);
                    currentlyWorkingCheckbox.checked = false;
                    endDateInput.disabled = false;
                } else {
                    currentlyWorkingCheckbox.checked = true;
                    endDateInput.value = '';
                    endDateInput.disabled = true;
                }
                
                document.getElementById('descriptionExp').value = exp.description || '';
                
                showModal(experienceModal, experienceModalElement);
            }
        })
        .catch(error => {
            console.error('Error fetching experience details:', error);
            showAlert('Failed to load experience details. Please try again.', 'danger');
        });
    }
    
    // Handle save experience (both add and update)
    const saveExperienceBtn = document.getElementById('saveExperienceBtn');
    if (saveExperienceBtn) {
        saveExperienceBtn.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Format dates for the API
            function formatDateForApi(dateString) {
                if (!dateString) return null;
                const date = new Date(dateString);
                return date.toISOString().split('T')[0]; // YYYY-MM-DD
            }
            
            const experienceData = {
                company: document.getElementById('company').value.trim(),
                position: document.getElementById('position').value.trim(),
                employmentType: document.getElementById('employmentType').value,
                location: document.getElementById('location').value.trim(),
                startDate: formatDateForApi(document.getElementById('startDateExp').value),
                endDate: document.getElementById('currentlyWorking').checked ? 
                    null : formatDateForApi(document.getElementById('endDateExp').value),
                description: document.getElementById('descriptionExp').value.trim()
            };
            
            // Basic validation
            if (!experienceData.company || !experienceData.position || !experienceData.startDate) {
                showAlert('Please fill in all required fields', 'warning');
                return;
            }
            
            const expId = document.getElementById('experienceId').value;
            const method = expId ? 'PUT' : 'POST';
            const url = expId ? `/api/experience/${expId}` : '/api/experience';
            
            // Show loading state
            const saveBtnText = saveExperienceBtn.innerHTML;
            saveExperienceBtn.disabled = true;
            saveExperienceBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Saving...';
            
            fetch(url, {
                method: method,
                headers: getHeaders(),
                body: JSON.stringify(experienceData)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(err => {
                        throw new Error(err.message || 'Failed to save experience');
                    });
                }
                return response.json();
            })
            .then(() => {
                // Reload the experience list
                loadExperienceData();
                // Hide the modal
                hideModal(experienceModal, experienceModalElement);
                // Show success message
                showAlert('Work experience saved successfully!', 'success');
            })
            .catch(error => {
                console.error('Error saving experience:', error);
                showAlert(error.message || 'Failed to save work experience. Please try again.', 'danger');
            })
            .finally(() => {
                // Reset button state
                saveExperienceBtn.disabled = false;
                saveExperienceBtn.innerHTML = saveBtnText;
            });
        });
    }
    
    // Handle delete confirmation
    const confirmDeleteBtn = document.getElementById('confirmDeleteExperience');
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', function() {
            const expId = document.getElementById('deleteExperienceId').value;
            
            if (!expId) {
                hideModal(deleteModal, deleteModalElement);
                return;
            }
            
            // Show loading state
            const deleteBtnText = confirmDeleteBtn.innerHTML;
            confirmDeleteBtn.disabled = true;
            confirmDeleteBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Deleting...';
            
            fetch(`/api/experience/${expId}`, {
                method: 'DELETE',
                headers: getHeaders()
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to delete experience');
                }
                return response.json();
            })
            .then(() => {
                // Remove the experience item from the DOM
                const expItem = document.getElementById(`exp-${expId}`);
                if (expItem) {
                    expItem.remove();
                }
                
                // Check if there are no more experiences
                const experienceList = document.getElementById('experienceList');
                const noExperienceMessage = document.getElementById('noExperienceMessage');
                
                if (experienceList && experienceList.children.length === 0 && noExperienceMessage) {
                    noExperienceMessage.classList.remove('d-none');
                }
                
                // Hide the modal
                hideModal(deleteModal, deleteModalElement);
                // Show success message
                showAlert('Work experience deleted successfully!', 'success');
            })
            .catch(error => {
                console.error('Error deleting experience:', error);
                showAlert('Failed to delete work experience. Please try again.', 'danger');
            })
            .finally(() => {
                // Reset button state
                confirmDeleteBtn.disabled = false;
                confirmDeleteBtn.innerHTML = deleteBtnText;
            });
        });
    }
    
    // Handle modal hidden event to reset form
    if (experienceModalElement) {
        experienceModalElement.addEventListener('hidden.bs.modal', function () {
            const form = document.getElementById('experienceForm');
            if (form) {
                form.reset();
                document.getElementById('experienceId').value = '';
                document.getElementById('currentlyWorking').checked = false;
                document.getElementById('endDateExp').disabled = false;
            }
        });
    }
    
    // Helper function to show alerts
    function showAlert(message, type) {
        // Remove any existing alerts
        const existingAlert = document.querySelector('.alert-dismissible');
        if (existingAlert) {
            existingAlert.remove();
        }
        
        const alertHtml = `
            <div class="alert alert-${type} alert-dismissible fade show" role="alert">
                ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
        
        // Insert the alert at the top of the main content
        const mainContent = document.querySelector('.container.py-5');
        if (mainContent) {
            mainContent.insertAdjacentHTML('afterbegin', alertHtml);
            
            // Auto-dismiss after 5 seconds
            setTimeout(() => {
                const alert = document.querySelector('.alert-dismissible');
                if (alert) {
                    const bsAlert = new bootstrap.Alert(alert);
                    bsAlert.close();
                }
            }, 5000);
        }
    }
});
