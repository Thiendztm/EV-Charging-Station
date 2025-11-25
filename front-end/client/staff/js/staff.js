/**
 * Main entry point for Staff Dashboard
 */

import { API_BASE_URL, state } from './data.js';
import { loadStations } from './stations.js';
import { fetchWithAuth, showNotification } from './utils.js';
import { loadActiveSessions, startAutoRefresh, stopAutoRefresh } from './sessions.js';
import { showPaymentModal } from './payments.js';

// Check authentication
function checkAuth() {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
  const userRole = localStorage.getItem('userRole');

  if (!token) {
    alert('Bạn cần đăng nhập để truy cập trang này');
    window.location.href = './login.html';
    return false;
  }

  if (userRole !== 'CS_STAFF' && userRole !== 'ADMIN') {
    alert('Bạn không có quyền truy cập trang này');
    window.location.href = '../index.html';
    return false;
  }

  return true;
}

// Page navigation
function setupNavigation() {
  const menuItems = document.querySelectorAll('.menu li');
  const pageTitle = document.getElementById('pageTitle');

  menuItems.forEach(item => {
    item.addEventListener('click', () => {
      const page = item.dataset.page;

      // Update active menu
      menuItems.forEach(mi => mi.classList.remove('active'));
      item.classList.add('active');

      // Stop auto-refresh when leaving monitoring
      if (state.currentPage === 'monitoring' && page !== 'monitoring') {
        stopAutoRefresh();
      }

      // Update page title and content
      state.currentPage = page;

      switch (page) {
        case 'monitoring':
          pageTitle.textContent = 'Giám sát điểm sạc';
          renderMonitoringPage();
          break;
        case 'payments':
          pageTitle.textContent = 'Quản lý thanh toán';
          renderPaymentsPage();
          break;
        case 'incidents':
          pageTitle.textContent = 'Báo cáo sự cố';
          renderIncidentsPage();
          break;
      }
    });
  });
}

function renderMonitoringPage() {
  const content = document.getElementById('content');

  content.innerHTML = `
    <!-- Stats Cards -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon primary">
          <i class="fas fa-bolt"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">Phiên đang hoạt động</div>
          <div class="stat-value" id="activeSessionsCount">0</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon success">
          <i class="fas fa-charging-station"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">Tổng số charger</div>
          <div class="stat-value" id="totalChargersCount">0</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon warning">
          <i class="fas fa-plug"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">Charger khả dụng</div>
          <div class="stat-value" id="availableChargersCount">0</div>
        </div>
      </div>
    </div>

    <!-- Station Selector -->
    <div class="card">
      <div class="form-group">
        <label for="stationSelect">Chọn trạm sạc:</label>
        <select id="stationSelect" style="padding: 10px; border-radius: 8px; background: var(--card-2); border: 1px solid var(--border); color: var(--text);">
          <option value="">-- Chọn trạm --</option>
        </select>
      </div>
    </div>

    <div id="monitoringContent"></div>

    <!-- Active Sessions Table -->
    <div class="card" style="padding: 0; overflow: hidden;">
      <div style="padding: 16px; border-bottom: 1px solid var(--border);">
        <h3 style="margin: 0; font-size: 16px; color: #c7d2fe;">Phiên sạc đang hoạt động</h3>
      </div>
      <div style="overflow-x: auto;">
        <table class="sessions-table">
          <thead>
            <tr>
              <th>Charger</th>
              <th>Khách hàng</th>
              <th>SOC</th>
              <th>Năng lượng</th>
              <th>Thời gian</th>
              <th>Chi phí</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody id="sessionsTableBody">
            <tr>
              <td colspan="8" style="text-align: center; padding: 40px; color: #666;">
                <i class="fas fa-inbox" style="font-size: 48px; margin-bottom: 16px; display: block;"></i>
                Vui lòng chọn trạm sạc để xem phiên hoạt động
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `;

  // Load stations
  loadStations();

  // Setup station selector change handler
  const stationSelect = document.getElementById('stationSelect');
  stationSelect.addEventListener('change', (e) => {
    const stationId = e.target.value;
    if (stationId) {
      localStorage.setItem('staffStationId', stationId);
      loadActiveSessions(stationId);
      startAutoRefresh(stationId, 10000); // Refresh every 10 seconds
    } else {
      stopAutoRefresh();
    }
  });

  // Auto-select if station was previously selected
  const savedStationId = localStorage.getItem('staffStationId');
  if (savedStationId) {
    setTimeout(() => {
      stationSelect.value = savedStationId;
      stationSelect.dispatchEvent(new Event('change'));
    }, 500);
  }
}

function renderPaymentsPage() {
  const content = document.getElementById('content');

  content.innerHTML = `
    <div class="grid">
      <div class="card">
        <div class="header">
          <span>Xác nhận thanh toán tại chỗ</span>
        </div>
        <p style="color: var(--muted); margin: 12px 0;">
          Nhập mã phiên sạc để xác nhận thanh toán bằng tiền mặt, thẻ hoặc ví điện tử tại trạm.
        </p>
        <form id="cashPaymentForm">
          <div class="form-group">
            <label>Mã phiên sạc:</label>
            <input type="text" id="paymentSessionId" placeholder="Ví dụ: SESSION-123" required />
          </div>
          <button type="submit" class="btn primary" style="width: 100%;">
            <i class="fas fa-search"></i> Tra cứu phiên sạc
          </button>
        </form>
      </div>

      <div class="card">
        <div class="header">
          <span>Hướng dẫn</span>
        </div>
        <ol style="color: var(--muted); font-size: 14px; line-height: 1.8;">
          <li>Nhập mã phiên sạc (có thể lấy từ bảng giám sát)</li>
          <li>Xác nhận thông tin phiên sạc và tổng chi phí</li>
          <li>Chọn phương thức thanh toán (tiền mặt, thẻ, ví)</li>
          <li>Nhận tiền và xác nhận thanh toán</li>
          <li>In hóa đơn cho khách hàng (nếu cần)</li>
        </ol>
      </div>
    </div>
  `;

  // Setup payment form handler
  const form = document.getElementById('cashPaymentForm');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const sessionId = document.getElementById('paymentSessionId').value.trim();
    if (sessionId) {
      showPaymentModal(sessionId);
    }
  });
}

function renderIncidentsPage() {
  const content = document.getElementById('content');

  content.innerHTML = `
    <div class="grid">
      <div class="card">
        <div class="header">
          <span>Báo cáo sự cố mới</span>
        </div>
        <form id="incidentForm">
          <div class="form-group">
            <label>Trạm sạc:</label>
            <select id="incidentStation" required>
              <option value="">-- Chọn trạm --</option>
            </select>
          </div>
          <div class="form-group">
            <label>Mã charger (tùy chọn):</label>
            <input type="text" id="incidentCharger" placeholder="Ví dụ: CHARGER-5" />
          </div>
          <div class="form-group">
            <label>Loại sự cố:</label>
            <select id="incidentType" required>
              <option value="HARDWARE">Lỗi phần cứng</option>
              <option value="SOFTWARE">Lỗi phần mềm</option>
              <option value="CONNECTIVITY">Lỗi kết nối</option>
              <option value="SAFETY">An toàn</option>
              <option value="OTHER">Khác</option>
            </select>
          </div>
          <div class="form-group">
            <label>Mức độ:</label>
            <select id="incidentSeverity" required>
              <option value="LOW">Thấp</option>
              <option value="MEDIUM">Trung bình</option>
              <option value="HIGH">Cao</option>
              <option value="CRITICAL">Nghiêm trọng</option>
            </select>
          </div>
          <div class="form-group">
            <label>Mô tả chi tiết:</label>
            <textarea id="incidentDescription" rows="4" placeholder="Mô tả chi tiết sự cố..." required></textarea>
          </div>
          <button type="submit" class="btn danger" style="width: 100%;">
            <i class="fas fa-exclamation-triangle"></i> Gửi báo cáo
          </button>
        </form>
      </div>

      <div class="card">
        <div class="header">
          <span>Sự cố gần đây</span>
        </div>
        <div id="incidentsList">
          <p style="color: var(--muted); text-align: center; padding: 20px;">
            Chưa có sự cố nào được báo cáo
          </p>
        </div>
      </div>
    </div>
  `;

  // Load station select for incidents
  loadStations();

  // Setup incident form handler
  const form = document.getElementById('incidentForm');
  form.addEventListener('submit', handleIncidentReport);
}

async function handleIncidentReport(e) {
  e.preventDefault();

  const stationId = document.getElementById('incidentStation').value;
  const chargerId = document.getElementById('incidentCharger').value;
  const type = document.getElementById('incidentType').value;
  const severity = document.getElementById('incidentSeverity').value;
  const description = document.getElementById('incidentDescription').value;

  if (!stationId || !description) {
    showNotification('Vui lòng chọn trạm và nhập mô tả sự cố', 'error');
    return;
  }

  try {
    const payload = {
      stationId,
      pointId: chargerId || null,
      type,
      severity,
      description
    };

    const response = await fetchWithAuth(`${API_BASE_URL}/staff/incident`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    const data = await response.json();

    if (response.ok && data.success) {
      showNotification('Đã gửi báo cáo sự cố thành công', 'success');

      // Reset form
      e.target.reset();

      // Hiển thị tóm tắt trong danh sách sự cố gần đây
      const list = document.getElementById('incidentsList');
      if (list) {
        if (list.querySelector('p')) {
          list.innerHTML = '';
        }
        const item = document.createElement('div');
        item.className = 'incident-item';
        item.style.padding = '8px 0';
        item.style.borderBottom = '1px solid var(--border)';
        item.innerHTML = `
          <div style="font-weight: 500;">Trạm ${stationId}${chargerId ? ' - Charger ' + chargerId : ''}</div>
          <div style="font-size: 12px; color: var(--muted);">${type} • ${severity}</div>
          <div style="font-size: 13px; margin-top: 4px;">${description}</div>
        `;
        list.prepend(item);
      }
    } else {
      showNotification(data.error || 'Không thể gửi báo cáo sự cố', 'error');
    }
  } catch (err) {
    console.error('Error reporting incident:', err);
    showNotification('Lỗi kết nối đến máy chủ', 'error');
  }
}

// Refresh button handler
document.getElementById('refreshBtn')?.addEventListener('click', () => {
  switch (state.currentPage) {
    case 'monitoring':
      const stationId = localStorage.getItem('staffStationId');
      if (stationId) {
        loadActiveSessions(stationId);
      } else {
        showNotification('Vui lòng chọn trạm sạc', 'warning');
      }
      break;
    default:
      location.reload();
  }
});

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  if (!checkAuth()) return;

  setupNavigation();
  renderMonitoringPage(); // Default page

  // Display staff name
  const userName = localStorage.getItem('userName') || 'Staff';
  const userEmail = localStorage.getItem('userEmail') || '';
  document.querySelector('.brand span').textContent = userName;

  // Add logout to topbar
  const topbar = document.querySelector('.topbar-actions');
  if (topbar) {
    const logoutBtn = document.createElement('button');
    logoutBtn.className = 'btn danger';
    logoutBtn.innerHTML = '<i class="fas fa-sign-out-alt"></i> Đăng xuất';
    logoutBtn.onclick = () => {
      localStorage.clear();
      window.location.href = '../login.html';
    };
    topbar.appendChild(logoutBtn);
  }
});
