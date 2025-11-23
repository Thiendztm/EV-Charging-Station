/**
 * Staff Sessions Management
 * Quản lý phiên sạc tại trạm cho nhân viên
 */

import { API_BASE_URL } from './data.js';
import { fetchWithAuth } from './utils.js';

// Active sessions list
let activeSessions = [];
let refreshInterval = null;

/**
 * Load active sessions at current station
 */
export async function loadActiveSessions(stationId) {
    try {
        const response = await fetchWithAuth(`${API_BASE_URL}/staff/station/${stationId}/status`);

        if (response.ok) {
            const data = await response.json();
            activeSessions = data.sessions || [];
            renderSessionsTable(activeSessions);
            updateStationStats(data);
        } else {
            showError('Không thể tải danh sách phiên sạc');
        }
    } catch (error) {
        console.error('Error loading sessions:', error);
        showError('Lỗi kết nối đến máy chủ');
    }
}

/**
 * Render sessions table
 */
function renderSessionsTable(sessions) {
    const tbody = document.getElementById('sessionsTableBody');
    if (!tbody) return;

    if (sessions.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" style="text-align: center; padding: 40px; color: #666;">
                    <i class="fas fa-inbox" style="font-size: 48px; margin-bottom: 16px; display: block;"></i>
                    Không có phiên sạc nào đang hoạt động
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = sessions.map(session => {
        const duration = calculateDuration(session.startTime);
        const statusClass = getStatusClass(session.status);
        const statusText = getStatusText(session.status);

        return `
            <tr data-session-id="${session.sessionId}">
                <td>
                    <div style="font-weight: 600;">${session.chargerName || `Charger #${session.chargerId}`}</div>
                    <small style="color: #666;">${session.connectorType || 'CCS2'}</small>
                </td>
                <td>
                    <div>${session.userName || 'N/A'}</div>
                    <small style="color: #666;">${session.userEmail || ''}</small>
                </td>
                <td>
                    <div class="soc-progress">
                        <div class="soc-bar" style="width: ${session.currentSoc || 0}%"></div>
                        <span class="soc-text">${session.currentSoc || 0}%</span>
                    </div>
                </td>
                <td>${session.energyConsumed ? session.energyConsumed.toFixed(2) : '0.00'} kWh</td>
                <td>${duration}</td>
                <td>${formatCurrency(session.currentCost || 0)}</td>
                <td>
                    <span class="status-badge ${statusClass}">${statusText}</span>
                </td>
                <td>
                    ${session.status === 'ACTIVE' ? `
                        <button class="btn btn-sm btn-danger" onclick="staffSessions.stopSession('${session.sessionId}')">
                            <i class="fas fa-stop"></i> Dừng
                        </button>
                    ` : `
                        <button class="btn btn-sm btn-outline" onclick="staffSessions.viewSessionDetail('${session.sessionId}')">
                            <i class="fas fa-eye"></i> Chi tiết
                        </button>
                    `}
                </td>
            </tr>
        `;
    }).join('');
}

/**
 * Update station statistics
 */
function updateStationStats(data) {
    const activeCount = data.sessions?.filter(s => s.status === 'ACTIVE').length || 0;
    const totalChargers = data.totalChargers || 0;
    const availableChargers = data.availableChargers || 0;

    const activeEl = document.getElementById('activeSessionsCount');
    const totalEl = document.getElementById('totalChargersCount');
    const availableEl = document.getElementById('availableChargersCount');

    if (activeEl) activeEl.textContent = activeCount;
    if (totalEl) totalEl.textContent = totalChargers;
    if (availableEl) availableEl.textContent = availableChargers;
}

/**
 * Start new session (for walk-in customers or manual start)
 */
export async function startSession(chargerId, userId) {
    if (!confirm('Xác nhận bắt đầu phiên sạc?')) return;

    try {
        const response = await fetchWithAuth(`${API_BASE_URL}/staff/sessions/start`, {
            method: 'POST',
            body: JSON.stringify({ chargerId, userId })
        });

        if (response.ok) {
            const data = await response.json();
            showSuccess('Đã bắt đầu phiên sạc thành công!');

            // Refresh sessions list
            const stationId = getCurrentStationId();
            if (stationId) {
                await loadActiveSessions(stationId);
            }
        } else {
            const error = await response.json();
            showError(error.message || 'Không thể bắt đầu phiên sạc');
        }
    } catch (error) {
        console.error('Error starting session:', error);
        showError('Lỗi kết nối đến máy chủ');
    }
}

/**
 * Stop active session
 */
export async function stopSession(sessionId) {
    // Show confirmation dialog with final details input
    const modal = showStopSessionModal(sessionId);

    // Wait for user input
    return new Promise((resolve) => {
        window.confirmStopSession = async (endSoc, notes) => {
            try {
                const response = await fetchWithAuth(`${API_BASE_URL}/staff/sessions/${sessionId}/stop`, {
                    method: 'POST',
                    body: JSON.stringify({ endSoc, notes })
                });

                if (response.ok) {
                    const data = await response.json();
                    showSuccess(`Đã dừng phiên sạc. Chi phí: ${formatCurrency(data.finalCost)}`);

                    // Close modal
                    closeModal(modal);

                    // Refresh sessions list
                    const stationId = getCurrentStationId();
                    if (stationId) {
                        await loadActiveSessions(stationId);
                    }

                    resolve(true);
                } else {
                    const error = await response.json();
                    showError(error.message || 'Không thể dừng phiên sạc');
                    resolve(false);
                }
            } catch (error) {
                console.error('Error stopping session:', error);
                showError('Lỗi kết nối đến máy chủ');
                resolve(false);
            }
        };
    });
}

/**
 * Show stop session modal
 */
function showStopSessionModal(sessionId) {
    const session = activeSessions.find(s => s.sessionId === sessionId);
    if (!session) return;

    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>Dừng phiên sạc</h3>
                <button class="btn-close" onclick="this.closest('.modal-overlay').remove()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                <div class="session-summary">
                    <p><strong>Charger:</strong> ${session.chargerName}</p>
                    <p><strong>Khách hàng:</strong> ${session.userName}</p>
                    <p><strong>Thời gian:</strong> ${calculateDuration(session.startTime)}</p>
                    <p><strong>Năng lượng:</strong> ${session.energyConsumed?.toFixed(2) || '0.00'} kWh</p>
                    <p><strong>Chi phí tạm tính:</strong> ${formatCurrency(session.currentCost || 0)}</p>
                </div>
                
                <div class="form-group">
                    <label for="endSoc">SOC cuối (%):</label>
                    <input type="number" id="endSoc" min="0" max="100" value="${session.currentSoc || 80}" required />
                </div>
                
                <div class="form-group">
                    <label for="stopNotes">Ghi chú (tùy chọn):</label>
                    <textarea id="stopNotes" rows="3" placeholder="Ví dụ: Khách yêu cầu dừng sớm..."></textarea>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-outline" onclick="this.closest('.modal-overlay').remove()">
                    Hủy
                </button>
                <button class="btn btn-primary" onclick="staffSessions.confirmStopFromModal('${sessionId}')">
                    Xác nhận dừng
                </button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);
    return modal;
}

/**
 * Confirm stop from modal
 */
export function confirmStopFromModal(sessionId) {
    const endSoc = parseInt(document.getElementById('endSoc').value);
    const notes = document.getElementById('stopNotes').value;

    if (isNaN(endSoc) || endSoc < 0 || endSoc > 100) {
        showError('SOC không hợp lệ (0-100)');
        return;
    }

    if (window.confirmStopSession) {
        window.confirmStopSession(endSoc, notes);
    }
}

/**
 * View session detail
 */
export function viewSessionDetail(sessionId) {
    window.location.href = `../session-detail.html?id=${sessionId}`;
}

/**
 * Confirm at-site payment
 */
export async function confirmPayment(sessionId, amount, method) {
    if (!confirm(`Xác nhận thanh toán ${formatCurrency(amount)} bằng ${method}?`)) return;

    try {
        const response = await fetchWithAuth(`${API_BASE_URL}/staff/payments/confirm`, {
            method: 'POST',
            body: JSON.stringify({ sessionId, amount, method })
        });

        if (response.ok) {
            showSuccess('Đã xác nhận thanh toán thành công!');

            // Refresh sessions
            const stationId = getCurrentStationId();
            if (stationId) {
                await loadActiveSessions(stationId);
            }
        } else {
            const error = await response.json();
            showError(error.message || 'Không thể xác nhận thanh toán');
        }
    } catch (error) {
        console.error('Error confirming payment:', error);
        showError('Lỗi kết nối đến máy chủ');
    }
}

/**
 * Start auto-refresh
 */
export function startAutoRefresh(stationId, intervalMs = 10000) {
    if (refreshInterval) {
        clearInterval(refreshInterval);
    }

    refreshInterval = setInterval(() => {
        loadActiveSessions(stationId);
    }, intervalMs);
}

/**
 * Stop auto-refresh
 */
export function stopAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
        refreshInterval = null;
    }
}

// Utility functions
function calculateDuration(startTime) {
    const start = new Date(startTime);
    const now = new Date();
    const diffMs = now - start;
    const hours = Math.floor(diffMs / (1000 * 60 * 60));
    const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
    return `${hours}h ${minutes}m`;
}

function getStatusClass(status) {
    const map = {
        'ACTIVE': 'status-active',
        'COMPLETED': 'status-completed',
        'STOPPED': 'status-stopped',
        'ERROR': 'status-error'
    };
    return map[status] || '';
}

function getStatusText(status) {
    const map = {
        'ACTIVE': 'Đang sạc',
        'COMPLETED': 'Hoàn thành',
        'STOPPED': 'Đã dừng',
        'ERROR': 'Lỗi'
    };
    return map[status] || status;
}

function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(amount);
}

function getCurrentStationId() {
    // Get from localStorage or page context
    return localStorage.getItem('staffStationId') || document.getElementById('stationSelector')?.value;
}

function showSuccess(message) {
    // Use your notification system
    if (window.showNotification) {
        window.showNotification(message, 'success');
    } else {
        alert(message);
    }
}

function showError(message) {
    // Use your notification system
    if (window.showNotification) {
        window.showNotification(message, 'error');
    } else {
        alert(message);
    }
}

function closeModal(modal) {
    if (modal && modal.remove) {
        modal.remove();
    }
}

// Export for global access
window.staffSessions = {
    loadActiveSessions,
    startSession,
    stopSession,
    confirmStopFromModal,
    viewSessionDetail,
    confirmPayment,
    startAutoRefresh,
    stopAutoRefresh
};
