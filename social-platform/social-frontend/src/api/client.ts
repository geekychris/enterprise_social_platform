import axios from 'axios';
import { useAuthStore } from '../stores/authStore';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const { token, debugUserId } = useAuthStore.getState();

  if (debugUserId) {
    config.headers['X-Debug-User-Id'] = debugUserId;
  } else if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  // Send tenant ID on every request
  const tenantId = localStorage.getItem('tenantId');
  if (tenantId) {
    config.headers['X-Tenant-Id'] = tenantId;
  }

  // Let axios set Content-Type automatically for FormData (multipart uploads)
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type'];
  }

  return config;
});

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

export default api;
