/**
 * CPS Dashboard — API Client (api.js)
 * ─────────────────────────────────────────────────────────────
 * Singleton fetch wrapper that:
 *  - Centralizes the base URL
 *  - Automatically injects Authorization: Bearer headers
 *  - Detects 401 responses and redirects to login
 *  - Provides typed methods for each backend endpoint
 *  - Exposes auth helpers for session management
 */

const API = (() => {

  // ── Configuration ──────────────────────────────────────────
  const BASE_URL = 'http://localhost:8000/api/v1';

  const TOKEN_KEY   = 'cps_token';
  const ROLE_KEY    = 'cps_role';
  const NAME_KEY    = 'cps_name';
  const EMAIL_KEY   = 'cps_email';

  // ── Token Storage (sessionStorage for security) ─────────────
  const auth = {
    setSession(token, role, name, email) {
      sessionStorage.setItem(TOKEN_KEY,  token);
      sessionStorage.setItem(ROLE_KEY,   role);
      sessionStorage.setItem(NAME_KEY,   name);
      sessionStorage.setItem(EMAIL_KEY,  email || '');
    },
    getToken()  { return sessionStorage.getItem(TOKEN_KEY); },
    getRole()   { return sessionStorage.getItem(ROLE_KEY); },
    getName()   { return sessionStorage.getItem(NAME_KEY); },
    getEmail()  { return sessionStorage.getItem(EMAIL_KEY); },
    isLoggedIn(){ return !!sessionStorage.getItem(TOKEN_KEY); },
    clear() {
      [TOKEN_KEY, ROLE_KEY, NAME_KEY, EMAIL_KEY].forEach(k => sessionStorage.removeItem(k));
    }
  };

  // ── Core Fetch ──────────────────────────────────────────────
  async function request(method, path, body = null, options = {}) {
    const token = auth.getToken();

    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    };

    const config = {
      method,
      headers,
      ...(body ? { body: JSON.stringify(body) } : {}),
    };

    let response;
    try {
      response = await fetch(`${BASE_URL}${path}`, config);
    } catch (networkErr) {
      throw new ApiError('Network error — is the backend running?', 0);
    }

    // Auto-logout on 401
    if (response.status === 401) {
      auth.clear();
      if (!window.location.pathname.includes('index.html') && window.location.pathname !== '/') {
        window.location.href = 'index.html?session=expired';
      }
      throw new ApiError('Session expired. Please log in again.', 401);
    }

    // Parse JSON or text
    let data;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = await response.text();
    }

    if (!response.ok) {
      const message = data?.detail || data?.message || `Request failed (${response.status})`;
      throw new ApiError(message, response.status, data);
    }

    return data;
  }

  // ── Api Error Class ─────────────────────────────────────────
  class ApiError extends Error {
    constructor(message, status, raw = null) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.raw = raw;
    }
  }

  // ── Shorthand Methods ───────────────────────────────────────
  const get  = (path, params = {}) => {
    const qs = new URLSearchParams(
      Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))
    ).toString();
    return request('GET', qs ? `${path}?${qs}` : path);
  };

  const post   = (path, body)  => request('POST',   path, body);
  const put    = (path, body)  => request('PUT',    path, body);
  const del    = (path)        => request('DELETE', path);

  // ══════════════════════════════════════════════════════════
  // ENDPOINT DEFINITIONS
  // Map directly to backend contracts from the project spec
  // ══════════════════════════════════════════════════════════

  /**
   * POST /auth/login
   * @param {string} email
   * @param {string} password
   * @returns {{ token, role, name }}
   */
  async function login(email, password) {
    const data = await post('/auth/login', { email, password });
    auth.setSession(data.token, data.role, data.name, email);
    return data;
  }

  /**
   * POST /auth/logout (optional — clear local session anyway)
   */
  async function logout() {
    try { await post('/auth/logout', {}); } catch (_) { /* ignore */ }
    auth.clear();
    window.location.href = 'index.html';
  }

  /**
   * GET /statistics/summary
   * Returns aggregate counts + arrays for Chart.js
   * Role-aware: admin sees all, teacher sees their own
   */
  async function getStatisticsSummary() {
    return get('/statistics/summary');
  }

  /**
   * GET /attendance/records
   * @param {object} filters - { course, date, teacher_id, search, page }
   * @returns {{ records: [], total_pages: number, current_page: number, total_records: number }}
   */
  async function getAttendanceRecords(filters = {}) {
    return get('/attendance/records', filters);
  }

  /**
   * GET /attendance/records/export  (CSV download)
   * Returns a blob URL the page can link to
   * @param {object} filters
   */
  async function exportAttendanceCSV(filters = {}) {
    const token = auth.getToken();
    const qs = new URLSearchParams(
      Object.fromEntries(Object.entries(filters).filter(([, v]) => v))
    ).toString();

    const resp = await fetch(`${BASE_URL}/attendance/records/export?${qs}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!resp.ok) throw new ApiError('Export failed', resp.status);

    const blob = await resp.blob();
    return URL.createObjectURL(blob);
  }

  /**
   * GET /statistics/courses
   * Returns list of courses for filter dropdowns
   */
  async function getCourses() {
    return get('/statistics/courses');
  }

  /**
   * GET /statistics/teachers  (admin only)
   * Returns list of teachers for filter dropdown
   */
  async function getTeachers() {
    return get('/statistics/teachers');
  }

  // ── Utility: guard pages from unauthenticated access ────────
  function requireAuth(redirectTo = 'index.html') {
    if (!auth.isLoggedIn()) {
      window.location.href = redirectTo;
      return false;
    }
    return true;
  }

  /**
   * requireRole — kick non-admin users off admin-only pages
   * @param {'admin'|'teacher'} requiredRole
   */
  function requireRole(requiredRole) {
    if (!requireAuth()) return false;
    if (auth.getRole() !== requiredRole) {
      window.location.href = 'dashboard.html';
      return false;
    }
    return true;
  }

  // ── Public API ──────────────────────────────────────────────
  return {
    auth,
    login,
    logout,
    getStatisticsSummary,
    getAttendanceRecords,
    exportAttendanceCSV,
    getCourses,
    getTeachers,
    requireAuth,
    requireRole,
    ApiError,
  };
})();

// ── Toast helper (shared across all pages) ─────────────────────
function showToast(message, type = 'success', duration = 3500) {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const iconMap = {
    success: `<svg width="16" height="16" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="#3fb950" stroke-width="2"/><path d="M8 12l3 3 5-5" stroke="#3fb950" stroke-width="2" stroke-linecap="round"/></svg>`,
    error:   `<svg width="16" height="16" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="#f85149" stroke-width="2"/><path d="M15 9l-6 6M9 9l6 6" stroke="#f85149" stroke-width="2" stroke-linecap="round"/></svg>`,
    info:    `<svg width="16" height="16" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="#58a6ff" stroke-width="2"/><path d="M12 8v4M12 16h.01" stroke="#58a6ff" stroke-width="2" stroke-linecap="round"/></svg>`,
  };

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span class="toast-icon">${iconMap[type] || ''}</span>${message}`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = 'toastIn 0.3s ease reverse both';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// ── Sidebar helpers (shared) ────────────────────────────────────
function initSidebar() {
  // Populate user panel
  const nameEl  = document.getElementById('sidebar-user-name');
  const roleEl  = document.getElementById('sidebar-user-role');
  const initEl  = document.getElementById('sidebar-user-initials');
  const logoutBtn = document.getElementById('logout-btn');

  if (nameEl) nameEl.textContent = API.auth.getName() || 'User';
  if (roleEl) roleEl.textContent = API.auth.getRole() || '—';
  if (initEl) {
    const name = API.auth.getName() || 'U';
    initEl.textContent = name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
  }

  if (logoutBtn) logoutBtn.addEventListener('click', () => API.logout());

  // Highlight active nav item
  const current = window.location.pathname.split('/').pop();
  document.querySelectorAll('.nav-item').forEach(item => {
    if (item.getAttribute('href') === current) item.classList.add('active');
  });

  // Hide admin-only nav items for teachers
  if (API.auth.getRole() !== 'admin') {
    document.querySelectorAll('[data-admin-only]').forEach(el => el.style.display = 'none');
  }

  // Mobile toggle
  const menuBtn = document.getElementById('mobile-menu-btn');
  const sidebar  = document.querySelector('.sidebar');
  const overlay  = document.querySelector('.sidebar-overlay');

  if (menuBtn && sidebar && overlay) {
    menuBtn.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      overlay.classList.toggle('show');
    });
    overlay.addEventListener('click', () => {
      sidebar.classList.remove('open');
      overlay.classList.remove('show');
    });
  }
}

// ── Time formatter ──────────────────────────────────────────────
function formatTimestamp(isoString) {
  if (!isoString) return '—';
  const d = new Date(isoString);
  if (isNaN(d)) return isoString;
  return d.toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false
  });
}

function timeAgo(isoString) {
  const d = new Date(isoString);
  const diff = Date.now() - d.getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1)  return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h/24)}d ago`;
}
