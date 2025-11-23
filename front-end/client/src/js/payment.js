// Configuration
const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080/api';

let currentPlan = 'usage';   // usage | once | monthly
let hideEnergy = false;

const totalEl = document.getElementById('totalAmount');
const energyEl = document.querySelector('.detail-item:nth-child(3)');
const pricePerKwhEl = document.querySelector('.detail-item:nth-child(4)');
const detailItemPrice = document.querySelector('.detail-item-price');
const paymentDetails = document.querySelector('.payment-details.card');
const otpSection = document.getElementById('otpSection');
const otpInput = document.getElementById('otp');

// === 1. Thêm dòng "Gói sạc" vào đầu danh sách chi tiết ===
const planTitleEl = document.createElement('div');
planTitleEl.className = 'detail-item';
planTitleEl.innerHTML = `<span>Gói sạc:</span> <strong id="selectedPlan">Chưa chọn</strong>`;
paymentDetails.querySelector('.box__detail-items').prepend(planTitleEl);

// === Wallet Balance Management - Integrated with Backend ===
async function fetchWalletBalance() {
    const token = localStorage.getItem('accessToken'); // (đã sửa)
    if (!token) {
        console.error('No auth token found');
        return 0;
    }

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
            // Update localStorage cache
            localStorage.setItem('walletBalance', balance.toString());
            return balance;
        } else {
            console.error('Failed to fetch wallet balance');
            return parseFloat(localStorage.getItem('walletBalance') || '0');
        }
    } catch (error) {
        console.error('Error fetching wallet:', error);
        // Fallback to cached value
        return parseFloat(localStorage.getItem('walletBalance') || '0');
    }
}

async function updateWalletDisplay() {
    const balance = await fetchWalletBalance();
    const balanceEl = document.getElementById('walletBalance');
    if (balanceEl) {
        balanceEl.textContent = balance.toLocaleString('vi-VN') + 'đ';
    }
}

// Tạo phiên sạc giả đã hoàn thành sau khi thanh toán thành công
async function simulateChargingSessionForCurrentBooking(total, method) {
    const token = localStorage.getItem('accessToken');
    const chargerId = localStorage.getItem('currentChargerId');

    if (!token || !chargerId) {
        console.warn('Thiếu token hoặc currentChargerId, bỏ qua giả lập phiên sạc');
        return;
    }

    const price = window.currentStationPrice || 0;
    let energyKwh = 20.0;
    if (price > 0 && total > 0) {
        energyKwh = total / price;
    }

    const payload = {
        chargerId: parseInt(chargerId, 10),
        energyKwh: energyKwh,
        durationMinutes: 60,
        paymentMethod: method || 'SIMULATED'
    };

    try {
        const response = await fetch(`${API_BASE_URL}/payment/simulate-charge-session`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            console.error('Failed to simulate charging session:', response.status);
            return;
        }

        const data = await response.json();
        console.log('Simulated charging session created:', data);
    } catch (error) {
        console.error('Error simulating charging session:', error);
    }
}

// === 2. DOMContentLoaded - Khôi phục dữ liệu + điền thông tin trạm ===
document.addEventListener('DOMContentLoaded', () => {
    // --- Load và hiển thị số dư ví ---
    updateWalletDisplay();

    // --- Điền thông tin trạm sạc từ localStorage ---
    const saved = localStorage.getItem('bookingStation');
    if (!saved) {
        alert('Không có thông tin trạm sạc. Vui lòng đặt lại.');
        window.location.href = 'index.html';
        return;
    }

    let station;
    try {
        station = JSON.parse(saved);
    } catch (e) {
        alert('Dữ liệu trạm sạc bị lỗi!');
        window.location.href = 'index.html';
        return;
    }

    document.getElementById('stationName').textContent = station.name;
    document.getElementById('connectorType').textContent = station.connectorDisplay;
    document.getElementById('pricePerKWh').textContent = station.priceDisplay;
    document.getElementById('energyAmount').textContent = '0 kWh';

    // Lưu giá để tính toán sau
    window.currentStationPrice = station.price;

    // --- Khôi phục gói đã chọn trước đó ---
    const savedPlan = localStorage.getItem('selectedPlan');
    const savedAmount = localStorage.getItem('planAmount');
    const savedHide = localStorage.getItem('hideEnergy') === 'true';
    const savedCurrentPlan = localStorage.getItem('currentPlan') || 'usage';

    if (savedPlan) {
        document.getElementById('selectedPlan').textContent = savedPlan;
        totalEl.textContent = savedAmount || '0đ';
        hideEnergy = savedHide;
        currentPlan = savedCurrentPlan;

        if (hideEnergy) {
            energyEl.style.display = 'none';
            pricePerKwhEl.style.display = 'none';
            if (detailItemPrice) detailItemPrice.style.display = 'none';
        } else {
            energyEl.style.display = '';
            pricePerKwhEl.style.display = '';
            if (detailItemPrice) detailItemPrice.style.display = '';
        }
    }

    // Cập nhật tổng tiền lần đầu
    updateTotalAmount();
});

// === 3. Xử lý chọn gói sạc ===
document.querySelectorAll('.btn-option').forEach((btn, index) => {
    btn.addEventListener('click', () => {
        let planName = '';
        let planPrice = '';
        hideEnergy = false;
        currentPlan = 'usage';

        if (index === 0) {
            // Gói Linh Hoạt
            planName = 'Gói Linh Hoạt';
            planPrice = '0đ';
            hideEnergy = false;
            currentPlan = 'usage';
        } else if (index === 1) {
            // Gói Một Lần
            planName = 'Gói Một Lần';
            planPrice = document.querySelectorAll('.option-price')[0].textContent;
            hideEnergy = true;
            currentPlan = 'once';
        } else if (index === 2) {
            // Gói Tháng
            planName = 'Gói Tháng';
            planPrice = document.querySelectorAll('.option-price')[1].textContent;
            hideEnergy = true;
            currentPlan = 'monthly';
        }

        // Cập nhật giao diện
        document.getElementById('selectedPlan').textContent = planName;
        totalEl.textContent = planPrice;

        // Ẩn/hiện chi tiết năng lượng
        if (hideEnergy) {
            energyEl.style.display = 'none';
            pricePerKwhEl.style.display = 'none';
            if (detailItemPrice) detailItemPrice.style.display = 'none';
        } else {
            energyEl.style.display = '';
            pricePerKwhEl.style.display = '';
            if (detailItemPrice) detailItemPrice.style.display = '';
        }

        // Lưu vào localStorage
        localStorage.setItem('selectedPlan', planName);
        localStorage.setItem('planAmount', planPrice);
        localStorage.setItem('hideEnergy', hideEnergy);
        localStorage.setItem('currentPlan', currentPlan);

        // Cập nhật tổng tiền
        updateTotalAmount();
    });
});

// === 4. Hàm cập nhật tổng tiền ===
function updateTotalAmount() {
    let amount = 0;

    if (currentPlan === 'once') {
        amount = 60000;
    } else if (currentPlan === 'monthly') {
        amount = 1000000;
    } else if (currentPlan === 'usage') {
        // Có thể mở rộng: nhập kWh → tính tiền
        amount = 51000; // tạm tính ví dụ
    }

    if (!hideEnergy || currentPlan === 'usage') {
        totalEl.textContent = amount.toLocaleString('vi-VN') + 'đ';
    }
}

// === 5. Xử lý thanh toán - Integrated with Backend ===
document.getElementById('paymentForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();

    const method = document.querySelector('input[name="method"]:checked').value;

    // Cập nhật lại tổng tiền trước khi thanh toán
    updateTotalAmount();
    const total = parseInt(totalEl.textContent.replace(/\D/g, '')) || 0;

    if (method === 'ev') {
        // Hiển thị loading
        const submitBtn = document.querySelector('.btn-pay');
        const originalText = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang xử lý...';
        submitBtn.disabled = true;

        try {
            // Get current balance first
            const currentBalance = await fetchWalletBalance();

            if (currentBalance < total) {
                alert(`Số dư ví không đủ!\nSố dư hiện tại: ${currentBalance.toLocaleString('vi-VN')}đ\nSố tiền cần thanh toán: ${total.toLocaleString('vi-VN')}đ`);
                submitBtn.innerHTML = originalText;
                submitBtn.disabled = false;
                return;
            }

            // Giả lập thanh toán + tạo phiên sạc hoàn thành (không trừ ví thực tế trên server)
            await simulateChargingSessionForCurrentBooking(total, 'WALLET');

            const newBalance = currentBalance - total;
            localStorage.setItem('walletBalance', String(newBalance));
            updateWalletDisplay();

            alert(`Thanh toán (giả lập) thành công bằng Ví EV!\nĐã trừ: ${total.toLocaleString('vi-VN')}đ\nSố dư còn lại (tạm tính): ${newBalance.toLocaleString('vi-VN')}đ`);

            localStorage.setItem('bookingStatus', 'success');
            // Clear session if exists
            localStorage.removeItem('currentSessionId');
            window.location.href = 'index.html';
        } catch (error) {
            console.error('Payment error:', error);
            alert('Có lỗi xảy ra khi xử lý thanh toán! Vui lòng thử lại.');
        } finally {
            // Restore button
            submitBtn.innerHTML = originalText;
            submitBtn.disabled = false;
        }
    } else {
        // Thanh toán ngân hàng/thẻ → hiện OTP (sẽ tích hợp payment gateway sau)
        otpSection.style.display = 'block';
        otpInput.value = '';
        otpInput.focus();

        // Gỡ listener cũ để tránh bị ghi đè
        otpInput.oninput = null;
        otpInput.addEventListener('input', async function handler() {
            if (this.value.length === 6) {
                // TODO: Verify OTP with backend
                const submitBtn = document.querySelector('.btn-pay');
                submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Xác thực OTP...';
                submitBtn.disabled = true;

                // Simulate OTP verification (replace with real API call)
                setTimeout(async () => {
                    alert(`Thanh toán thành công bằng ${method === 'bank' ? 'Ngân hàng' : 'Thẻ tín dụng'}!`);

                    // Tạo phiên sạc giả sau khi thanh toán thành công
                    const paymentMethod = method === 'bank' ? 'BANK_TRANSFER' : 'CARD';
                    await simulateChargingSessionForCurrentBooking(total, paymentMethod);

                    otpSection.style.display = 'none';
                    this.value = '';
                    otpInput.removeEventListener('input', handler);
                    localStorage.setItem('bookingStatus', 'success');
                    window.location.href = 'index.html';
                }, 1000);
            }
        });
    }
});

// === 6. Ẩn OTP khi đổi phương thức thanh toán ===
document.querySelectorAll('input[name="method"]').forEach((radio) => {
    radio.addEventListener('change', () => {
        otpSection.style.display = 'none';
    });
});

// === 7. Hủy thanh toán ===
document.querySelector('.btn-report')?.addEventListener('click', () => {
    localStorage.setItem('bookingStatus', 'cancel');
    window.location.href = 'index.html';
});