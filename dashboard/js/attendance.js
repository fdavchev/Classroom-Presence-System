/**
 * CPS Dashboard — attendance.js
 * ─────────────────────────────────────────────────────────────
 * Responsibilities:
 *  1. Auth guard
 *  2. Populate filter dropdowns (courses, teachers)
 *  3. Manage pagination state and fetch attendance records
 *  4. Render table rows with status badges
 *  5. Handle filter apply/reset with debounce on search
 *  6. CSV export via API
 *
 * State pattern: Single `state` object drives all renders
 */

(async () => {

  // ── 1. Auth Guard ──────────────────────────────────────────
  if (!API.requireAuth()) return;

  // ── 2. Init sidebar ────────────────────────────────────────
  initSidebar();

  // ── 3. Page State ───────────────────────────────────────────
  const state = {
    page:        1,
    totalPages:  1,
    totalRecords: 0,
    pageSize:    20,
    loading:     false,
    filters: {
      search:   '',
      course:   '',
      date:     '',
      teacher:  '',
    }
  };

  // ── 4. DOM Refs ─────────────────────────────────────────────
  const tableBody      = document.getElementById('table-body');
  const tableInfo      = document.getElementById('table-info');
  const paginationInfo = document.getElementById('pagination-info');
  const paginationCtrl = document.getElementById('pagination-controls');
  const searchInput    = document.getElementById('filter-search');
  const courseSelect   = document.getElementById('filter-course');
  const dateInput      = document.getElementById('filter-date');
  const teacherSelect  = document.getElementById('filter-teacher');
  const teacherWrap    = document.getElementById('teacher-filter-wrap');
  const applyBtn       = document.getElementById('apply-filter-btn');
  const resetBtn       = document.getElementById('reset-filter-btn');
  const exportBtn      = document.getElementById('export-btn');

  // Set max date to today
  dateInput.max = new Date().toISOString().split('T')[0];

  // ── 5. Show teacher filter for admins ───────────────────────
  if (API.auth.getRole() === 'admin') {
    teacherWrap.style.display = 'flex';
  }

  // ── 6. Populate Course Dropdown ─────────────────────────────
  async function loadCourseOptions() {
    try {
      const data = await API.getCourses();
      const courses = data.courses || data || [];
      courses.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id || c;
        opt.textContent = c.name || c;
        courseSelect.appendChild(opt);
      });
    } catch (_) {
      // silently degrade — filter still works without options
    }
  }

  // ── 7. Populate Teacher Dropdown (admin only) ───────────────
  async function loadTeacherOptions() {
    if (API.auth.getRole() !== 'admin') return;
    try {
      const data = await API.getTeachers();
      const teachers = data.teachers || data || [];
      teachers.forEach(t => {
        const opt = document.createElement('option');
        opt.value = t.id || t;
        opt.textContent = t.name || t;
        teacherSelect.appendChild(opt);
      });
    } catch (_) { /* degrade */ }
  }

  // ── 8. Render Table Rows ────────────────────────────────────
  function renderTable(records) {
    if (!records || records.length === 0) {
      tableBody.innerHTML = `
        <tr>
          <td colspan="7">
            <div class="table-empty">
              <svg width="40" height="40" fill="none" viewBox="0 0 24 24">
                <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                  stroke="#484f58" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
              <p>No records found for the selected filters.</p>
            </div>
          </td>
        </tr>`;
      return;
    }

    const offset = (state.page - 1) * state.pageSize;

    tableBody.innerHTML = records.map((r, i) => {
      const initials = (r.student_name || 'S')
        .split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();

      // Determine status badge
      let statusBadge;
      if (r.status === 'absent') {
        statusBadge = `<span class="badge badge-red">Absent</span>`;
      } else if (r.status === 'late') {
        statusBadge = `<span class="badge badge-amber">Late</span>`;
      } else {
        statusBadge = `<span class="badge badge-green">Present</span>`;
      }

      return `
        <tr style="animation-delay:${i * 0.03}s">
          <td class="td-mono">${offset + i + 1}</td>
          <td>
            <div class="td-student">
              <div class="td-avatar">${initials}</div>
              <span>${r.student_name || '—'}</span>
            </div>
          </td>
          <td class="td-mono">${r.student_id || '—'}</td>
          <td>${r.course_name || r.course || '—'}</td>
          <td>${r.teacher_name || r.teacher || '—'}</td>
          <td class="td-mono" style="font-size:12px;">${formatTimestamp(r.timestamp)}</td>
          <td>${statusBadge}</td>
        </tr>`;
    }).join('');
  }

  // ── 9. Render Pagination ────────────────────────────────────
  function renderPagination() {
    const { page, totalPages, totalRecords, pageSize } = state;

    const start = totalRecords === 0 ? 0 : (page - 1) * pageSize + 1;
    const end   = Math.min(page * pageSize, totalRecords);

    paginationInfo.textContent = totalRecords === 0
      ? 'No records'
      : `Showing ${start}–${end} of ${totalRecords}`;

    if (totalPages <= 1) {
      paginationCtrl.innerHTML = '';
      return;
    }

    // Build page numbers with ellipsis
    const pages = buildPageRange(page, totalPages);

    paginationCtrl.innerHTML = `
      <button class="page-btn" id="pg-prev" ${page <= 1 ? 'disabled' : ''}>
        <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
      </button>
      ${pages.map(p =>
        p === '…'
          ? `<span class="page-btn" style="cursor:default;border:none;">…</span>`
          : `<button class="page-btn ${p === page ? 'active' : ''}" data-page="${p}">${p}</button>`
      ).join('')}
      <button class="page-btn" id="pg-next" ${page >= totalPages ? 'disabled' : ''}>
        <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
      </button>`;

    // Attach listeners
    paginationCtrl.querySelector('#pg-prev')?.addEventListener('click', () => goToPage(page - 1));
    paginationCtrl.querySelector('#pg-next')?.addEventListener('click', () => goToPage(page + 1));
    paginationCtrl.querySelectorAll('[data-page]').forEach(btn => {
      btn.addEventListener('click', () => goToPage(Number(btn.dataset.page)));
    });
  }

  function buildPageRange(current, total) {
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    if (current <= 4) return [1, 2, 3, 4, 5, '…', total];
    if (current >= total - 3) return [1, '…', total-4, total-3, total-2, total-1, total];
    return [1, '…', current-1, current, current+1, '…', total];
  }

  // ── 10. Fetch + Render cycle ─────────────────────────────────
  async function fetchAndRender() {
    if (state.loading) return;
    state.loading = true;

    // Show loading skeleton
    tableBody.innerHTML = Array(5).fill('').map(() => `
      <tr class="row-skeleton">
        ${Array(7).fill('').map(() => `
          <td><div class="skeleton skeleton-text" style="width:${60 + Math.random()*40}%"></div></td>
        `).join('')}
      </tr>`).join('');

    tableInfo.textContent = 'Loading…';

    try {
      const params = {
        page:    state.page,
        ...Object.fromEntries(
          Object.entries(state.filters).filter(([, v]) => v)
        ),
      };

      const data = await API.getAttendanceRecords(params);

      state.totalPages   = data.total_pages   ?? 1;
      state.totalRecords = data.total_records  ?? (data.records?.length ?? 0);
      state.pageSize     = data.page_size      ?? state.pageSize;

      renderTable(data.records || []);
      renderPagination();

      tableInfo.textContent = `${state.totalRecords} record${state.totalRecords !== 1 ? 's' : ''} found`;

    } catch (err) {
      tableBody.innerHTML = `
        <tr><td colspan="7">
          <div class="table-empty">
            <p style="color:var(--red)">Failed to load records: ${err.message}</p>
          </div>
        </td></tr>`;
      tableInfo.textContent = 'Error';
      showToast(err.message || 'Failed to load records.', 'error');
    } finally {
      state.loading = false;
    }
  }

  // ── 11. Navigation ──────────────────────────────────────────
  function goToPage(p) {
    if (p < 1 || p > state.totalPages || p === state.page) return;
    state.page = p;
    fetchAndRender();
    // Scroll to top of table
    document.querySelector('.table-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // ── 12. Filter Handlers ─────────────────────────────────────
  function applyFilters() {
    state.filters.search  = searchInput.value.trim();
    state.filters.course  = courseSelect.value;
    state.filters.date    = dateInput.value;
    state.filters.teacher = teacherSelect?.value || '';
    state.page = 1; // reset to first page
    fetchAndRender();
  }

  function resetFilters() {
    searchInput.value   = '';
    courseSelect.value  = '';
    dateInput.value     = '';
    if (teacherSelect) teacherSelect.value = '';
    state.filters = { search: '', course: '', date: '', teacher: '' };
    state.page = 1;
    fetchAndRender();
  }

  applyBtn.addEventListener('click', applyFilters);
  resetBtn.addEventListener('click', resetFilters);

  // Allow Enter key in search
  searchInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') applyFilters();
  });

  // ── 13. CSV Export ──────────────────────────────────────────
  exportBtn.addEventListener('click', async () => {
    exportBtn.disabled = true;
    exportBtn.textContent = 'Exporting…';
    try {
      const blobUrl = await API.exportAttendanceCSV(state.filters);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = `attendance_${new Date().toISOString().split('T')[0]}.csv`;
      link.click();
      URL.revokeObjectURL(blobUrl);
      showToast('Export downloaded!', 'success');
    } catch (err) {
      showToast('Export failed: ' + err.message, 'error');
    } finally {
      exportBtn.disabled = false;
      exportBtn.innerHTML = `
        <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
          <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
        Export CSV`;
    }
  });

  // ── 14. Boot ────────────────────────────────────────────────
  await Promise.all([loadCourseOptions(), loadTeacherOptions()]);
  await fetchAndRender();

})();
