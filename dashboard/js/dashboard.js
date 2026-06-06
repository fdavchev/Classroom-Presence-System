/**
 * CPS Dashboard — dashboard.js
 * ─────────────────────────────────────────────────────────────
 * Responsibilities:
 *  1. Auth guard (redirect if not logged in)
 *  2. Fetch /statistics/summary and render metric cards
 *  3. Render Chart.js: bar (by course) + line (trend)
 *  4. Render recent attendance feed
 *  5. Live clock + refresh button
 *
 * Pattern: Module IIFE — no global namespace pollution
 */

(async () => {
  // ── 1. Auth Guard ──────────────────────────────────────────
  if (!API.requireAuth()) return;

  // ── 2. Init sidebar ────────────────────────────────────────
  initSidebar();

  // ── 3. Live Clock ──────────────────────────────────────────
  const liveTimeEl = document.getElementById('live-time');
  function tickClock() {
    liveTimeEl.textContent = new Date().toLocaleTimeString('en-GB', {
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }
  tickClock();
  setInterval(tickClock, 1000);

  // ── 4. Chart.js global defaults ────────────────────────────
  Chart.defaults.color = '#8b949e';
  Chart.defaults.font.family = "'DM Mono', monospace";
  Chart.defaults.font.size = 12;
  Chart.defaults.borderColor = '#2a3441';

  // ── 5. Chart instances (stored to allow destroy on re-render) ─
  let courseChartInstance = null;
  let trendChartInstance  = null;

  // ── 6. Render Metrics ───────────────────────────────────────
  function renderMetrics(data) {
    const grid = document.getElementById('metrics-grid');

    const metrics = [
      {
        label: 'Today\'s Check-ins',
        value: data.today_checkins ?? 0,
        color: 'amber',
        icon: `<svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`,
        delta: data.today_vs_yesterday != null
          ? { value: `${data.today_vs_yesterday > 0 ? '+' : ''}${data.today_vs_yesterday} vs yesterday`, dir: data.today_vs_yesterday >= 0 ? 'up' : 'down' }
          : null,
      },
      {
        label: 'Overall Rate',
        value: `${data.attendance_rate ?? 0}%`,
        color: 'green',
        icon: `<svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`,
        delta: data.rate_vs_last_week != null
          ? { value: `${data.rate_vs_last_week > 0 ? '+' : ''}${data.rate_vs_last_week}% vs last week`, dir: data.rate_vs_last_week >= 0 ? 'up' : 'down' }
          : null,
      },
      {
        label: 'Active Sessions',
        value: data.active_sessions ?? 0,
        color: 'blue',
        icon: `<svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M13 10V3L4 14h7v7l9-11h-7z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`,
        delta: { value: 'Currently live', dir: 'up' },
      },
      {
        label: 'Total Students',
        value: data.total_students ?? 0,
        color: 'purple',
        icon: `<svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M23 21v-2a4 4 0 00-3-3.87M9 7a4 4 0 100 8 4 4 0 000-8zM16 3.13a4 4 0 010 7.75" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`,
        delta: null,
      },
    ];

    grid.innerHTML = metrics.map((m, i) => `
      <div class="metric-card ${m.color}" style="animation-delay:${i * 0.07}s">
        <div class="metric-label">
          ${m.icon}
          ${m.label}
        </div>
        <div class="metric-value">${m.value}</div>
        ${m.delta
          ? `<div class="metric-delta ${m.delta.dir}">
               ${m.delta.dir === 'up'
                  ? `<svg width="12" height="12" viewBox="0 0 24 24" fill="none"><path d="M18 15l-6-6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`
                  : `<svg width="12" height="12" viewBox="0 0 24 24" fill="none"><path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`
               }
               ${m.delta.value}
             </div>`
          : '<div class="metric-delta">—</div>'
        }
      </div>
    `).join('');
  }

  // ── 7. Render Course Bar Chart ──────────────────────────────
  function renderCourseChart(data) {
    if (courseChartInstance) courseChartInstance.destroy();

    const ctx = document.getElementById('courseChart').getContext('2d');
    const courses  = data.by_course?.map(c => c.course)  ?? [];
    const counts   = data.by_course?.map(c => c.count)   ?? [];

    courseChartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: courses,
        datasets: [{
          label: 'Check-ins',
          data: counts,
          backgroundColor: 'rgba(240,165,0,0.20)',
          borderColor: 'rgba(240,165,0,0.85)',
          borderWidth: 2,
          borderRadius: 5,
          borderSkipped: false,
          hoverBackgroundColor: 'rgba(240,165,0,0.35)',
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { intersect: false, mode: 'index' },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1c2330',
            borderColor: '#2a3441',
            borderWidth: 1,
            titleColor: '#e6edf3',
            bodyColor: '#8b949e',
            padding: 10,
            callbacks: {
              label: ctx => ` ${ctx.parsed.y} students`,
            }
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#8b949e', maxRotation: 30 },
          },
          y: {
            beginAtZero: true,
            grid: { color: '#2a3441' },
            ticks: {
              color: '#8b949e',
              precision: 0,
              callback: v => v,
            },
          },
        },
      }
    });
  }

  // ── 8. Render Trend Line Chart ──────────────────────────────
  function renderTrendChart(data) {
    if (trendChartInstance) trendChartInstance.destroy();

    const ctx    = document.getElementById('trendChart').getContext('2d');
    const labels = data.daily_trend?.map(d => {
      const dt = new Date(d.date);
      return dt.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
    }) ?? [];
    const counts = data.daily_trend?.map(d => d.count) ?? [];

    // Create gradient fill
    const gradient = ctx.createLinearGradient(0, 0, 0, 220);
    gradient.addColorStop(0, 'rgba(88,166,255,0.28)');
    gradient.addColorStop(1, 'rgba(88,166,255,0.00)');

    trendChartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Daily check-ins',
          data: counts,
          borderColor: '#58a6ff',
          borderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: '#58a6ff',
          pointBorderColor: '#0d1117',
          pointBorderWidth: 2,
          fill: true,
          backgroundColor: gradient,
          tension: 0.4,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { intersect: false, mode: 'index' },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1c2330',
            borderColor: '#2a3441',
            borderWidth: 1,
            titleColor: '#e6edf3',
            bodyColor: '#8b949e',
            padding: 10,
            callbacks: {
              label: ctx => ` ${ctx.parsed.y} check-ins`,
            }
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#8b949e', maxTicksLimit: 8 },
          },
          y: {
            beginAtZero: true,
            grid: { color: '#2a3441' },
            ticks: {
              color: '#8b949e',
              precision: 0,
            },
          },
        },
      }
    });
  }

  // ── 9. Render Recent Feed ───────────────────────────────────
  function renderFeed(records) {
    const list = document.getElementById('feed-list');
    if (!records || records.length === 0) {
      list.innerHTML = `
        <div class="feed-item" style="justify-content:center;padding:24px;">
          <span class="text-muted">No recent check-ins to display.</span>
        </div>`;
      return;
    }

    list.innerHTML = records.slice(0, 8).map((r, i) => {
      const initials = (r.student_name || 'S')
        .split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
      return `
        <div class="feed-item" style="animation-delay:${i * 0.05}s">
          <div class="fi-avatar">${initials}</div>
          <div class="fi-body">
            <div class="fi-name">${r.student_name || '—'}</div>
            <div class="fi-meta">${r.course_name || r.course || '—'}</div>
          </div>
          <div class="fi-time">${timeAgo(r.timestamp)}</div>
        </div>`;
    }).join('');
  }

  // ── 10. Main Data Load ──────────────────────────────────────
  async function loadDashboard() {
    try {
      const [summary, recent] = await Promise.all([
        API.getStatisticsSummary(),
        API.getAttendanceRecords({ page: 1 }),
      ]);

      renderMetrics(summary);
      renderCourseChart(summary);
      renderTrendChart(summary);
      renderFeed(recent.records || []);

    } catch (err) {
      showToast(err.message || 'Failed to load dashboard data.', 'error');
      console.error('[Dashboard] Load error:', err);

      // Render empty/fallback state so page doesn't break
      renderMetrics({});
      renderCourseChart({ by_course: [] });
      renderTrendChart({ daily_trend: [] });
      renderFeed([]);
    }
  }

  // ── 11. Refresh button ──────────────────────────────────────
  document.getElementById('refresh-btn').addEventListener('click', async () => {
    const btn = document.getElementById('refresh-btn');
    btn.disabled = true;
    await loadDashboard();
    showToast('Dashboard refreshed', 'success', 1800);
    btn.disabled = false;
  });

  // ── 12. Boot ───────────────────────────────────────────────
  await loadDashboard();

})();
