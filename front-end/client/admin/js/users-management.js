/**
 * Admin Users Management
 * List, edit, delete users and manage roles
 */

import { API_BASE_URL, fetchWithAuth } from './api-client.js';
import { showNotification } from './utils.js';

let allUsers = [];
let currentFilter = 'ALL';

/**
 * Initialize users management page
 */
export function initUsersManagement() {
    renderUsersPage();
    loadUsers();
}

/**
 * Render the users management page
 */
function renderUsersPage() {
    const content = document.querySelector('.main-content');
    if (!content) {
        console.error('Main content container not found');
        return;
    }
    
    content.innerHTML = `
        <div class="page-header">
            <h2>Quản lý người dùng</h2>
            <button class="btn btn-primary" onclick="usersManagement.showCreateUserModal()">
                <i class="fas fa-user-plus"></i> Thêm người dùng
            </button>
        </div>

        <div class="users-filters">
            <button class="filter-btn active" data-filter="ALL" onclick="usersManagement.filterByRole('ALL')">
                Tất cả (<span id="countAll">0</span>)
            </button>
            <button class="filter-btn" data-filter="DRIVER" onclick="usersManagement.filterByRole('DRIVER')">
                Tài xế (<span id="countDriver">0</span>)
            </button>
            <button class="filter-btn" data-filter="CS_STAFF" onclick="usersManagement.filterByRole('CS_STAFF')">
                Nhân viên (<span id="countStaff">0</span>)
            </button>
            <button class="filter-btn" data-filter="ADMIN" onclick="usersManagement.filterByRole('ADMIN')">
                Admin (<span id="countAdmin">0</span>)
            </button>
        </div>

        <div class="search-box">
            <input type="text" id="userSearch" placeholder="Tìm kiếm theo tên, email..." />
            <i class="fas fa-search"></i>
        </div>

        <div class="users-table-container">
            <table class="users-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Tên</th>
                        <th>Email</th>
                        <th>Vai trò</th>
                        <th>Số điện thoại</th>
                        <th>Trạng thái</th>
                        <th>Ngày tạo</th>
                        <th>Thao tác</th>
                    </tr>
                </thead>
                <tbody id="usersTableBody">
                    <tr>
                        <td colspan="8" style="text-align: center; padding: 40px;">
                            <i class="fas fa-spinner fa-spin"></i> Đang tải...
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    `;

    // Setup search
    document.getElementById('userSearch')?.addEventListener('input', (e) => {
        searchUsers(e.target.value);
    });
}

/**
 * Load all users
 */
async function loadUsers() {
    try {
        const data = await fetchWithAuth(`${API_BASE_URL}/admin/users`);
        const users = data && Array.isArray(data.users)
            ? data.users
            : Array.isArray(data)
                ? data
                : [];

        allUsers = users;
        updateRoleCounts();
        renderUsersTable(allUsers);
    } catch (error) {
        console.error('Error loading users:', error);
        showNotification('Lỗi kết nối đến máy chủ', 'error');
    }
}

/**
 * Update role counts
 */
function updateRoleCounts() {
    document.getElementById('countAll').textContent = allUsers.length;
    document.getElementById('countDriver').textContent = allUsers.filter(u => u.role === 'DRIVER').length;
    document.getElementById('countStaff').textContent = allUsers.filter(u => u.role === 'CS_STAFF').length;
    document.getElementById('countAdmin').textContent = allUsers.filter(u => u.role === 'ADMIN').length;
}

/**
 * Filter users by role
 */
export function filterByRole(role) {
    currentFilter = role;
    
    // Update active filter button
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.filter === role);
    });

    // Filter and render
    const filtered = role === 'ALL' 
        ? allUsers 
        : allUsers.filter(u => u.role === role);
    
    renderUsersTable(filtered);
}

/**
 * Search users
 */
function searchUsers(searchTerm) {
    const filtered = allUsers.filter(user => 
        (user.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
         user.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
         user.phone?.includes(searchTerm)) &&
        (currentFilter === 'ALL' || user.role === currentFilter)
    );
    renderUsersTable(filtered);
}

/**
 * Render users table
 */
function renderUsersTable(users) {
    const tbody = document.getElementById('usersTableBody');
    
    if (users.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; padding: 40px; color: #666;">
                    <i class="fas fa-inbox" style="font-size: 48px; margin-bottom: 16px; display: block;"></i>
                    Không có người dùng nào
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr>
            <td>${user.id}</td>
            <td>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <div class="user-avatar">${getUserInitials(user.name)}</div>
                    <span>${user.name || 'N/A'}</span>
                </div>
            </td>
            <td>${user.email}</td>
            <td>
                <span class="role-badge role-${user.role.toLowerCase()}">${getRoleText(user.role)}</span>
            </td>
            <td>${user.phone || 'N/A'}</td>
            <td>
                <span class="status-badge ${user.active ? 'status-active' : 'status-inactive'}">
                    ${user.active ? 'Hoạt động' : 'Khóa'}
                </span>
            </td>
            <td>${formatDate(user.createdAt)}</td>
            <td>
                <div class="action-buttons">
                    <button class="btn-icon" onclick="usersManagement.showEditUserModal(${user.id})" title="Chỉnh sửa">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-icon btn-danger" onclick="usersManagement.deleteUser(${user.id})" title="Xóa">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

/**
 * Show create user modal
 */
export function showCreateUserModal() {
    const modal = createModal('Thêm người dùng mới', `
        <form id="userForm">
            <div class="form-group">
                <label>Tên *</label>
                <input type="text" name="name" required />
            </div>
            <div class="form-group">
                <label>Email *</label>
                <input type="email" name="email" required />
            </div>
            <div class="form-group">
                <label>Mật khẩu *</label>
                <input type="password" name="password" required minlength="6" />
            </div>
            <div class="form-group">
                <label>Số điện thoại</label>
                <input type="tel" name="phone" />
            </div>
            <div class="form-group">
                <label>Vai trò *</label>
                <select name="role" required>
                    <option value="DRIVER">Tài xế</option>
                    <option value="CS_STAFF">Nhân viên trạm sạc</option>
                    <option value="ADMIN">Admin</option>
                </select>
            </div>
            <div class="form-group">
                <label>Trạng thái</label>
                <select name="active">
                    <option value="true">Hoạt động</option>
                    <option value="false">Khóa</option>
                </select>
            </div>
        </form>
    `, async () => {
        const form = document.getElementById('userForm');
        const formData = new FormData(form);
        const data = Object.fromEntries(formData);
        data.active = data.active === 'true';
        
        await createUser(data);
        closeModal(modal);
    });
}

/**
 * Show edit user modal
 */
export async function showEditUserModal(userId) {
    const user = allUsers.find(u => u.id === userId);
    if (!user) return;

    const modal = createModal('Chỉnh sửa người dùng', `
        <form id="userForm">
            <div class="form-group">
                <label>Tên *</label>
                <input type="text" name="name" value="${user.name || ''}" required />
            </div>
            <div class="form-group">
                <label>Email *</label>
                <input type="email" name="email" value="${user.email}" required />
            </div>
            <div class="form-group">
                <label>Mật khẩu mới (để trống nếu không đổi)</label>
                <input type="password" name="password" minlength="6" />
            </div>
            <div class="form-group">
                <label>Số điện thoại</label>
                <input type="tel" name="phone" value="${user.phone || ''}" />
            </div>
            <div class="form-group">
                <label>Vai trò *</label>
                <select name="role" required>
                    <option value="DRIVER" ${user.role === 'DRIVER' ? 'selected' : ''}>Tài xế</option>
                    <option value="CS_STAFF" ${user.role === 'CS_STAFF' ? 'selected' : ''}>Nhân viên trạm sạc</option>
                    <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>Admin</option>
                </select>
            </div>
            <div class="form-group">
                <label>Trạng thái</label>
                <select name="active">
                    <option value="true" ${user.active ? 'selected' : ''}>Hoạt động</option>
                    <option value="false" ${!user.active ? 'selected' : ''}>Khóa</option>
                </select>
            </div>
        </form>
    `, async () => {
        const form = document.getElementById('userForm');
        const formData = new FormData(form);
        const data = Object.fromEntries(formData);
        
        // Remove password if empty
        if (!data.password) {
            delete data.password;
        }
        
        data.active = data.active === 'true';
        
        await updateUser(userId, data);
        closeModal(modal);
    });
}

/**
 * Create new user
 */
async function createUser(data) {
    try {
        const result = await fetchWithAuth(`${API_BASE_URL}/admin/users`, {
            method: 'POST',
            body: JSON.stringify(data)
        });

        if (result && result.success) {
            showNotification('Tạo người dùng thành công!', 'success');
            await loadUsers();
        } else {
            showNotification(
                (result && (result.error || result.message)) || 'Không thể tạo người dùng',
                'error'
            );
        }
    } catch (error) {
        console.error('Error creating user:', error);
        showNotification('Lỗi kết nối đến máy chủ', 'error');
    }
}

/**
 * Update user
 */
async function updateUser(userId, data) {
    try {
        const result = await fetchWithAuth(`${API_BASE_URL}/admin/users/${userId}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });

        if (result && result.success) {
            showNotification('Cập nhật người dùng thành công!', 'success');
            await loadUsers();
        } else {
            showNotification(
                (result && (result.error || result.message)) || 'Không thể cập nhật người dùng',
                'error'
            );
        }
    } catch (error) {
        console.error('Error updating user:', error);
        showNotification('Lỗi kết nối đến máy chủ', 'error');
    }
}

/**
 * Delete user
 */
export async function deleteUser(userId) {
    const user = allUsers.find(u => u.id === userId);
    if (!user) return;

    if (!confirm(`Bạn có chắc chắn muốn xóa người dùng "${user.name}" (${user.email})?`)) {
        return;
    }

    try {
        const result = await fetchWithAuth(`${API_BASE_URL}/admin/users/${userId}`, {
            method: 'DELETE'
        });

        if (result && result.success) {
            showNotification('Xóa người dùng thành công!', 'success');
            await loadUsers();
        } else {
            showNotification(
                (result && (result.error || result.message)) || 'Không thể xóa người dùng',
                'error'
            );
        }
    } catch (error) {
        console.error('Error deleting user:', error);
        showNotification('Lỗi kết nối đến máy chủ', 'error');
    }
}

// Utility functions
function createModal(title, content, onSubmit) {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>${title}</h3>
                <button class="btn-close" onclick="this.closest('.modal-overlay').remove()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                ${content}
            </div>
            <div class="modal-footer">
                <button class="btn btn-outline" onclick="this.closest('.modal-overlay').remove()">
                    Hủy
                </button>
                <button class="btn btn-primary" id="modalSubmitBtn">
                    Xác nhận
                </button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    const submitBtn = modal.querySelector('#modalSubmitBtn');
    submitBtn.addEventListener('click', async () => {
        const form = modal.querySelector('form');
        if (form && !form.checkValidity()) {
            form.reportValidity();
            return;
        }
        await onSubmit();
    });

    return modal;
}

function closeModal(modal) {
    if (modal && modal.remove) {
        modal.remove();
    }
}

function getUserInitials(name) {
    if (!name) return '?';
    const parts = name.split(' ');
    if (parts.length >= 2) {
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
}

function getRoleText(role) {
    const map = {
        'DRIVER': 'Tài xế',
        'CS_STAFF': 'Nhân viên',
        'ADMIN': 'Admin'
    };
    return map[role] || role;
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN');
}

// Export for global access
window.usersManagement = {
    initUsersManagement,
    filterByRole,
    showCreateUserModal,
    showEditUserModal,
    deleteUser
};
