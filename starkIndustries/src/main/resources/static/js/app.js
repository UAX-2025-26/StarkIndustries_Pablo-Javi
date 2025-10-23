// Lógica principal de la aplicación (separada)
(function() {
  let token = null;
  let stompClient = null;
  let eventsChart = null;
  let temperatureChart = null;
  let motionChart = null;
  let accessChart = null;
  let statsTimer = null;
  let alertsTimer = null;
  let tempTimer = null;
  let motionTimer = null;
  let accessTimer = null;

  // Para suavizar la métrica de threads activos
  let lastActiveThreads = 0;
  let lastActiveTs = 0;

  document.addEventListener('DOMContentLoaded', () => {
    const loginBtn = document.getElementById('loginBtnSubmit');
    if (loginBtn) loginBtn.addEventListener('click', login);
  });

  async function login() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const errorBox = document.getElementById('loginError');

    errorBox.textContent = '';

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!res.ok) {
        const txt = await res.text();
        errorBox.textContent = res.status === 401 ? 'Credenciales inválidas' : `Error ${res.status}: ${txt}`;
        return;
      }

      const data = await res.json();
      token = data.token;
      document.getElementById('loginSection').classList.add('hidden');
      document.getElementById('dashboardSection').classList.remove('hidden');

      initDashboard();
    } catch (e) {
      errorBox.textContent = 'Error de conexión con el servidor.';
      console.error(e);
    }
  }


  function initDashboard() {
    initTemperatureChart();
    initMotionChart();
    initAccessChart();
    connectWebSocket();
    loadStatistics();
    loadActiveAlerts();
    loadRecentTemperatures();
    loadRecentMotion();
    loadRecentAccess();

    statsTimer = setInterval(loadStatistics, 5000);
    alertsTimer = setInterval(loadActiveAlerts, 5000);
    tempTimer = setInterval(loadRecentTemperatures, 5000);
    motionTimer = setInterval(loadRecentMotion, 5000);
    accessTimer = setInterval(loadRecentAccess, 5000);
  }

  function initTemperatureChart() {
    const ctx = document.getElementById('temperatureChart').getContext('2d');
    temperatureChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'Temperatura (°C)',
          data: [],
          borderColor: '#111',
          backgroundColor: 'rgba(0,0,0,0.05)',
          tension: 0.2,
          pointRadius: 3,
          pointBackgroundColor: '#111'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: { beginAtZero: false, grid: { color: '#eee' } },
          x: { grid: { display: false } }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.parsed.y.toFixed(1)} °C`
            }
          }
        }
      }
    });
  }

  function initMotionChart() {
    const ctx = document.getElementById('motionChart').getContext('2d');
    motionChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: [],
        datasets: [{
          label: 'Detecciones',
          data: [],
          backgroundColor: '#555',
          borderColor: '#333',
          borderWidth: 1
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: { beginAtZero: true, grid: { color: '#eee' } },
          x: { grid: { display: false } }
        },
        plugins: {
          legend: { display: false }
        }
      }
    });
  }

  function initAccessChart() {
    const ctx = document.getElementById('accessChart').getContext('2d');
    accessChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: [],
        datasets: [{
          label: 'Intentos',
          data: [],
          backgroundColor: '#999',
          borderColor: '#777',
          borderWidth: 1
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: { beginAtZero: true, grid: { color: '#eee' } },
          x: { grid: { display: false } }
        },
        plugins: {
          legend: { display: false }
        }
      }
    });
  }

  function connectWebSocket() {
    const socket = new SockJS('/ws');
    // @stomp/stompjs v5 usa una API diferente
    const StompJs = window.StompJs || window;
    stompClient = new StompJs.Client({
      webSocketFactory: () => socket,
      onConnect: (frame) => {
        console.log('✅ WebSocket conectado');

        stompClient.subscribe('/topic/stats', (msg) => {
          try {
            const snapshot = JSON.parse(msg.body);
            console.log('📊 Stats recibidas:', snapshot);
            const normalized = normalizeStats(snapshot);
            updateMetrics(normalized);
          } catch (e) { console.error('WS stats parse error', e); }
        });

        stompClient.subscribe('/topic/alerts', (msg) => {
          try {
            const alert = JSON.parse(msg.body);
            console.log('🚨 Alerta recibida:', alert);
            displayAlert(alert);
          } catch (e) { console.error('WS alert parse error', e); }
        });

        try {
          stompClient.publish({
            destination: '/app/stats/request',
            body: 'init'
          });
        } catch (e) {
          console.error('Error solicitando stats:', e);
        }
      },
      onStompError: (frame) => {
        console.error('❌ STOMP error:', frame);
      },
      onWebSocketError: (err) => {
        console.error('❌ WebSocket error:', err);
      }
    });

    stompClient.activate();
  }

  async function loadStatistics() {
    try {
      const res = await fetch('/api/sensors/statistics', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) {
        console.warn('Stats HTTP error:', res.status);
        return;
      }
      const stats = await res.json();
      console.log('📊 Stats HTTP:', stats);
      const normalized = normalizeStats(stats);
      updateMetrics(normalized);
    } catch (e) {
      console.error('Error cargando stats:', e);
    }
  }

  async function loadActiveAlerts() {
    try {
      const res = await fetch('/api/alerts/active', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) return;
      const alerts = await res.json();
      const container = document.getElementById('alertsContainer');
      container.innerHTML = '';
      if (!alerts || alerts.length === 0) {
        container.innerHTML = '<p class="muted">Esperando eventos...</p>';
        document.getElementById('activeAlerts').textContent = 0;
        return;
      }
      document.getElementById('activeAlerts').textContent = alerts.length;
      alerts.slice(0, 10).forEach(displayAlert);
    } catch (e) { console.error(e); }
  }

  function displayAlert(alert) {
    const container = document.getElementById('alertsContainer');
    const level = (alert.level || '').toString().toLowerCase();
    const title = alert.title || 'Alerta de Seguridad';
    const location = alert.location || '-';
    const ts = alert.timestamp || alert.createdAt || new Date().toISOString();
    const message = alert.message || alert.description || '';

    const el = document.createElement('div');
    el.className = `alert-item ${level}`;
    el.innerHTML = `<strong>${title}</strong><br><small>${location} - ${new Date(ts).toLocaleString()}</small><br>${message}`;
    container.insertBefore(el, container.firstChild);

    while (container.children.length > 10) container.removeChild(container.lastChild);
  }

  async function loadRecentTemperatures() {
    try {
      const url = '/api/sensors/temperatures/recent?minutes=120&limit=50&criticalOnly=false';
      const res = await fetch(url, { headers: { 'Authorization': `Bearer ${token}` } });
      if (!res.ok) return;
      const data = await res.json();
      updateTemperatureChart(data);
    } catch (e) { console.error(e); }
  }

  async function loadRecentMotion() {
    try {
      const url = '/api/sensors/motion/recent?minutes=120&limit=50';
      const res = await fetch(url, { headers: { 'Authorization': `Bearer ${token}` } });
      if (!res.ok) return;
      const data = await res.json();
      updateMotionChart(data);
    } catch (e) { console.error(e); }
  }

  async function loadRecentAccess() {
    try {
      const url = '/api/sensors/access/recent?minutes=120&limit=50';
      const res = await fetch(url, { headers: { 'Authorization': `Bearer ${token}` } });
      if (!res.ok) return;
      const data = await res.json();
      updateAccessChart(data);
    } catch (e) { console.error(e); }
  }

  function updateTemperatureChart(series) {
    if (!temperatureChart || !series || series.length === 0) return;
    const labels = series.map(p => new Date(p.timestamp).toLocaleTimeString());
    const values = series.map(p => Number(p.value || 0));

    temperatureChart.data.labels = labels;
    temperatureChart.data.datasets[0].data = values;
    const unit = (series && series[0] && series[0].unit) ? series[0].unit : '°C';
    temperatureChart.data.datasets[0].label = `Temperatura (${unit})`;
    temperatureChart.update();
  }

  function updateMotionChart(series) {
    if (!motionChart || !series || series.length === 0) return;
    const labels = series.map(p => new Date(p.timestamp).toLocaleTimeString());
    const values = series.map(p => Number(p.value || 0));

    motionChart.data.labels = labels;
    motionChart.data.datasets[0].data = values;
    motionChart.update();
  }

  function updateAccessChart(series) {
    if (!accessChart || !series || series.length === 0) return;
    const labels = series.map(p => new Date(p.timestamp).toLocaleTimeString());
    const values = series.map(p => Number(p.value || 0));

    accessChart.data.labels = labels;
    accessChart.data.datasets[0].data = values;
    accessChart.update();
  }

  function normalizeStats(stats) {
    const result = {
      totalEvents: { MOTION: 0, TEMPERATURE: 0, ACCESS: 0 },
      criticalEvents: { MOTION: 0, TEMPERATURE: 0, ACCESS: 0 },
      activeThreads: 0,
      threadPool: { active: 0, poolSize: 0, corePoolSize: 0, maxPoolSize: 0 }
    };

    if (stats && stats.totalEvents) {
      const t = stats.totalEvents;
      result.totalEvents.MOTION = Number(t.MOTION || t.motion || 0);
      result.totalEvents.TEMPERATURE = Number(t.TEMPERATURE || t.temperature || 0);
      result.totalEvents.ACCESS = Number(t.ACCESS || t.access || 0);
    }

    // Fallback a eventsByType si totalEvents está vacío
    if (result.totalEvents.MOTION === 0 && result.totalEvents.TEMPERATURE === 0 && result.totalEvents.ACCESS === 0) {
      if (stats && stats.eventsByType) {
        Object.entries(stats.eventsByType).forEach(([key, value]) => {
          const k = key.toUpperCase();
          if (k === 'MOTION' || k === 'TEMPERATURE' || k === 'ACCESS') {
            result.totalEvents[k] = Number(value || 0);
          }
        });
      }
    }

    if (stats && stats.criticalEvents) {
      const c = stats.criticalEvents;
      result.criticalEvents.MOTION = Number(c.MOTION || c.motion || 0);
      result.criticalEvents.TEMPERATURE = Number(c.TEMPERATURE || c.temperature || 0);
      result.criticalEvents.ACCESS = Number(c.ACCESS || c.access || 0);
    }

    if (stats && stats.threadPool) {
      const tp = stats.threadPool;
      result.threadPool.active = Number(tp.active || 0);
      result.threadPool.poolSize = Number(tp.poolSize || 0);
      result.threadPool.corePoolSize = Number(tp.corePoolSize || 0);
      result.threadPool.maxPoolSize = Number(tp.maxPoolSize || 0);
    }

    if (typeof stats?.activeThreads === 'number') {
      result.activeThreads = Number(stats.activeThreads);
    } else {
      result.activeThreads = result.threadPool.active;
    }

    return result;
  }

  function updateMetrics(stats) {
    const t = stats.totalEvents;
    const c = stats.criticalEvents;

    const total = (t.MOTION || 0) + (t.TEMPERATURE || 0) + (t.ACCESS || 0);
    const critical = (c.MOTION || 0) + (c.TEMPERATURE || 0) + (c.ACCESS || 0);

    document.getElementById('totalEvents').textContent = total;
    document.getElementById('motionEvents').textContent = t.MOTION || 0;
    document.getElementById('tempEvents').textContent = t.TEMPERATURE || 0;
    document.getElementById('accessEvents').textContent = t.ACCESS || 0;
    document.getElementById('criticalEvents').textContent = critical;

    // Threads activos n/m (m = maxPoolSize | corePoolSize | poolSize)
    const now = Date.now();
    const activeNow = Number(stats.activeThreads || 0);
    const tp = stats.threadPool || {};
    const maxSize = Number(tp.maxPoolSize || 0);
    const coreSize = Number(tp.corePoolSize || 0);
    const poolSize = Number(tp.poolSize || 0);
    const capacity = maxSize > 0 ? maxSize : (coreSize > 0 ? coreSize : poolSize);

    if (!isNaN(activeNow) && activeNow > 0) {
      lastActiveThreads = activeNow;
      lastActiveTs = now;
    }
    const withinWindow = (now - lastActiveTs) < 5000;
    const displayActive = activeNow > 0 ? activeNow : (withinWindow ? lastActiveThreads : 0);

    document.getElementById('activeThreads').textContent = `${displayActive}/${capacity || 0}`;
  }

  // Expone logout para el botón del header
  window.logout = logout;
})();
