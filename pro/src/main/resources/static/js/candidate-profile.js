// Global function to add a skill
window.addSkill = function(skill) {
    const skillsInput = document.getElementById('skills');
    if (!skillsInput) return;
    
    const currentSkills = skillsInput.value 
        ? skillsInput.value.split(',').map(s => s.trim()).filter(s => s !== '')
        : [];
    
    // Add the skill if it's not already in the list
    if (skill && !currentSkills.includes(skill)) {
        currentSkills.push(skill);
        skillsInput.value = currentSkills.join(', ');
    }
    
    // Focus the input field
    skillsInput.focus();
};

document.addEventListener('DOMContentLoaded', function() {
    // Initialize tooltips
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl));
    
    // Personal Info Edit/Save functionality
    const editPersonalInfoBtn = document.getElementById('editPersonalInfo');
    const cancelPersonalInfoBtn = document.getElementById('cancelPersonalInfo');
    const savePersonalInfoBtn = document.getElementById('savePersonalInfo');
    const personalInfoForm = document.getElementById('profileForm');
    
    if (editPersonalInfoBtn && personalInfoForm) {
        // Store original values when edit is clicked
        let originalValues = {};
        
        editPersonalInfoBtn.addEventListener('click', function() {
            // Store original values
            const inputs = personalInfoForm.querySelectorAll('input[readonly]');
            inputs.forEach(input => {
                originalValues[input.name] = input.value;
                input.readOnly = false;
                input.classList.add('editable');
                
                // Special handling for experience field to ensure it's editable
                if (input.name === 'experience') {
                    input.type = 'number';
                    input.step = '0.5';
                    input.min = '0';
                    input.max = '50';
                }
            });
            
            // Toggle buttons
            editPersonalInfoBtn.style.display = 'none';
            savePersonalInfoBtn.style.display = 'block';
            cancelPersonalInfoBtn.style.display = 'block';
        });
        
        // Cancel button handler
        if (cancelPersonalInfoBtn) {
            cancelPersonalInfoBtn.addEventListener('click', function() {
                // Restore original values
                const inputs = personalInfoForm.querySelectorAll('input.editable');
                inputs.forEach(input => {
                    input.value = originalValues[input.name] || '';
                    input.readOnly = true;
                    input.classList.remove('editable');
                    
                    // Reset experience field type if needed
                    if (input.name === 'experience') {
                        input.type = 'text';
                    }
                });
                
                // Toggle buttons
                editPersonalInfoBtn.style.display = 'block';
                savePersonalInfoBtn.style.display = 'none';
                cancelPersonalInfoBtn.style.display = 'none';
            });
        }
        
        // Save button handler
        if (savePersonalInfoBtn) {
            savePersonalInfoBtn.addEventListener('click', function() {
                // Submit the form
                personalInfoForm.submit();
            });
        }
    }

    // Profile Edit Toggle
    const editBtn = document.getElementById('editProfileBtn');
    const cancelBtn = document.getElementById('cancelEdit');
    const form = document.getElementById('profileForm');
    const inputs = form.querySelectorAll('input[readonly]');
    const formButtons = document.getElementById('formButtons');
    
    if (editBtn) {
        editBtn.addEventListener('click', function() {
            inputs.forEach(input => {
                if (input) {
                    input.readOnly = false;
                    input.classList.add('editable');
                }
            });
            if (formButtons) formButtons.classList.remove('d-none');
            editBtn.classList.add('d-none');
        });
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', function() {
            inputs.forEach(input => {
                if (input) {
                    input.readOnly = true;
                    input.classList.remove('editable');
                }
            });
            if (formButtons) formButtons.classList.add('d-none');
            editBtn.classList.remove('d-none');
            form.reset();
        });
    }

    // Skills Management
    const editSkillsBtn = document.getElementById('editSkillsBtn');
    const cancelSkillsEdit = document.getElementById('cancelSkillsEdit');
    const saveSkillsBtn = document.getElementById('saveSkills');
    const skillsView = document.getElementById('skillsView');
    const skillsEdit = document.getElementById('skillsEdit');
    const skillsInput = document.getElementById('skillsInput');

    if (editSkillsBtn) {
        editSkillsBtn.addEventListener('click', function() {
            skillsView.classList.add('d-none');
            skillsEdit.classList.remove('d-none');
        });
    }

    if (cancelSkillsEdit) {
        cancelSkillsEdit.addEventListener('click', function() {
            skillsView.classList.remove('d-none');
            skillsEdit.classList.add('d-none');
        });
    }

    if (saveSkillsBtn) {
        saveSkillsBtn.addEventListener('click', function() {
            const skills = skillsInput.value.trim();
            const token = document.querySelector('meta[name="_csrf"]').content;
            const header = document.querySelector('meta[name="_csrf_header"]').content;
            
            fetch('/api/candidate/profile/skills', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [header]: token
                },
                body: JSON.stringify({ skills: skills })
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    location.reload();
                } else {
                    alert('Failed to update skills');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('An error occurred while updating skills');
            });
        });
    }

    // Resume Upload
    const resumeForm = document.getElementById('updateResumeForm');
    const resumeFileInput = document.getElementById('resumeFile');
    const resumeFileInfo = document.getElementById('resumeFileInfo');
    const resumeFileError = document.getElementById('resumeFileError');
    const submitResumeBtn = document.getElementById('submitResumeBtn');

    // Update resume file info when file is selected
    window.updateResumeFile = function(input) {
        if (input.files.length > 0) {
            const file = input.files[0];
            const fileSize = (file.size / (1024 * 1024)).toFixed(2); // Convert to MB
            
            // Validate file type
            const validTypes = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
            if (!validTypes.includes(file.type)) {
                resumeFileError.textContent = 'Invalid file type. Please upload a PDF or Word document.';
                resumeFileError.classList.remove('d-none');
                submitResumeBtn.disabled = true;
                return;
            }
            
            // Validate file size (max 5MB)
            if (file.size > 5 * 1024 * 1024) {
                resumeFileError.textContent = 'File size exceeds 5MB limit.';
                resumeFileError.classList.remove('d-none');
                submitResumeBtn.disabled = true;
                return;
            }
            
            resumeFileInfo.textContent = `${file.name} (${fileSize} MB)`;
            resumeFileError.classList.add('d-none');
            submitResumeBtn.disabled = false;
        }
    };

    // Handle resume form submission
    if (resumeForm) {
        resumeForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            if (!resumeFileInput.files.length) {
                resumeFileError.textContent = 'Please select a file to upload.';
                resumeFileError.classList.remove('d-none');
                return;
            }
            
            const formData = new FormData(this);
            const submitBtn = this.querySelector('button[type="submit"]');
            const spinner = submitBtn.querySelector('.spinner-border');
            const submitText = submitBtn.querySelector('.submit-text');
            
            // Show loading state
            submitBtn.disabled = true;
            spinner.classList.remove('d-none');
            submitText.textContent = 'Uploading...';
            
            fetch(this.action, {
                method: 'POST',
                body: formData,
                headers: {
                    'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
                }
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    location.reload();
                } else {
                    resumeFileError.textContent = data.message || 'Failed to upload resume';
                    resumeFileError.classList.remove('d-none');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                resumeFileError.textContent = 'An error occurred while uploading the file.';
                resumeFileError.classList.remove('d-none');
            })
            .finally(() => {
                submitBtn.disabled = false;
                spinner.classList.add('d-none');
                submitText.textContent = 'Upload Resume';
            });
        });
    }

    // Education Management
    const addEducationBtn = document.getElementById('addEducationBtn');
    const cancelEducationBtn = document.getElementById('cancelEducation');
    const educationFormContainer = document.getElementById('educationFormContainer');
    const educationForm = document.getElementById('educationForm');
    const educationFormTitle = document.getElementById('educationFormTitle');
    const currentlyStudyingCheckbox = document.getElementById('currentlyStudying');
    const endDateInput = document.getElementById('endDate');
    
    // Toggle end date input based on currently studying checkbox
    if (currentlyStudyingCheckbox && endDateInput) {
        currentlyStudyingCheckbox.addEventListener('change', function() {
            endDateInput.disabled = this.checked;
            if (this.checked) {
                endDateInput.value = '';
            }
        });
    }
    
    // Add new education
    if (addEducationBtn) {
        addEducationBtn.addEventListener('click', function() {
            educationForm.reset();
            educationFormTitle.textContent = 'Add Education';
            document.getElementById('educationId').value = '';
            educationFormContainer.classList.remove('d-none');
            window.scrollTo({
                top: educationFormContainer.offsetTop - 20,
                behavior: 'smooth'
            });
        });
    }
    
    // Cancel education form
    if (cancelEducationBtn) {
        cancelEducationBtn.addEventListener('click', function() {
            educationFormContainer.classList.add('d-none');
            educationForm.reset();
        });
    }
    
    // Edit education
    document.querySelectorAll('.edit-education').forEach(btn => {
        btn.addEventListener('click', function() {
            const educationId = this.getAttribute('data-id');
            const educationItem = this.closest('.border-bottom');
            
            // Fill form with existing data
            document.getElementById('educationId').value = educationId;
            document.getElementById('institution').value = educationItem.querySelector('.institution').textContent.trim();
            document.getElementById('degree').value = educationItem.querySelector('.degree').textContent.trim();
            document.getElementById('fieldOfStudy').value = educationItem.querySelector('.field-of-study').textContent.trim();
            
            const dateRange = educationItem.querySelector('.date-range').textContent.split(' - ');
            const startDate = new Date(dateRange[0]);
            document.getElementById('startDate').value = startDate.toISOString().substring(0, 7);
            
            if (dateRange[1] === 'Present') {
                document.getElementById('currentlyStudying').checked = true;
                endDateInput.disabled = true;
            } else {
                const endDate = new Date(dateRange[1]);
                document.getElementById('endDate').value = endDate.toISOString().substring(0, 7);
                document.getElementById('currentlyStudying').checked = false;
                endDateInput.disabled = false;
            }
            
            const description = educationItem.querySelector('.description');
            if (description) {
                document.getElementById('description').value = description.textContent.trim();
            }
            
            educationFormTitle.textContent = 'Edit Education';
            educationFormContainer.classList.remove('d-none');
            
            window.scrollTo({
                top: educationFormContainer.offsetTop - 20,
                behavior: 'smooth'
            });
        });
    });
    
    // Delete education
    document.querySelectorAll('.delete-education').forEach(btn => {
        btn.addEventListener('click', function() {
            if (confirm('Are you sure you want to delete this education entry?')) {
                const educationId = this.getAttribute('data-id');
                const token = document.querySelector('meta[name="_csrf"]').content;
                const header = document.querySelector('meta[name="_csrf_header"]').content;
                
                fetch(`/api/candidate/profile/education/${educationId}`, {
                    method: 'DELETE',
                    headers: {
                        'Content-Type': 'application/json',
                        [header]: token
                    }
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        location.reload();
                    } else {
                        alert('Failed to delete education');
                    }
                })
                .catch(error => {
                    console.error('Error:', error);
                    alert('An error occurred while deleting education');
                });
            }
        });
    });
    
    // Submit education form
    if (educationForm) {
        educationForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            const formData = new FormData(this);
            const educationId = formData.get('id');
            const url = educationId ? 
                `/api/candidate/profile/education/${educationId}` : 
                '/api/candidate/profile/education';
            const method = educationId ? 'PUT' : 'POST';
            
            const submitBtn = this.querySelector('button[type="submit"]');
            const spinner = submitBtn.querySelector('.spinner-border');
            const submitText = submitBtn.querySelector('.submit-text');
            
            // Show loading state
            submitBtn.disabled = true;
            spinner.classList.remove('d-none');
            submitText.textContent = 'Saving...';
            
            // Convert FormData to JSON
            const jsonData = {};
            formData.forEach((value, key) => {
                if (key === 'currentlyStudying') {
                    jsonData[key] = true; // If it's in the form data, it's checked
                } else if (value) {
                    jsonData[key] = value;
                }
            });
            
            // If currentlyStudying is not checked, add end date validation
            if (!document.getElementById('currentlyStudying').checked && !formData.get('endDate')) {
                alert('Please provide an end date or check "I currently study here"');
                submitBtn.disabled = false;
                spinner.classList.add('d-none');
                submitText.textContent = 'Save Education';
                return;
            }
            
            fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
                },
                body: JSON.stringify(jsonData)
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    location.reload();
                } else {
                    alert(data.message || 'Failed to save education');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('An error occurred while saving education');
            })
            .finally(() => {
                submitBtn.disabled = false;
                spinner.classList.add('d-none');
                submitText.textContent = 'Save Education';
            });
        });
    }

    const skillsInput = document.getElementById('skills');
            
    // Handle comma and Enter key in skills input
    if (skillsInput) {
        skillsInput.addEventListener('keydown', function(e) {
            // Prevent form submission on Enter
            if (e.key === 'Enter') {
                e.preventDefault();
                return false;
            }
            
            // Add skill on comma
            if (e.key === ',') {
                e.preventDefault();
                const text = this.value.trim();
                if (text) {
                    const skills = text.split(',').map(s => s.trim()).filter(s => s !== '');
                    this.value = skills.join(', ');
                }
                return false;
            }
        });
        
        // Format skills on blur
        skillsInput.addEventListener('blur', function() {
            const text = this.value.trim();
            if (text) {
                const skills = text.split(',').map(s => s.trim()).filter(s => s !== '');
                this.value = skills.join(', ');
            }
        });
    }

    // Helper function to format date to YYYY-MM format for date inputs
    function formatDateForInput(date) {
        const d = new Date(date);
        let month = '' + (d.getMonth() + 1);
        let year = d.getFullYear();

        if (month.length < 2) 
            month = '0' + month;

        return [year, month].join('-');
    }
