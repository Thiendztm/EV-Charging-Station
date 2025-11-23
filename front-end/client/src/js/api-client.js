/**
 * Centralized API Client for EV Charging Station
 * Ensures consistent API calls across Driver, Staff, and Admin roles
 */

const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080/api';

// ============= Authentication Utilities =============
export function getAuthToken() {
    return localStorage.getItem('accessToken') || localStorage.getItem('jwt_token');
}

export function getUserRole() {
    return localStorage.getItem('userRole');
}

export function getUserId() {
    return localStorage.getItem('userId');
}

export function isAuthenticated() {
    return !!getAuthToken();
}

export function redirectToLogin(redirectUrl = null) {
    const url = redirectUrl ? `login.html?redirect=${encodeURIComponent(redirectUrl)}` : 'login.html';
    window.location.href = url;
}

// ============= HTTP Client =============
async function apiRequest(endpoint, options = {}) {
    const token = getAuthToken();

    const defaultHeaders = {
        'Content-Type': 'application/json',
    };

    if (token) {
        defaultHeaders['Authorization'] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers: {
            ...defaultHeaders,
            ...options.headers
        }
    };

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

        // Handle 401 Unauthorized
        if (response.status === 401) {
            localStorage.clear();
            redirectToLogin(window.location.pathname);
            throw new Error('Session expired. Please login again.');
        }

        // Handle 403 Forbidden
        if (response.status === 403) {
            throw new Error('You do not have permission to perform this action.');
        }

        // Parse response
        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(data.message || `HTTP ${response.status}: ${response.statusText}`);
        }

        return data;
    } catch (error) {
        console.error(`API Error [${endpoint}]:`, error);
        throw error;
    }
}

// ============= DRIVER APIs =============
export const driverAPI = {
    // Profile
    getProfile: () => apiRequest('/profile'),
    updateProfile: (data) => apiRequest('/profile', { method: 'PUT', body: JSON.stringify(data) }),

    // Wallet
    getWallet: () => apiRequest('/profile/wallet'),
    topUpWallet: (amount, method) => apiRequest('/payment/wallet/topup', {
        method: 'POST',
        body: JSON.stringify({ amount, paymentMethod: method })
    }),

    // Stations
    getAllStations: () => apiRequest('/stations'),
    getStationById: (stationId) => apiRequest(`/stations/${stationId}`),
    getNearbyStations: (lat, lng, radius = 10) => apiRequest(`/stations/nearby?lat=${lat}&lng=${lng}&radius=${radius}`),

    // Reservations
    createReservation: (stationId, chargerId, startTime) => apiRequest('/reservations', {
        method: 'POST',
        body: JSON.stringify({ stationId, chargerId, startTime })
    }),
    getMyReservations: () => apiRequest('/reservations/my'),
    cancelReservation: (reservationId) => apiRequest(`/reservations/${reservationId}`, { method: 'DELETE' }),

    // Charging Sessions
    startSession: (chargerId, stationId) => apiRequest('/charging/start', {
        method: 'POST',
        body: JSON.stringify({ chargerId, stationId })
    }),
    getSessionStatus: (sessionId) => apiRequest(`/charging/session/${sessionId}`),
    stopSession: (sessionId) => apiRequest('/charging/stop', {
        method: 'POST',
        body: JSON.stringify({ sessionId })
    }),

    // History
    getChargingHistory: async (userId) => {
        const data = await apiRequest('/history/charging');
        const sessions = Array.isArray(data) ? data : (data.history || data.sessions || []);
        return sessions;
    },
    getSessionDetail: (sessionId) => apiRequest(`/charging/session/${sessionId}`),

    // Payment
    paySession: (sessionId, paymentMethod) => apiRequest('/payment/charge', {
        method: 'POST',
        body: JSON.stringify({ sessionId, paymentMethod })
    }),
    getInvoice: (sessionId) => apiRequest(`/payment/invoice/${sessionId}`)
};

// ============= STAFF APIs =============
export const staffAPI = {
    // Stations Monitoring
    getAssignedStations: () => apiRequest('/staff/stations'),
    getStationStatus: (stationId) => apiRequest(`/staff/stations/${stationId}/status`),
    getChargerStatus: (chargerId) => apiRequest(`/staff/chargers/${chargerId}/status`),

    // Active Sessions
    getActiveSessions: (stationId = null) => {
        const endpoint = stationId
            ? `/staff/sessions/active?stationId=${stationId}`
            : '/staff/sessions/active';
        return apiRequest(endpoint);
    },
    getSessionDetails: (sessionId) => apiRequest(`/staff/sessions/${sessionId}`),

    // Session Management
    startSessionForDriver: (driverId, chargerId) => apiRequest('/staff/sessions/start', {
        method: 'POST',
        body: JSON.stringify({ driverId, chargerId })
    }),
    stopSessionForDriver: (sessionId, reason) => apiRequest('/staff/sessions/stop', {
        method: 'POST',
        body: JSON.stringify({ sessionId, reason })
    }),

    // Cash Payment
    processCashPayment: (sessionId, amount, notes) => apiRequest('/staff/payments/cash', {
        method: 'POST',
        body: JSON.stringify({ sessionId, amount, notes })
    }),
    getPaymentHistory: (stationId = null, date = null) => {
        let endpoint = '/staff/payments/history';
        const params = new URLSearchParams();
        if (stationId) params.append('stationId', stationId);
        if (date) params.append('date', date);
        if (params.toString()) endpoint += '?' + params.toString();
        return apiRequest(endpoint);
    },

    // Incident Reporting
    reportIncident: (stationId, chargerId, type, description, severity = 'MEDIUM') => apiRequest('/staff/incidents', {
        method: 'POST',
        body: JSON.stringify({ stationId, chargerId, type, description, severity })
    }),
    getIncidents: (stationId = null, status = null) => {
        let endpoint = '/staff/incidents';
        const params = new URLSearchParams();
        if (stationId) params.append('stationId', stationId);
        if (status) params.append('status', status);
        if (params.toString()) endpoint += '?' + params.toString();
        return apiRequest(endpoint);
    },
    updateIncidentStatus: (incidentId, status, notes) => apiRequest(`/staff/incidents/${incidentId}`, {
        method: 'PUT',
        body: JSON.stringify({ status, notes })
    }),

    // Charger Control
    enableCharger: (chargerId) => apiRequest(`/staff/chargers/${chargerId}/enable`, { method: 'POST' }),
    disableCharger: (chargerId, reason) => apiRequest(`/staff/chargers/${chargerId}/disable`, {
        method: 'POST',
        body: JSON.stringify({ reason })
    })
};

// ============= ADMIN APIs =============
export const adminAPI = {
    // Users Management
    getAllUsers: (role = null, page = 0, size = 20) => {
        let endpoint = `/admin/users?page=${page}&size=${size}`;
        if (role) endpoint += `&role=${role}`;
        return apiRequest(endpoint);
    },
    getUserDetails: (userId) => apiRequest(`/admin/users/${userId}`),
    createUser: (userData) => apiRequest('/admin/users', {
        method: 'POST',
        body: JSON.stringify(userData)
    }),
    updateUser: (userId, userData) => apiRequest(`/admin/users/${userId}`, {
        method: 'PUT',
        body: JSON.stringify(userData)
    }),
    deleteUser: (userId) => apiRequest(`/admin/users/${userId}`, { method: 'DELETE' }),
    assignStaffToStation: (staffId, stationId) => apiRequest('/admin/staff/assign', {
        method: 'POST',
        body: JSON.stringify({ staffId, stationId })
    }),

    // Stations Management
    getAllStations: (status = null) => {
        let endpoint = '/admin/stations';
        if (status) endpoint += `?status=${status}`;
        return apiRequest(endpoint);
    },
    createStation: (stationData) => apiRequest('/admin/stations', {
        method: 'POST',
        body: JSON.stringify(stationData)
    }),
    updateStation: (stationId, stationData) => apiRequest(`/admin/stations/${stationId}`, {
        method: 'PUT',
        body: JSON.stringify(stationData)
    }),
    deleteStation: (stationId) => apiRequest(`/admin/stations/${stationId}`, { method: 'DELETE' }),

    // Chargers Management
    getChargersByStation: (stationId) => apiRequest(`/admin/stations/${stationId}/chargers`),
    createCharger: (chargerData) => apiRequest('/admin/chargers', {
        method: 'POST',
        body: JSON.stringify(chargerData)
    }),
    updateCharger: (chargerId, chargerData) => apiRequest(`/admin/chargers/${chargerId}`, {
        method: 'PUT',
        body: JSON.stringify(chargerData)
    }),
    deleteCharger: (chargerId) => apiRequest(`/admin/chargers/${chargerId}`, { method: 'DELETE' }),
    remoteControl: (chargerId, action) => apiRequest(`/admin/chargers/${chargerId}/control`, {
        method: 'POST',
        body: JSON.stringify({ action })
    }),

    // Reports & Analytics
    getRevenueReport: (startDate, endDate, groupBy = 'day') => apiRequest(
        `/admin/reports/revenue?startDate=${startDate}&endDate=${endDate}&groupBy=${groupBy}`
    ),
    getUsageReport: (startDate, endDate, stationId = null) => {
        let endpoint = `/admin/reports/usage?startDate=${startDate}&endDate=${endDate}`;
        if (stationId) endpoint += `&stationId=${stationId}`;
        return apiRequest(endpoint);
    },
    getPeakHoursReport: (stationId = null, period = 'week') => {
        let endpoint = `/admin/reports/peak-hours?period=${period}`;
        if (stationId) endpoint += `&stationId=${stationId}`;
        return apiRequest(endpoint);
    },
    getStationPerformance: (stationId, period = 'month') => apiRequest(
        `/admin/reports/station-performance/${stationId}?period=${period}`
    ),

    // Subscription Plans
    getAllPlans: () => apiRequest('/admin/plans'),
    createPlan: (planData) => apiRequest('/admin/plans', {
        method: 'POST',
        body: JSON.stringify(planData)
    }),
    updatePlan: (planId, planData) => apiRequest(`/admin/plans/${planId}`, {
        method: 'PUT',
        body: JSON.stringify(planData)
    }),
    deletePlan: (planId) => apiRequest(`/admin/plans/${planId}`, { method: 'DELETE' }),

    // Support Tickets
    getAllTickets: (status = null) => {
        let endpoint = '/admin/support/tickets';
        if (status) endpoint += `?status=${status}`;
        return apiRequest(endpoint);
    },
    getTicketDetails: (ticketId) => apiRequest(`/admin/support/tickets/${ticketId}`),
    updateTicketStatus: (ticketId, status, response) => apiRequest(`/admin/support/tickets/${ticketId}`, {
        method: 'PUT',
        body: JSON.stringify({ status, response })
    }),

    // Incidents Management (Admin view)
    getAllIncidents: (status = null, severity = null) => {
        let endpoint = '/admin/incidents';
        const params = new URLSearchParams();
        if (status) params.append('status', status);
        if (severity) params.append('severity', severity);
        if (params.toString()) endpoint += '?' + params.toString();
        return apiRequest(endpoint);
    },
    assignIncident: (incidentId, staffId) => apiRequest(`/admin/incidents/${incidentId}/assign`, {
        method: 'POST',
        body: JSON.stringify({ staffId })
    }),
    resolveIncident: (incidentId, solution) => apiRequest(`/admin/incidents/${incidentId}/resolve`, {
        method: 'POST',
        body: JSON.stringify({ solution })
    })
};

// ============= Real-time Updates (WebSocket) =============
export class RealtimeConnection {
    constructor(endpoint, onMessage, onError) {
        this.endpoint = endpoint;
        this.onMessage = onMessage;
        this.onError = onError;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
    }

    connect() {
        const wsUrl = API_BASE_URL.replace('http', 'ws') + this.endpoint;
        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
            console.log('WebSocket connected');
            this.reconnectAttempts = 0;

            // Send auth token
            const token = getAuthToken();
            if (token) {
                this.ws.send(JSON.stringify({ type: 'auth', token }));
            }
        };

        this.ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this.onMessage(data);
            } catch (error) {
                console.error('Failed to parse WebSocket message:', error);
            }
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            if (this.onError) this.onError(error);
        };

        this.ws.onclose = () => {
            console.log('WebSocket closed');
            this.reconnect();
        };
    }

    reconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`Reconnecting... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            setTimeout(() => this.connect(), 3000);
        }
    }

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
        }
    }

    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }
}

// ============= Export all APIs =============
export default {
    driver: driverAPI,
    staff: staffAPI,
    admin: adminAPI,
    auth: {
        getAuthToken,
        getUserRole,
        getUserId,
        isAuthenticated,
        redirectToLogin
    },
    realtime: RealtimeConnection
};
