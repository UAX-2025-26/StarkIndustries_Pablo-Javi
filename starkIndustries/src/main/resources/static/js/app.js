let stompClient = null;
let temperatureChart, motionChart, accessChart;
let prevTotals = { MOTION: 0, TEMPERATURE: 0, ACCESS: 0 };

// Buffers para muestreo a 5s
const SAMPLE_INTERVAL_MS = 5000;
let sampleTimer = null;
let tempBuffer = [];
let lastTempValue = null;
let motionSum = 0;
let accessSum = 0;

document.getElementById("loginBtnSubmit").addEventListener("click", login);

function login() {
    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value.trim();
    const errorDiv = document.getElementById("loginError");

    if (
        (username === "admin" && password === "admin123") ||
        (username === "jarvis" && password === "jarvis123")
    ) {
        document.getElementById("loginSection").classList.add("hidden");
        document.getElementById("dashboardSection").classList.remove("hidden");
        document.getElementById("logoutContainer").classList.remove("hidden");
        initDashboard();
    } else {
        errorDiv.textContent = "Credenciales incorrectas";
    }
}

function logout() {
    document.getElementById("loginSection").classList.remove("hidden");
    document.getElementById("dashboardSection").classList.add("hidden");
    document.getElementById("logoutContainer").classList.add("hidden");
    if (stompClient) stompClient.deactivate();
    // Limpiar muestreador y buffers
    if (sampleTimer) { clearInterval(sampleTimer); sampleTimer = null; }
    tempBuffer = []; lastTempValue = null; motionSum = 0; accessSum = 0;
}

function initDashboard() {
    initTemperatureChart();
    initMotionChart();
    initAccessChart();
    connectWebSocket();
}

function connectWebSocket() {
    const socket = new SockJS("/ws");
    const StompLib = window.StompJs || window.Stomp;

    stompClient = new StompLib.Client({
        webSocketFactory: () => socket,
        onConnect: () => {
            console.log("✅ WebSocket conectado");

            // Snapshot de métricas (totales y críticos)
            stompClient.subscribe("/topic/stats", (msg) => {
                const stats = JSON.parse(msg.body);
                updateMetrics(stats);
            });

            // Alertas
            stompClient.subscribe("/topic/alerts", (msg) => {
                const alert = JSON.parse(msg.body);
                displayAlert(alert);
            });

            // Eventos individuales por tipo: acumular para muestreo cada 5s
            stompClient.subscribe("/topic/sensors/temperature", (msg) => {
                try {
                    const ev = JSON.parse(msg.body);
                    const val = Number(ev.value);
                    if (!Number.isNaN(val)) {
                        tempBuffer.push(val);
                        lastTempValue = val;
                    }
                } catch (_) {}
            });

            stompClient.subscribe("/topic/sensors/motion", (msg) => {
                try {
                    const ev = JSON.parse(msg.body);
                    const val = Number(ev.value);
                    if (!Number.isNaN(val)) motionSum += val;
                } catch (_) {}
            });

            stompClient.subscribe("/topic/sensors/access", (msg) => {
                try {
                    const ev = JSON.parse(msg.body);
                    // Para accesos: éxito=1 (value 0), fallos = N intentos
                    const raw = Number(ev.value);
                    const val = Number.isNaN(raw) ? 0 : (raw === 0 ? 1 : raw);
                    accessSum += val;
                } catch (_) {}
            });

            // Iniciar muestreo a 5s
            startSampling();
        },
        onStompError: (frame) => console.error("❌ STOMP error:", frame),
        onWebSocketError: (err) => console.error("❌ WebSocket error:", err),
    });

    stompClient.activate();
}

function startSampling() {
    if (sampleTimer) { clearInterval(sampleTimer); }
    sampleTimer = setInterval(() => {
        const now = Date.now();
        // Temperatura: media del intervalo, si no hubo lecturas usar última conocida
        let tempVal;
        if (tempBuffer.length > 0) {
            const sum = tempBuffer.reduce((a, b) => a + b, 0);
            tempVal = sum / tempBuffer.length;
        } else if (lastTempValue != null) {
            tempVal = lastTempValue;
        }
        if (tempVal != null) addRealtimePoint(temperatureChart, tempVal, now);

        // Movimiento: suma de detecciones en ventana
        addRealtimePoint(motionChart, motionSum, now);

        // Accesos: suma de eventos ponderados en ventana
        addRealtimePoint(accessChart, accessSum, now);

        // Reset buffers
        tempBuffer = [];
        motionSum = 0;
        accessSum = 0;
    }, SAMPLE_INTERVAL_MS);
}

function updateMetrics(stats) {
    // stats viene como { totalEvents: {MOTION: n, TEMPERATURE: n, ACCESS: n}, criticalEvents: {...}, ... }
    const totalMap = (stats && stats.totalEvents) || {};
    const criticalMap = (stats && stats.criticalEvents) || {};

    const sumValues = (obj) => Object.values(obj || {}).reduce((a, b) => a + (Number(b) || 0), 0);

    // Totales globales
    document.getElementById("totalEvents").textContent = sumValues(totalMap);
    document.getElementById("criticalEvents").textContent = sumValues(criticalMap);

    // Por tipo (acumulados)
    const motionTotal = totalMap.MOTION ?? 0;
    const tempTotal = totalMap.TEMPERATURE ?? 0;
    const accessTotal = totalMap.ACCESS ?? 0;

    document.getElementById("motionEvents").textContent = motionTotal;
    document.getElementById("tempEvents").textContent = tempTotal;
    document.getElementById("accessEvents").textContent = accessTotal;

    prevTotals = { MOTION: motionTotal, TEMPERATURE: tempTotal, ACCESS: accessTotal };
}

function displayAlert(alert) {
    const container = document.getElementById("alertsContainer");
    const p = document.createElement("p");

    const type = alert && typeof alert.type === 'string' ? alert.type : (alert && alert.level ? alert.level : 'alerta');
    const timestamp = alert && (alert.timestamp || new Date().toLocaleTimeString());
    const message = alert && (alert.message || alert.title) ? (alert.message || alert.title) : '';
    const level = alert && alert.level ? alert.level : '';

    p.textContent = `${timestamp} | ${String(type).toUpperCase()} | ${message}`;
    p.classList.add("alert-item");
    if (level === "CRITICAL") p.classList.add("critical");
    container.prepend(p);
}

// Añade un punto (x=timestamp, y=valor) y refresca silenciosamente
function addRealtimePoint(chart, value, ts = Date.now()) {
    if (!chart || !chart.data || !chart.data.datasets || !chart.data.datasets[0]) return;
    chart.data.datasets[0].data.push({ x: ts, y: Number(value) || 0 });
    chart.update('none');
}

function initTemperatureChart() {
    const ctx = document.getElementById("temperatureChart").getContext("2d");
    temperatureChart = new Chart(ctx, {
        type: "line",
        data: {
            datasets: [{
                label: "Temperatura (°C)",
                borderColor: "#ff4d4d",
                backgroundColor: "rgba(255,77,77,0.15)",
                borderWidth: 2,
                pointRadius: 3, // mostrar puntos visibles
                pointBackgroundColor: "#ff4d4d",
                tension: 0.25,
                showLine: true,
                data: []
            }]
        },
        options: {
            interaction: { intersect: false },
            animation: false,
            scales: {
                x: { type: "realtime", realtime: { delay: 2000, duration: 60000, frameRate: 30 } },
                y: { beginAtZero: true, suggestedMax: 60 },
            },
            plugins: { legend: { labels: { color: '#fff' } } }
        },
    });
}

function initMotionChart() {
    const ctx = document.getElementById("motionChart").getContext("2d");
    motionChart = new Chart(ctx, {
        type: "bar",
        data: {
            datasets: [{
                label: "Movimiento (detecciones/min)",
                borderColor: "rgba(54, 162, 235, 1)",
                backgroundColor: "rgba(54, 162, 235, 0.85)",
                borderWidth: 1,
                barThickness: 10,
                data: []
            }]
        },
        options: {
            interaction: { intersect: false },
            animation: false,
            scales: {
                x: { type: "realtime", realtime: { delay: 2000, duration: 60000, frameRate: 30 } },
                y: { beginAtZero: true, suggestedMax: 20 },
            },
            plugins: { legend: { labels: { color: '#fff' } } }
        },
    });
}

function initAccessChart() {
    const ctx = document.getElementById("accessChart").getContext("2d");
    accessChart = new Chart(ctx, {
        type: "bar",
        data: {
            datasets: [{
                label: "Accesos (intentos)",
                borderColor: "rgba(75, 192, 192, 1)",
                backgroundColor: "rgba(75, 192, 192, 0.85)",
                borderWidth: 1,
                barThickness: 10,
                data: []
            }]
        },
        options: {
            interaction: { intersect: false },
            animation: false,
            scales: {
                x: { type: "realtime", realtime: { delay: 2000, duration: 60000, frameRate: 30 } },
                y: { beginAtZero: true, suggestedMax: 5 },
            },
            plugins: { legend: { labels: { color: '#fff' } } }
        },
    });
}
