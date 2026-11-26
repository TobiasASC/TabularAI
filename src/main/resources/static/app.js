const API_BASE = '/api/v1/datasets';

// Estado actual en memoria del navegador
let currentDatasetId = null;

// Referencias a elementos del DOM
const uploadForm = document.getElementById('uploadForm');
const csvFileInput = document.getElementById('csvFile');
const userContextInput = document.getElementById('userContext');
const uploadBtn = document.getElementById('uploadBtn');

const datasetHistoryList = document.getElementById('datasetHistoryList');
const refreshHistoryBtn = document.getElementById('refreshHistoryBtn');

const alertBox = document.getElementById('alertBox');
const emptyState = document.getElementById('emptyState');
const datasetDetail = document.getElementById('datasetDetail');

const detailStatus = document.getElementById('detailStatus');
const detailFilename = document.getElementById('detailFilename');
const detailMeta = document.getElementById('detailMeta');
const detailRows = document.getElementById('detailRows');
const detailCols = document.getElementById('detailCols');
const detailSize = document.getElementById('detailSize');
const detailReportCount = document.getElementById('detailReportCount');
const deleteDatasetBtn = document.getElementById('deleteDatasetBtn');

const profilesTableBody = document.getElementById('profilesTableBody');
const newReportForm = document.getElementById('newReportForm');
const newReportContext = document.getElementById('newReportContext');
const newReportBtn = document.getElementById('newReportBtn');
const aiReportsContainer = document.getElementById('aiReportsContainer');

// Inicialización al cargar la página
document.addEventListener('DOMContentLoaded', () => {
    loadDatasetHistory();
});

// 1. Subir nuevo archivo CSV (POST /api/v1/datasets)
uploadForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert();

    const file = csvFileInput.files[0];
    if (!file) {
        showAlert('Selecciona un archivo CSV antes de continuar.', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    const contextValue = userContextInput.value.trim();
    if (contextValue) {
        formData.append('context', contextValue);
    }

    setButtonLoading(uploadBtn, true, 'Procesando CSV y consultando IA...');

    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.detail || 'Error al procesar el archivo CSV.');
        }

        uploadForm.reset();
        showAlert(`Dataset "${data.filename}" analizado correctamente.`, 'success');
        renderDatasetDetail(data);
        loadDatasetHistory();
    } catch (error) {
        showAlert(error.message, 'error');
    } finally {
        setButtonLoading(uploadBtn, false, 'Analizar Dataset con IA');
    }
});

// 2. Solicitar un nuevo reporte de IA sobre el dataset actual (POST /api/v1/datasets/{id}/reports)
newReportForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert();

    if (!currentDatasetId) return;

    const context = newReportContext.value.trim();
    if (!context) return;

    setButtonLoading(newReportBtn, true, 'Generando con IA...');

    try {
        const response = await fetch(`${API_BASE}/${currentDatasetId}/reports`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ context })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.detail || 'No se pudo generar el nuevo reporte de IA.');
        }

        newReportForm.reset();
        showAlert('Nuevo reporte de IA generado sin reprocesar el CSV.', 'success');
        // Recargamos el detalle del dataset para ver la lista actualizada de reportes
        await selectDataset(currentDatasetId);
    } catch (error) {
        showAlert(error.message, 'error');
    } finally {
        setButtonLoading(newReportBtn, false, '+ Nuevo Reporte IA');
    }
});

// 3. Eliminar el dataset actual (DELETE /api/v1/datasets/{id})
deleteDatasetBtn.addEventListener('click', async () => {
    if (!currentDatasetId) return;
    if (!confirm('¿Seguro que deseas eliminar este dataset y todos sus reportes asociados?')) return;

    hideAlert();
    try {
        const response = await fetch(`${API_BASE}/${currentDatasetId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('No se pudo eliminar el dataset.');
        }

        currentDatasetId = null;
        datasetDetail.classList.add('hidden');
        emptyState.classList.remove('hidden');
        showAlert('Dataset eliminado correctamente.', 'success');
        loadDatasetHistory();
    } catch (error) {
        showAlert(error.message, 'error');
    }
});

refreshHistoryBtn.addEventListener('click', () => loadDatasetHistory());

// 4. Cargar historial paginado (GET /api/v1/datasets)
async function loadDatasetHistory() {
    try {
        const response = await fetch(`${API_BASE}?page=0&size=20`);
        if (!response.ok) throw new Error('Error al cargar el historial.');

        const pageData = await response.json();
        const items = pageData.content || [];

        if (items.length === 0) {
            datasetHistoryList.innerHTML = '<p class="muted-text">Aún no hay datasets cargados.</p>';
            return;
        }

        datasetHistoryList.innerHTML = items.map(item => `
            <div class="history-item ${item.id === currentDatasetId ? 'active' : ''}" onclick="selectDataset(${item.id})">
                <div class="history-item-title">#${item.id} — ${escapeHtml(item.filename)}</div>
                <div class="history-item-meta">
                    ${item.rowCount} filas • ${item.columnCount} cols • ${formatDate(item.createdAt)}
                </div>
            </div>
        `).join('');
    } catch (error) {
        datasetHistoryList.innerHTML = `<p class="muted-text">${escapeHtml(error.message)}</p>`;
    }
}

// 5. Cargar detalle completo de un dataset por ID (GET /api/v1/datasets/{id})
async function selectDataset(datasetId) {
    hideAlert();
    try {
        const response = await fetch(`${API_BASE}/${datasetId}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.detail || 'No se pudo cargar el dataset.');
        }

        renderDatasetDetail(data);
        loadDatasetHistory();
    } catch (error) {
        showAlert(error.message, 'error');
    }
}

// 6. Renderizar los datos del Dataset en la interfaz
function renderDatasetDetail(dataset) {
    currentDatasetId = dataset.id;
    emptyState.classList.add('hidden');
    datasetDetail.classList.remove('hidden');

    detailStatus.textContent = dataset.status;
    detailFilename.textContent = dataset.filename;
    detailMeta.textContent = `ID: #${dataset.id} • Cargado: ${formatDate(dataset.createdAt)}`;
    detailRows.textContent = dataset.rowCount.toLocaleString('es-AR');
    detailCols.textContent = dataset.columnCount;
    detailSize.textContent = formatBytes(dataset.fileSizeBytes);

    const reports = dataset.aiReports || [];
    detailReportCount.textContent = reports.length;

    // Renderizar tabla de ColumnProfiles
    const profiles = dataset.columnProfiles || [];
    profilesTableBody.innerHTML = profiles.map(col => `
        <tr>
            <td><strong>${escapeHtml(col.columnName)}</strong></td>
            <td><span class="type-pill type-${col.dataType}">${col.dataType}</span></td>
            <td>${col.nullCount}</td>
            <td>${col.uniqueCount}</td>
            <td>${formatNumber(col.minVal)}</td>
            <td>${formatNumber(col.maxVal)}</td>
            <td>${formatNumber(col.meanVal)}</td>
            <td>${formatNumber(col.medianVal)}</td>
            <td>${renderTopValues(col.topValues)}</td>
        </tr>
    `).join('');

    // Renderizar tarjetas de AiReports (del más reciente al más antiguo)
    const sortedReports = [...reports].reverse();
    aiReportsContainer.innerHTML = sortedReports.map(rep => `
        <article class="report-card">
            <div class="report-header">
                <span class="report-context">Enfoque: "${escapeHtml(rep.userContext)}"</span>
                <span class="report-meta">Modelo: ${escapeHtml(rep.modelUsed)} • ${formatDate(rep.createdAt)}</span>
            </div>
            <p class="report-summary">${escapeHtml(rep.executiveSummary)}</p>
            <div class="report-findings-title">Hallazgos y Recomendaciones</div>
            <ul class="report-findings-list">
                ${(rep.findings || []).map(f => `<li>${escapeHtml(f)}</li>`).join('')}
            </ul>
        </article>
    `).join('');
}

// Utilidades de formato
function renderTopValues(topValues) {
    if (!topValues || Object.keys(topValues).length === 0) {
        return '<span class="muted-text">—</span>';
    }
    return Object.entries(topValues)
        .map(([key, count]) => `<span class="freq-chip">${escapeHtml(key)}: <strong>${count}</strong></span>`)
        .join(' ');
}

function formatNumber(num) {
    if (num === null || num === undefined) return '<span class="muted-text">—</span>';
    return Number(num).toLocaleString('es-AR', { maximumFractionDigits: 2 });
}

function formatBytes(bytes) {
    if (!bytes) return '0 B';
    if (bytes < 1024) return `${bytes} B`;
    return `${(bytes / 1024).toFixed(1)} KB`;
}

function formatDate(isoString) {
    if (!isoString) return '-';
    return new Date(isoString).toLocaleString('es-AR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function setButtonLoading(btn, isLoading, text) {
    btn.disabled = isLoading;
    btn.textContent = text;
}

function showAlert(message, type) {
    alertBox.textContent = message;
    alertBox.className = `alert alert-${type}`;
    alertBox.classList.remove('hidden');
}

function hideAlert() {
    alertBox.classList.add('hidden');
}

function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
