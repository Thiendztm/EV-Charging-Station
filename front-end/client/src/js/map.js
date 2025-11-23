function tram(name, lat, lng, connector, status, power, price, address, distance, stationId, totalChargers, availableChargers) {
    this.name = name;
    this.lat = lat;
    this.lng = lng;
    this.id = (stationId !== undefined && stationId !== null) ? stationId : `${lat},${lng}`;
    this.connector = connector;
    this.status = status;
    this.power = power;
    this.price = price;
    this.address = address;
    this.distance = distance;
    this.totalChargers = totalChargers || 0;
    this.availableChargers = availableChargers || 0;

    this.hienThiThongTin = function () {
        console.log(`--- Thông tin trạm sạc ---`);
        console.log(`Tên: ${this.name}`);
        console.log(`Địa chỉ: ${this.address}`);
        console.log(`Trạng thái: ${this.status === 'available' ? 'Còn trống' : 'Đang bận'}`);
        console.log(`Loại sạc: ${this.connector} (${this.power}kW)`);
        console.log(`Giá: ${this.price}đ/kWh`);
    };

    this.conTrong = function () {
        return this.status === 'available';
    };

    this.capNhatTrangThai = function (newStatus) {
        this.status = newStatus;
        console.log(`Trạm ${this.name} đã được cập nhật trạng thái thành: ${newStatus}`);
    };
}

// =====================================================
// LEAFLET MAP GLOBALS
// =====================================================
let map;
let markers = [];
let markerLayer;
let userMarker = null;
let isMapLoaded = false;

const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080/api';

// =====================================================
// CUSTOM ICONS FOR LEAFLET (chỉ khởi tạo sau khi Leaflet loaded)
// =====================================================
let availableIcon, occupiedIcon, userIcon;

function initIcons() {
    availableIcon = L.divIcon({
        className: 'custom-marker',
        html: `<div style="
        background: #00FF00;
        width: 32px;
        height: 32px;
        border-radius: 50% 50% 50% 0;
        transform: rotate(-45deg);
        border: 3px solid #FFFFFF;
        box-shadow: 0 3px 10px rgba(0,0,0,0.3);
        display: flex;
        align-items: center;
        justify-content: center;
    ">
        <span style="transform: rotate(45deg); color: white; font-size: 16px;">⚡</span>
    </div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 32],
        popupAnchor: [0, -32]
    });

    occupiedIcon = L.divIcon({
        className: 'custom-marker',
        html: `<div style="
        background: #FF0000;
        width: 32px;
        height: 32px;
        border-radius: 50% 50% 50% 0;
        transform: rotate(-45deg);
        border: 3px solid #FFFFFF;
        box-shadow: 0 3px 10px rgba(0,0,0,0.3);
        display: flex;
        align-items: center;
        justify-content: center;
    ">
        <span style="transform: rotate(45deg); color: white; font-size: 16px;">🔌</span>
    </div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 32],
        popupAnchor: [0, -32]
    });

    userIcon = L.divIcon({
        className: 'user-marker',
        html: `<div style="
        background: #007bff;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        border: 3px solid #FFFFFF;
        box-shadow: 0 0 0 2px #007bff, 0 3px 10px rgba(0,0,0,0.3);
    "></div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
    });
}

// Simple inline warning renderer for station loading issues
function showStationsWarning(message) {
    try {
        const list = document.getElementById('listContent');
        if (list) {
            const note = document.createElement('div');
            note.innerHTML = `
                <div style="
                    padding: 12px 14px; margin-bottom: 12px;
                    background: #fff3cd; color: #856404;
                    border: 1px solid #ffeeba; border-radius: 8px;
                    font-family: 'Inter', sans-serif; font-size: 14px;">
                    ⚠️ ${message}
                </div>`;
            list.prepend(note);
        }
    } catch (e) { /* no-op */ }
}

// =====================================================
// LOAD STATIONS FROM BACKEND API
// =====================================================
async function loadStationsFromAPI() {
    try {
        const response = await fetch(`${API_BASE_URL}/stations`);
        if (!response.ok) {
            throw new Error(`Tải trạm thất bại (HTTP ${response.status})`);
        }
        const data = await response.json();

        if (data && Array.isArray(data.stations)) {
            return data.stations.map(station => {
                // connectorTypes is a map; pick the first key as representative
                const connector = station.connectorTypes && Object.keys(station.connectorTypes).length > 0
                    ? Object.keys(station.connectorTypes)[0]
                    : 'Type 2';

                return new tram(
                    station.name,
                    station.latitude,
                    station.longitude,
                    connector,
                    station.availableChargers > 0 ? 'available' : 'occupied',
                    50,
                    3500,
                    station.address,
                    '0km',
                    station.id,
                    station.totalChargers || 0,
                    station.availableChargers || 0
                );
            });
        } else {
            console.warn('Định dạng API không hợp lệ: thiếu mảng stations');
            showStationsWarning('Không đọc được danh sách trạm từ máy chủ.');
            return [];
        }
    } catch (error) {
        console.error('Error loading stations from API:', error);
        showStationsWarning(error.message || 'Không thể tải danh sách trạm.');
        return [];
    }
}

// Fallback stations if API fails
function getFallbackStations() {
    return [
        new tram("Trạm sạc Bình Thạnh 1", 10.8231, 106.6297, "CCS", "available", 50, 3500, "123 Nguyễn Văn Cừ, Bình Thạnh, TP.HCM", "1.2km", null, 6, 4),
        new tram("Trạm sạc Quận 1", 10.7769, 106.7009, "AC", "available", 75, 4000, "789 Nguyễn Huệ, Q.1, TP.HCM", "2.1km", null, 8, 6),
        new tram("Trạm Sạc Sài Gòn 3", 10.770, 106.690, "CHAdeMO", "available", 100, 2500, "3 Pasteur, Q.1, TP.HCM", "0.8km", null, 5, 3)
    ];
}

// =====================================================
// MAIN INITIALIZATION FUNCTION - LEAFLET
// =====================================================
async function initMap() {
    console.log('🗺️ Initializing Leaflet Map...');

    try {
        // Kiểm tra Leaflet library
        if (typeof L === 'undefined') {
            throw new Error("Leaflet library not loaded!");
        }

        // Khởi tạo icons sau khi Leaflet loaded
        initIcons();

        // Tạo map với OpenStreetMap
        map = L.map('map', {
            center: [10.7769, 106.7009], // TP.HCM
            zoom: 12,
            zoomControl: true,
            scrollWheelZoom: true
        });

        // Thêm OpenStreetMap tile layer
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19,
            minZoom: 3
        }).addTo(map);

        // Tạo layer group cho markers
        markerLayer = L.layerGroup().addTo(map);

        isMapLoaded = true;
        console.log('✅ Leaflet Map loaded successfully');

        // Load stations from API
        let stations = await loadStationsFromAPI();
        console.log(`📍 Loaded ${stations.length} stations (tổng)`);

        // === Chỉ hiển thị các trạm có thể đặt chỗ (availableChargers > 0) (đã sửa) ===
        const bookableStations = stations.filter(s => (s.availableChargers || 0) > 0);
        if (bookableStations.length) {
            console.log(`✅ Hiển thị ${bookableStations.length} trạm có thể đặt chỗ`);
            stations = bookableStations;
        } else {
            console.warn('⚠️ Không có trạm khả dụng nào – hiển thị toàn bộ');
        }

        // Create markers for each station
        const bounds = [];
        stations.forEach((station) => {
            const icon = station.status === 'available' ? availableIcon : occupiedIcon;

            const marker = L.marker([station.lat, station.lng], { icon: icon })
                .addTo(markerLayer);

            // Create popup content
            const popupContent = `
                <div class="info-window" style="font-family: 'Inter', sans-serif; min-width: 280px;">
                    <h3 style="margin: 0 0 12px 0; font-size: 18px; font-weight: 700; color: #333;">${station.name}</h3>
                    <div class="info-details" style="font-size: 14px; color: #666; line-height: 1.8;">
                        <p style="margin: 6px 0;"><strong>Loại:</strong> ${station.connector}</p>
                        <p style="margin: 6px 0;">
                            <strong>Trạng thái:</strong> 
                            <span style="
                                display: inline-block;
                                padding: 4px 12px;
                                border-radius: 12px;
                                font-size: 12px;
                                font-weight: 600;
                                ${station.status === 'available'
                    ? 'background: #d4edda; color: #155724;'
                    : 'background: #f8d7da; color: #721c24;'}
                            ">
                                ${station.status === 'available' ? '✓ Trống' : '✗ Đang dùng'}
                            </span>
                        </p>
                        <p style="margin: 6px 0;"><strong>Công suất:</strong> ${station.power}kW</p>
                        <p style="margin: 6px 0;"><strong>Sạc khả dụng:</strong> ${station.availableChargers}/${station.totalChargers}</p>
                        <p style="margin: 6px 0;"><strong>Giá:</strong> ${station.price}đ/kWh</p>
                        <p style="margin: 6px 0;"><strong>Địa chỉ:</strong> ${station.address}</p>
                    </div>
                    <div class="action-row" style="margin-top: 15px; display: flex; gap: 10px;">
                        ${station.status === 'available'
                    ? `<button onclick="startBooking('${station.id}')" style="
                                flex: 1;
                                padding: 10px 20px;
                                background: #4CAF50;
                                color: white;
                                border: none;
                                border-radius: 8px;
                                font-size: 14px;
                                font-weight: 600;
                                cursor: pointer;
                                transition: background 0.3s;
                            " onmouseover="this.style.background='#45a049'" onmouseout="this.style.background='#4CAF50'">
                                📍 Đặt chỗ
                            </button>`
                    : `<button disabled style="
                                flex: 1;
                                padding: 10px 20px;
                                background: #ccc;
                                color: #666;
                                border: none;
                                border-radius: 8px;
                                font-size: 14px;
                                font-weight: 600;
                                cursor: not-allowed;
                            ">
                                ✗ Đã đặt chỗ
                            </button>`
                }
                        <button onclick="map.setView([${station.lat}, ${station.lng}], 16)" style="
                            padding: 10px 20px;
                            background: #007bff;
                            color: white;
                            border: none;
                            border-radius: 8px;
                            font-size: 14px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: background 0.3s;
                        " onmouseover="this.style.background='#0056b3'" onmouseout="this.style.background='#007bff'">
                            🔍 Zoom
                        </button>
                    </div>
                </div>
            `;

            marker.bindPopup(popupContent, { maxWidth: 350 });

            markers.push({ marker, station });
            bounds.push([station.lat, station.lng]);
        });

        // Fit map to show all markers
        if (bounds.length > 0) {
            map.fitBounds(bounds, { padding: [50, 50] });
        }

        updateStationList();
        initSearchFunctionality(stations);

        // Gán sự kiện cho nút vị trí hiện tại
        const locBtn = document.getElementById("currentLocation");
        if (locBtn) locBtn.addEventListener("click", getCurrentLocation);

        // Áp dụng kết quả thanh toán (nếu có)
        applyBookingFromStorage();

        console.log('✅ Map initialization complete!');

    } catch (error) {
        console.error("❌ Leaflet Map Error:", error.message);
        showMapError(error.message);
    }
}

// Hiển thị lỗi nếu map không load được
function showMapError(errorMessage) {
    const mapContainer = document.getElementById("map");
    if (mapContainer) {
        mapContainer.innerHTML = `
            <div style="
                display: flex; 
                align-items: center; 
                justify-content: center; 
                height: 100%; 
                background: #fff3cd; 
                color: #856404; 
                flex-direction: column; 
                border: 1px solid #ffeaa7; 
                border-radius: 8px; 
                padding: 20px;
                font-family: 'Inter', sans-serif;
            ">
                <h3 style="color: #856404; margin-bottom: 15px;">⚠️ Lỗi tải bản đồ</h3>
                <p style="text-align: center; margin-bottom: 15px;">
                    <strong>${errorMessage}</strong>
                </p>
                <p style="font-size: 14px; color: #6c757d;">
                    Vui lòng kiểm tra kết nối mạng và thử lại.
                </p>
            </div>
        `;
    }
}

// =====================================================
// GET CURRENT LOCATION (GEOLOCATION API)
// =====================================================
async function getCurrentLocation() {
    if (!navigator.geolocation || !isMapLoaded) {
        alert("Trình duyệt không hỗ trợ geolocation hoặc map chưa load.");
        return;
    }

    console.log('📍 Getting current location...');

    try {
        const position = await new Promise((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
                enableHighAccuracy: true,
                timeout: 10000,
                maximumAge: 0
            });
        });

        const userLocation = [position.coords.latitude, position.coords.longitude];

        // Remove old user marker if exists
        if (userMarker) {
            markerLayer.removeLayer(userMarker);
        }

        // Add new user marker (blue dot)
        userMarker = L.marker(userLocation, { icon: userIcon })
            .addTo(markerLayer)
            .bindPopup(`
                <div style="text-align: center; font-family: 'Inter', sans-serif;">
                    <strong style="color: #007bff;">📍 Vị trí của bạn</strong>
                </div>
            `)
            .openPopup();

        // Pan and zoom to user location
        map.setView(userLocation, 14);

        console.log('✅ User location found:', userLocation);

        // Calculate distances to all stations
        calculateDistances(position.coords.latitude, position.coords.longitude);
        updateStationList();

    } catch (error) {
        console.error('❌ Geolocation error:', error);
        alert("Không thể lấy vị trí hiện tại: " + error.message);
    }
}

// Calculate distances from user to all stations
function calculateDistances(userLat, userLng) {
    markers.forEach(({ station }) => {
        const distance = getDistance(userLat, userLng, station.lat, station.lng);
        station.distance = distance < 1 ? `${Math.round(distance * 1000)}m` : `${distance.toFixed(1)}km`;
    });
}

// Haversine formula to calculate distance between two coordinates
function getDistance(lat1, lng1, lat2, lng2) {
    const R = 6371; // Earth radius in km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// =====================================================
// SEARCH & FILTER FUNCTIONALITY
// =====================================================
function initSearchFunctionality(stations) {
    const searchInput = document.getElementById("searchInput");
    if (!searchInput) return;

    searchInput.addEventListener("input", () => {
        filterMarkers(stations);
        updateStationList();
    });
}

function filterMarkers(stations) {
    const searchQuery = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

    markers.forEach(({ marker, station }) => {
        const matchesSearch = !searchQuery ||
            station.name.toLowerCase().includes(searchQuery) ||
            station.address.toLowerCase().includes(searchQuery);

        if (matchesSearch) {
            markerLayer.addLayer(marker);
        } else {
            markerLayer.removeLayer(marker);
        }
    });
}

// =====================================================
// UPDATE STATION LIST SIDEBAR
// =====================================================
function updateStationList() {
    const listContent = document.getElementById("listContent");
    if (!listContent) return;

    let html = '';
    const visibleStations = markers.filter(item => markerLayer.hasLayer(item.marker));

    visibleStations.forEach(({ station }) => {
        const isAvailable = station.status === 'available';

        html += `
            <div class="station-card">
                <div class="station-header">
                    <h4>${station.name}</h4>
                    <span class="status ${station.status.toLowerCase()}">
                        ${isAvailable ? '✓ Trống' : '✗ Đang dùng'}
                    </span>
                </div>
                <p class="address">${station.address}</p>
                <p class="details">
                    <i class="fa-solid fa-bolt"></i> ${station.connector} | ${station.power}kW | ${station.price}đ/kWh
                </p>
                <p class="details" style="margin:0; color:var(--muted); font-size:1.3rem;">
                    <i class="fa-solid fa-charging-station"></i> Khả dụng: ${station.availableChargers}/${station.totalChargers}
                </p>
                <p class="distance">📍 ${station.distance}</p>
                <div class="action-row">
                    ${isAvailable
                ? `<button data-id="${station.id}" class="btn-book" onclick="startBooking('${station.id}')">Đặt chỗ</button>`
                : `<button class="btn-busy" disabled>Đã đặt chỗ</button>`
            }
                </div>
            </div>
        `;
    });

    listContent.innerHTML = html;
    const countEl = document.getElementById("stationCount");
    if (countEl) countEl.textContent = `${visibleStations.length} trạm`;
}

// =====================================================
// BOOKING FUNCTIONALITY
// =====================================================
async function startBooking(stationId) {
    // Use accessToken saved by auth.js
    const token = localStorage.getItem('accessToken');

    if (!token) {
        if (confirm('Bạn cần đăng nhập để đặt chỗ. Chuyển đến trang đăng nhập?')) {
            window.location.href = 'login.html?redirect=index.html';
        }
        return;
    }

    const entry = markers.find(m => String(m.station.id) === String(stationId));
    if (!entry || entry.station.status === 'busy') {
        alert("Trạm đang bận hoặc không tồn tại!");
        return;
    }

    showBookingModal(entry.station);
}

function showBookingModal(station) {
    const modal = document.createElement('div');
    modal.id = 'bookingModal';
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10000;
    `;

    const now = new Date();
    const minTime = new Date(now.getTime() + 30 * 60000);
    const maxTime = new Date(now.getTime() + 24 * 60 * 60000);

    modal.innerHTML = `
        <div style="background: white; padding: 30px; border-radius: 16px; max-width: 500px; width: 90%; font-family: 'Inter', sans-serif; color:#0f172a;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; font-size: 24px; font-weight: 700; color:#0f172a;">Đặt chỗ</h2>
                <button onclick="closeBookingModal()" style="background: none; border: none; font-size: 24px; cursor: pointer;">&times;</button>
            </div>
            
            <div style="background: #f5f5f5; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                <h3 style="margin: 0 0 10px 0; font-size: 18px; font-weight: 600;">${station.name}</h3>
                <p style="margin: 5px 0; color: #666;"><i class="fa-solid fa-location-dot"></i> ${station.address}</p>
                <p style="margin: 5px 0; color: #666;"><i class="fa-solid fa-bolt"></i> ${station.connector} - ${station.power}kW</p>
                <p style="margin: 5px 0; color: #666;"><i class="fa-solid fa-coins"></i> ${station.price}đ/kWh</p>
            </div>

            <form id="bookingForm">
                <div style="margin-bottom: 20px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #333;">
                        Thời gian đặt chỗ
                    </label>
                    <input 
                        type="datetime-local" 
                        id="bookingTime" 
                        name="bookingTime"
                        required
                        min="${minTime.toISOString().slice(0, 16)}"
                        max="${maxTime.toISOString().slice(0, 16)}"
                        value="${minTime.toISOString().slice(0, 16)}"
                        style="width: 100%; padding: 12px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 16px;"
                    />
                    <small style="color: #666; display: block; margin-top: 5px;">
                        Tối thiểu 30 phút từ bây giờ, tối đa 24 giờ
                    </small>
                </div>

                <div style="margin-bottom: 20px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #333;">
                        Thời gian dự kiến sạc (phút)
                    </label>
                    <input 
                        type="number" 
                        id="duration" 
                        name="duration"
                        min="15"
                        max="480"
                        value="60"
                        required
                        style="width: 100%; padding: 12px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 16px;"
                    />
                </div>

                <div style="margin-bottom: 20px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #333;">
                        Ghi chú (tùy chọn)
                    </label>
                    <textarea 
                        id="notes" 
                        name="notes"
                        rows="3"
                        placeholder="Ví dụ: Tôi sẽ đến muộn 5 phút..."
                        style="width: 100%; padding: 12px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 16px; resize: vertical;"
                    ></textarea>
                </div>

                <div style="display: flex; gap: 10px;">
                    <button 
                        type="button" 
                        onclick="closeBookingModal()"
                        style="flex: 1; padding: 15px; background: #e0e0e0; color: #333; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer;"
                    >
                        Hủy
                    </button>
                    <button 
                        type="submit"
                        style="flex: 1; padding: 15px; background: #4CAF50; color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer;"
                    >
                        Xác nhận đặt chỗ
                    </button>
                </div>
            </form>

            <div id="bookingError" style="display: none; margin-top: 15px; padding: 12px; background: #ffebee; color: #c62828; border-radius: 8px; border-left: 4px solid #f44336;"></div>
        </div>
    `;

    document.body.appendChild(modal);

    document.getElementById('bookingForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        await submitBooking(station.id);
    });
}

function closeBookingModal() {
    const modal = document.getElementById('bookingModal');
    if (modal) {
        modal.remove();
    }
}

function formatDateTime(date) {
    const pad = n => String(n).padStart(2, '0');
    const yyyy = date.getFullYear();
    const MM = pad(date.getMonth() + 1);
    const dd = pad(date.getDate());
    const HH = pad(date.getHours());
    const mm = pad(date.getMinutes());
    const ss = pad(date.getSeconds());
    return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`;
}

async function submitBooking(stationId) {
    const bookingTime = document.getElementById('bookingTime').value;
    const duration = document.getElementById('duration').value;
    const notes = document.getElementById('notes').value;
    // Use accessToken saved by auth.js
    const token = localStorage.getItem('accessToken');

    try {
        // Step 1: get available chargers for this station
        const availRes = await fetch(`${API_BASE_URL}/stations/${stationId}/available-chargers`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const availData = await availRes.json();
        const chargers = (availData && availData.chargers) ? availData.chargers : [];

        if (!chargers.length) {
            const errBox = document.getElementById('bookingError');
            if (errBox) {
                errBox.style.display = 'block';
                errBox.textContent = 'Không có điểm sạc khả dụng tại trạm này.';
            }
            return;
        }

        const chargerId = chargers[0].id; // chọn điểm sạc khả dụng đầu tiên

        // Step 2: compute startTime and endTime in required format
        const startDate = new Date(bookingTime);
        const endDate = new Date(startDate.getTime() + parseInt(duration, 10) * 60000);
        const payload = {
            startTime: formatDateTime(startDate),
            endTime: formatDateTime(endDate)
        };

        // Step 3: book the charger
        const response = await fetch(`${API_BASE_URL}/stations/${stationId}/chargers/${chargerId}/book`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.ok && data.success) {
            closeBookingModal();
            // Lưu thông tin đặt chỗ để trang Thanh toán đọc (đã sửa)
            const bookingStation = {
                id: stationId,
                name: (markers.find(m => String(m.station.id) === String(stationId))?.station.name) || '',
                connectorDisplay: (markers.find(m => String(m.station.id) === String(stationId))?.station.connector) || '',
                priceDisplay: `${(markers.find(m => String(m.station.id) === String(stationId))?.station.price || 0).toLocaleString('vi-VN')}đ/kWh`,
                price: (markers.find(m => String(m.station.id) === String(stationId))?.station.price) || 0
            };
            localStorage.setItem('bookingStation', JSON.stringify(bookingStation));
            // Lưu chargerId để trang thanh toán có thể tạo phiên sạc giả
            localStorage.setItem('currentChargerId', String(chargerId));
            // Nếu backend trả về sessionId/bookingId thì lưu lại
            if (data.sessionId) {
                localStorage.setItem('currentSessionId', String(data.sessionId));
            } else if (data.bookingId) {
                localStorage.setItem('currentBookingId', String(data.bookingId));
            }
            // Điều hướng sang trang thanh toán
            window.location.href = 'payment.html';
        } else {
            document.getElementById('bookingError').style.display = 'block';
            document.getElementById('bookingError').textContent = (data && (data.message || data.error)) || 'Không thể đặt chỗ. Vui lòng thử lại.';
        }
    } catch (error) {
        console.error('Error booking station:', error);
        document.getElementById('bookingError').style.display = 'block';
        document.getElementById('bookingError').textContent = 'Lỗi kết nối. Vui lòng thử lại.';
    }
}

// Áp dụng kết quả booking từ localStorage (nếu có)
function applyBookingFromStorage() {
    console.log('Checking for booking results in localStorage...');
}

// =====================================================
// GLOBAL EXPORTS
// =====================================================
window.startBooking = startBooking;
window.closeBookingModal = closeBookingModal;
window.getCurrentLocation = getCurrentLocation;

// Initialize when DOM loads
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMap);
} else {
    initMap();
}

console.log('✅ map.js loaded - Leaflet + OpenStreetMap ready!');