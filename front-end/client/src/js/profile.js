// Configuration
const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080/api';

// DOM Elements
const userInfoSection = document.getElementById('userInfoSection');
const editBtn = document.getElementById('editBtn');
const infoView = document.getElementById('infoView');
const infoEdit = document.getElementById('infoEdit');
const cancelEdit = document.getElementById('cancelEdit');
const editProfileForm = document.getElementById('editProfileForm');
const avatarImg = document.getElementById('avatarImg');
const previewImg = document.getElementById('previewImg');
const editAvatar = document.getElementById('editAvatar');
const displayName = document.getElementById('displayName');
const displayEmail = document.getElementById('displayEmail');
const displayPhone = document.getElementById('displayPhone');
const displayVehicle = document.getElementById('displayVehicle');
const profileWalletBalance = document.getElementById('profileWalletBalance');
const editName = document.getElementById('editName');
const editEmail = document.getElementById('editEmail');
const editPhone = document.getElementById('editPhone');
const editVehicle = document.getElementById('editVehicle');

// === Wallet Balance Management ===
async function fetchAndUpdateWalletBalance() {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
    if (!token) return 0;

    try {
        const response = await fetch(`${API_BASE_URL}/profile/wallet`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            const balance = data.balance || 0;
            localStorage.setItem('walletBalance', balance.toString());
            return balance;
        }
    } catch (error) {
        console.error('Error fetching wallet balance:', error);
    }

    // Fallback to localStorage
    return parseFloat(localStorage.getItem('walletBalance') || '0');
}

function updateProfileWalletDisplay(balance) {
    if (profileWalletBalance) {
        profileWalletBalance.textContent = balance.toLocaleString('vi-VN') + 'đ';

        // Add color coding based on balance
        if (balance >= 1000000) {
            profileWalletBalance.style.color = '#28a745'; // Green for good balance
        } else if (balance >= 500000) {
            profileWalletBalance.style.color = '#fd7e14'; // Orange for medium balance
        } else {
            profileWalletBalance.style.color = '#dc3545'; // Red for low balance
        }
    }
}

async function displayProfile() {
    try {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
        if (!token) {
            console.error('No token found');
            alert('Vui lòng đăng nhập để xem hồ sơ');
            window.location.href = 'login.html';
            return;
        }

        const response = await fetch(`${API_BASE_URL}/profile`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const data = await response.json();
            console.log('Profile data received:', data);

            // Lấy thông tin người dùng từ response (ưu tiên alias hoTen/email/sdt)
            const user = data.user || {};
            const nameFromAlias = data.hoTen;
            const nameFromUser = `${user.firstName || ''} ${user.lastName || ''}`.trim();
            const finalName = nameFromAlias || nameFromUser || 'Người dùng';

            const finalEmail = data.email || user.email || '';
            const finalPhone = data.sdt || user.phone || '';

            // Cập nhật khu vực hiển thị
            if (displayName) displayName.textContent = finalName;
            if (displayEmail) displayEmail.textContent = finalEmail || 'Chưa cập nhật';
            if (displayPhone) displayPhone.textContent = finalPhone || 'Chưa cập nhật';

            // Đồng bộ giá trị ban đầu cho form edit
            if (editName) editName.value = finalName;
            if (editEmail) editEmail.value = finalEmail;
            if (editPhone) editPhone.value = finalPhone;

            // Fetch and update wallet balance separately
            const balance = await fetchAndUpdateWalletBalance();
            updateProfileWalletDisplay(balance);

            // Load vehicle data separately
            await loadVehicleData();

        } else if (response.status === 401) {
            alert('Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.');
            localStorage.removeItem('accessToken');
            localStorage.removeItem('jwt_token');
            window.location.href = 'login.html';
        } else {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    } catch (error) {
        console.error('Error displaying profile:', error);
        alert('Có lỗi xảy ra khi tải thông tin hồ sơ');
    }
}

// Load vehicle data
async function loadVehicleData() {
    try {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
        const response = await fetch(`${API_BASE_URL}/profile/vehicle`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const vehicle = await response.json();
            console.log('Vehicle data received:', vehicle);

            // Update vehicle form fields
            if (vehicle.licensePlate) {
                document.getElementById('editLicensePlate').value = vehicle.licensePlate;
            }
            if (vehicle.model) document.getElementById('editVehicleModel').value = vehicle.model;
            if (vehicle.connectorType) document.getElementById('editConnectorType').value = vehicle.connectorType;
            if (vehicle.batteryCapacity) document.getElementById('editBatteryCapacity').value = vehicle.batteryCapacity;

            // Update vehicle info display with formatted string
            const vehicleInfo = [];
            if (vehicle.licensePlate) vehicleInfo.push(vehicle.licensePlate);
            if (vehicle.model) vehicleInfo.push(vehicle.model);
            if (vehicle.batteryCapacity) vehicleInfo.push(`${vehicle.batteryCapacity} kWh`);
            if (vehicleInfo.length > 0) {
                document.getElementById('displayVehicle').textContent = vehicleInfo.join(' - ');
            }
        } else if (response.status === 404) {
            // No vehicle data yet - this is okay
            console.log('No vehicle data found');
        } else if (response.status === 401) {
            console.error('Unauthorized access to vehicle data');
        }
    } catch (error) {
        console.error('Error loading vehicle data:', error);
    }
}

// Save vehicle data
async function saveVehicleData() {
    try {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
        const vehicleData = {
            licensePlate: document.getElementById('editLicensePlate').value,
            model: document.getElementById('editVehicleModel').value,
            connectorType: document.getElementById('editConnectorType').value,
            batteryCapacity: parseFloat(document.getElementById('editBatteryCapacity').value) || 0
        };

        const response = await fetch(`${API_BASE_URL}/profile/vehicle`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(vehicleData)
        });

        if (response.ok) {
            const result = await response.json();
            console.log('Vehicle data saved:', result);

            // Update display with formatted string
            const vehicleInfo = [];
            if (vehicleData.licensePlate) vehicleInfo.push(vehicleData.licensePlate);
            if (vehicleData.model) vehicleInfo.push(vehicleData.model);
            if (vehicleData.batteryCapacity) vehicleInfo.push(`${vehicleData.batteryCapacity} kWh`);
            if (vehicleInfo.length > 0) {
                document.getElementById('displayVehicle').textContent = vehicleInfo.join(' - ');
            }
            return true;
        } else {
            throw new Error(`Failed to save vehicle data: ${response.status}`);
        }
    } catch (error) {
        console.error('Error saving vehicle data:', error);
        alert('Có lỗi xảy ra khi lưu thông tin xe');
        return false;
    }
}

async function loadChargingHistorySummary() {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
    if (!token) return;

    try {
        const response = await fetch(`${API_BASE_URL}/history/charging?limit=5`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            return;
        }

        const data = await response.json();
        const sessions = Array.isArray(data) ? data : (data.history || data.sessions || []);

        const listContent = document.getElementById('listContent');
        const historyCount = document.getElementById('historyCount');
        if (!listContent || !historyCount) return;

        if (sessions.length === 0) {
            historyCount.textContent = '0 giao dịch';
            listContent.innerHTML = '<p class="address">Chưa có lịch sử sạc</p>';
            return;
        }

        historyCount.textContent = sessions.length + ' giao dịch';

        listContent.innerHTML = sessions.map(session => {
            const start = session.startTime ? new Date(session.startTime) : null;
            const dateStr = start ? start.toLocaleString('vi-VN') : '';
            const energy = session.energyConsumed || 0;
            const cost = session.totalCost || 0;
            const stationName = session.stationName || (session.station && session.station.name) || 'Trạm sạc';
            const completed = session.status === 'COMPLETED';
            const statusClass = completed ? 'available' : 'busy';
            const statusText = completed ? 'Hoàn thành' : 'Đang xử lý';

            return `
                <div class="station-card">
                    <div class="station-header">
                        <h4>${stationName}</h4>
                        <span class="status ${statusClass}">${statusText}</span>
                    </div>
                    <p class="address"><i class="fa-solid fa-calendar-days"></i> ${dateStr}</p>
                    <p class="details"><i class="fa-solid fa-bolt"></i> ${energy.toFixed(1)} kWh</p>
                    <p class="distance charge-cost">-${cost.toLocaleString('vi-VN')}đ</p>
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('Error loading charging history summary:', error);
    }
}

// Load profile data on page load
document.addEventListener('DOMContentLoaded', () => {
    displayProfile();
    loadChargingHistorySummary();
});

// Toggle edit mode
editBtn.addEventListener('click', () => {
    infoView.style.display = 'none';
    infoEdit.style.display = 'block';
    editName.value = displayName.textContent;
    editEmail.value = displayEmail.textContent;
    editPhone.value = displayPhone.textContent;
    // Vehicle fields are already populated by loadVehicleData()
});

cancelEdit.addEventListener('click', () => {
    infoEdit.style.display = 'none';
    infoView.style.display = 'block';
});

// Preview and upload image from device
editAvatar.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function (e) {
            previewImg.src = e.target.result;
            avatarImg.src = e.target.result; // Cập nhật ảnh ngay khi chọn
        };
        reader.readAsDataURL(file); // Truy cập bộ nhớ để tải ảnh
    }
});

// Save changes
editProfileForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    // Save vehicle data first
    const vehicleSaved = await saveVehicleData();

    if (vehicleSaved) {
        displayName.textContent = editName.value;
        displayEmail.textContent = editEmail.value;
        displayPhone.textContent = editPhone.value;

        infoEdit.style.display = 'none';
        infoView.style.display = 'block';
        alert('Hồ sơ đã được cập nhật thành công!');
    }
    // TODO: Gọi API để lưu thông tin người dùng (name, email, phone)
});