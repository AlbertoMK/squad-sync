import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:3001';

export const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor to add auth token
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response interceptor for error handling
api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            // Token expired or invalid
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

// Auth API
export const authAPI = {
    register: (data: { username: string; email: string; password: string; avatarColor?: string }) =>
        api.post('/api/auth/register', data),
    login: (data: { email: string; password: string }) =>
        api.post('/api/auth/login', data),
    getCurrentUser: () => api.get('/api/auth/me'),
};

// Games API
export const gamesAPI = {
    getAll: (search?: string) => api.get('/api/games', { params: { search } }),
    getById: (id: string) => api.get(`/api/games/${id}`),
    create: (data: { title: string; minPlayers?: number; maxPlayers?: number; genre?: string; coverImageUrl?: string }) =>
        api.post('/api/games', data),
    update: (id: string, data: { title: string; minPlayers?: number; maxPlayers?: number; genre?: string; coverImageUrl?: string }) =>
        api.put(`/api/games/${id}`, data),
    delete: (id: string) => api.delete(`/api/games/${id}`),
};

// Preferences API
export const preferencesAPI = {
    getAll: () => api.get('/api/preferences'),
    set: (data: { gameId: string; weight: number }) =>
        api.post('/api/preferences', data),
    getByGame: (gameId: string) => api.get(`/api/preferences/game/${gameId}`),
};

// Availability API
export const availabilityAPI = {
    getAll: (startDate?: string, endDate?: string) =>
        api.get('/api/availability', { params: { startDate, endDate } }),
    getGroup: (startDate?: string, endDate?: string) =>
        api.get('/api/availability/group', { params: { startDate, endDate } }),
    create: (data: { startTime: string; endTime: string; gameId?: string }) =>
        api.post('/api/availability', data),
    delete: (id: string) => api.delete(`/api/availability/${id}`),
};

// Matchmaking API
export const matchmakingAPI = {
    run: () => api.post('/api/matchmaking/run'),
    getSessions: () => api.get('/api/matchmaking/sessions'),
    getSessionById: (id: string) => api.get(`/api/matchmaking/sessions/${id}`),
};

// Sessions API
export const sessionsAPI = {
    accept: (sessionId: string) => api.post(`/api/sessions/${sessionId}/accept`),
    reject: (sessionId: string, reason: string) => api.post(`/api/sessions/${sessionId}/reject`, { reason }),
};
