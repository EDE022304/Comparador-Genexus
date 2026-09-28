document.addEventListener('DOMContentLoaded', () => {
    let currentObjects = [];
    let selectedObject = null;
    let selectedPart = 'Definition';
    let currentDiffData = null;
    let viewMode = 'side-by-side'; // 'side-by-side' or 'unified'

    // DOM Elements
    const uploadForm = document.getElementById('uploadForm');
    const file1Input = document.getElementById('file1');
    const file2Input = document.getElementById('file2');
    const fileName1 = document.getElementById('fileName1');
    const fileName2 = document.getElementById('fileName2');
    const dz1 = document.getElementById('dz1');
    const dz2 = document.getElementById('dz2');
    const btnLoadSample = document.getElementById('btnLoadSample');

    const loadingSpinner = document.getElementById('loadingSpinner');
    const resultsSection = document.getElementById('resultsSection');
    const objectSelect = document.getElementById('objectSelect');
    const filterSearch = document.getElementById('filterSearch');
    const chkOnlyChanges = document.getElementById('chkOnlyChanges');
    const btnSideBySide = document.getElementById('btnSideBySide');
    const btnUnified = document.getElementById('btnUnified');

    const statAdded = document.getElementById('statAdded');
    const statDeleted = document.getElementById('statDeleted');
    const statModified = document.getElementById('statModified');
    const statTotal = document.getElementById('statTotal');
    const diffViewContainer = document.getElementById('diffViewContainer');
    const minimapTrack = document.getElementById('minimapTrack');

    // Drag and Drop
    setupDropzone(dz1, file1Input, fileName1);
    setupDropzone(dz2, file2Input, fileName2);

    function setupDropzone(dz, input, nameLabel) {
        dz.addEventListener('click', () => input.click());
        dz.addEventListener('dragover', (e) => {
            e.preventDefault();
            dz.classList.add('dragover');
        });
        dz.addEventListener('dragleave', () => dz.classList.remove('dragover'));
        dz.addEventListener('drop', (e) => {
            e.preventDefault();
            dz.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                input.files = e.dataTransfer.files;
                updateFileName(input, nameLabel);
            }
        });
        input.addEventListener('change', () => updateFileName(input, nameLabel));
    }

    function updateFileName(input, label) {
        if (input.files && input.files.length > 0) {
            label.textContent = input.files[0].name;
            label.style.color = '#0052cc';
        } else {
            label.textContent = 'Ningún archivo seleccionado';
            label.style.color = '#64748b';
        }
    }

    // Load sample files button
    btnLoadSample.addEventListener('click', async () => {
        showLoading(true);
        try {
            const res = await fetch('/api/load-sample');
            const data = await res.json();
            if (data.error) {
                alert(data.error);
            } else {
                handleObjectsLoaded(data);
            }
        } catch (err) {
            alert('Error al cargar archivos de muestra: ' + err.message);
        } finally {
            showLoading(false);
        }
    });

    // Form Submit
    uploadForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!file1Input.files[0] || !file2Input.files[0]) {
            alert('Por favor selecciona ambos archivos XPZ (V1 y V2).');
            return;
        }

        const formData = new FormData();
        formData.append('file1', file1Input.files[0]);
        formData.append('file2', file2Input.files[0]);

        showLoading(true);

        try {
            const res = await fetch('/api/upload', {
                method: 'POST',
                body: formData
            });
            const data = await res.json();
            if (data.error) {
                alert('Error al procesar XPZ: ' + data.error);
            } else {
                handleObjectsLoaded(data);
            }
        } catch (err) {
            alert('Error de conexión con el servidor: ' + err.message);
        } finally {
            showLoading(false);
        }
    });

    function showLoading(show) {
        if (show) {
            loadingSpinner.classList.remove('hidden');
            resultsSection.classList.add('hidden');
        } else {
            loadingSpinner.classList.add('hidden');
        }
    }

    function handleObjectsLoaded(data) {
        currentObjects = data.objects || [];
        resultsSection.classList.remove('hidden');

        // Populate Objects Select
        objectSelect.innerHTML = '';
        currentObjects.forEach(obj => {
            const opt = document.createElement('option');
            opt.value = obj.name;

            let icon = '⚪';
            if (obj.status === 'ADDED') icon = '🟢 [AGREGADO]';
            else if (obj.status === 'DELETED') icon = '🔴 [QUITADO]';
            else if (obj.status === 'MODIFIED') icon = '🟡 [MODIFICADO]';
            else icon = '⚪ [SIN CAMBIOS]';

            opt.textContent = `${icon} ${obj.name} (${obj.type || 'Objeto'})`;
            objectSelect.appendChild(opt);
        });

        if (currentObjects.length > 0) {
            const modifiedObj = currentObjects.find(o => o.status === 'MODIFIED') || currentObjects[0];
            objectSelect.value = modifiedObj.name;
            selectedObject = modifiedObj.name;
            loadDiff(selectedObject, selectedPart);
        }
    }

    // Object Dropdown Change
    objectSelect.addEventListener('change', (e) => {
        selectedObject = e.target.value;
        loadDiff(selectedObject, selectedPart);
    });

    // GeneXus Tabs Click
    document.querySelectorAll('.gx-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            document.querySelectorAll('.gx-tab').forEach(t => t.classList.remove('active'));
            const clickedTab = e.currentTarget;
            clickedTab.classList.add('active');
            selectedPart = clickedTab.dataset.part;
            loadDiff(selectedObject, selectedPart);
        });
    });

    // Filters and View Mode
    filterSearch.addEventListener('input', () => renderDiff());
    chkOnlyChanges.addEventListener('change', () => renderDiff());

    btnSideBySide.addEventListener('click', () => {
        btnSideBySide.classList.add('active');
        btnUnified.classList.remove('active');
        viewMode = 'side-by-side';
        renderDiff();
    });

    btnUnified.addEventListener('click', () => {
        btnUnified.classList.add('active');
        btnSideBySide.classList.remove('active');
        viewMode = 'unified';
        renderDiff();
    });

    async function loadDiff(objName, part) {
        if (!objName) return;
        try {
            const res = await fetch(`/api/diff?object=${encodeURIComponent(objName)}&part=${encodeURIComponent(part)}`);
            const data = await res.json();
            currentDiffData = data;
            renderDiff();
        } catch (err) {
            diffViewContainer.innerHTML = `<div style="padding:2rem;color:red;">Error al cargar diferencias: ${err.message}</div>`;
        }
    }

    function renderDiff() {
        if (!currentDiffData || !currentDiffData.lines) {
            diffViewContainer.innerHTML = '<div style="padding:2rem;text-align:center;color:#64748b;">No hay contenido disponible para esta sección.</div>';
            minimapTrack.innerHTML = '';
            updateStats(0, 0, 0, 0);
            return;
        }

        const filterText = filterSearch.value.trim().toLowerCase();
        const onlyChanges = chkOnlyChanges.checked;

        let filteredLines = currentDiffData.lines.filter(line => {
            if (onlyChanges && line.type === 'UNCHANGED') return false;
            if (filterText) {
                const oldMatch = line.oldContent && line.oldContent.toLowerCase().includes(filterText);
                const newMatch = line.newContent && line.newContent.toLowerCase().includes(filterText);
                return oldMatch || newMatch;
            }
            return true;
        });

        // Update Stats
        updateStats(
            currentDiffData.stats.added,
            currentDiffData.stats.deleted,
            currentDiffData.stats.modified,
            currentDiffData.stats.totalLines
        );

        if (filteredLines.length === 0) {
            diffViewContainer.innerHTML = '<div style="padding:2rem;text-align:center;color:#64748b;">Sin diferencias encontradas con los filtros aplicados.</div>';
            minimapTrack.innerHTML = '';
            return;
        }

        if (viewMode === 'side-by-side') {
            renderSynchronizedSideBySide(filteredLines);
        } else {
            renderUnified(filteredLines);
        }

        renderMinimap(filteredLines);
    }

    function updateStats(added, deleted, modified, total) {
        statAdded.textContent = `+${added}`;
        statDeleted.textContent = `-${deleted}`;
        statModified.textContent = `~${modified}`;
        statTotal.textContent = `${total}`;
    }

    function renderSynchronizedSideBySide(lines) {
        const nameV1 = currentDiffData.nameV1 || 'Versión 1 (Base)';
        const nameV2 = currentDiffData.nameV2 || 'Versión 2 (Modificada)';

        let html = `
        <div class="sbs-container">
            <!-- Left Panel V1 -->
            <div class="sbs-panel" id="panelV1">
                <div class="sbs-header"><span>📄 V1: ${escapeHtml(nameV1)}</span></div>
                <table class="diff-table"><tbody>`;

        lines.forEach((line, idx) => {
            let rowClass = 'line-unchanged';
            if (line.type === 'ADDED') rowClass = 'line-added';
            else if (line.type === 'DELETED') rowClass = 'line-deleted';
            else if (line.type === 'MODIFIED') rowClass = 'line-modified';

            const oldNo = line.oldLine ? line.oldLine : '';
            const oldText = escapeHtml(line.oldContent != null ? line.oldContent : '');

            if (line.type === 'ADDED') {
                html += `<tr class="line-added" id="row-left-${idx}"><td class="lineno"></td><td class="code-cell line-empty"></td></tr>`;
            } else {
                html += `<tr class="${rowClass}" id="row-left-${idx}"><td class="lineno">${oldNo}</td><td class="code-cell">${oldText}</td></tr>`;
            }
        });

        html += `</tbody></table></div>`;

        // Right Panel V2
        html += `
            <div class="sbs-panel" id="panelV2">
                <div class="sbs-header"><span>📄 V2: ${escapeHtml(nameV2)}</span></div>
                <table class="diff-table"><tbody>`;

        lines.forEach((line, idx) => {
            let rowClass = 'line-unchanged';
            if (line.type === 'ADDED') rowClass = 'line-added';
            else if (line.type === 'DELETED') rowClass = 'line-deleted';
            else if (line.type === 'MODIFIED') rowClass = 'line-modified';

            const newNo = line.newLine ? line.newLine : '';
            const newText = escapeHtml(line.newContent != null ? line.newContent : '');

            if (line.type === 'DELETED') {
                html += `<tr class="line-deleted" id="row-right-${idx}"><td class="lineno"></td><td class="code-cell line-empty"></td></tr>`;
            } else {
                html += `<tr class="${rowClass}" id="row-right-${idx}"><td class="lineno">${newNo}</td><td class="code-cell">${newText}</td></tr>`;
            }
        });

        html += `</tbody></table></div></div>`;

        diffViewContainer.innerHTML = html;

        // Synchronize Vertical & Horizontal Scroll
        const p1 = document.getElementById('panelV1');
        const p2 = document.getElementById('panelV2');

        let isSyncingVert = false;
        let isSyncingHoriz = false;

        p1.addEventListener('scroll', () => {
            if (!isSyncingVert) {
                isSyncingVert = true;
                p2.scrollTop = p1.scrollTop;
                requestAnimationFrame(() => isSyncingVert = false);
            }
            if (!isSyncingHoriz) {
                isSyncingHoriz = true;
                p2.scrollLeft = p1.scrollLeft;
                requestAnimationFrame(() => isSyncingHoriz = false);
            }
        });

        p2.addEventListener('scroll', () => {
            if (!isSyncingVert) {
                isSyncingVert = true;
                p1.scrollTop = p2.scrollTop;
                requestAnimationFrame(() => isSyncingVert = false);
            }
            if (!isSyncingHoriz) {
                isSyncingHoriz = true;
                p1.scrollLeft = p2.scrollLeft;
                requestAnimationFrame(() => isSyncingHoriz = false);
            }
        });
    }

    function renderUnified(lines) {
        let html = '<div id="panelUnified" style="overflow:auto;height:100%;"><table class="diff-table"><tbody>';
        lines.forEach((line, idx) => {
            let rowClass = 'line-unchanged';
            let prefix = ' ';
            let content = line.newContent != null ? line.newContent : line.oldContent;

            if (line.type === 'ADDED') {
                rowClass = 'line-added';
                prefix = '+';
            } else if (line.type === 'DELETED') {
                rowClass = 'line-deleted';
                prefix = '-';
            } else if (line.type === 'MODIFIED') {
                rowClass = 'line-modified';
                prefix = '~';
            }

            const oldNo = line.oldLine ? line.oldLine : '';
            const newNo = line.newLine ? line.newLine : '';

            html += `<tr class="${rowClass}" id="row-unified-${idx}">`;
            html += `<td class="lineno">${oldNo}</td>`;
            html += `<td class="lineno">${newNo}</td>`;
            html += `<td class="code-cell">${prefix} ${escapeHtml(content != null ? content : '')}</td>`;
            html += `</tr>`;
        });
        html += '</tbody>mtable></div>';
        diffViewContainer.innerHTML = html;
    }

    function renderMinimap(lines) {
        minimapTrack.innerHTML = '';
        const total = lines.length;
        if (total === 0) return;

        lines.forEach((line, idx) => {
            if (line.type !== 'UNCHANGED') {
                const tick = document.createElement('div');
                tick.className = 'minimap-tick ';
                if (line.type === 'ADDED') tick.className += 'minimap-tick-added';
                else if (line.type === 'DELETED') tick.className += 'minimap-tick-deleted';
                else if (line.type === 'MODIFIED') tick.className += 'minimap-tick-modified';

                const pct = (idx / total) * 100;
                tick.style.top = `${pct}%`;
                tick.title = `Línea ${line.newLine || line.oldLine}: ${line.type}`;

                tick.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (viewMode === 'side-by-side') {
                        const rowLeft = document.getElementById(`row-left-${idx}`);
                        const p1 = document.getElementById('panelV1');
                        if (rowLeft && p1) {
                            const targetTop = rowLeft.offsetTop - 80;
                            p1.scrollTop = Math.max(0, targetTop);
                        }
                    } else {
                        const rowUni = document.getElementById(`row-unified-${idx}`);
                        const pUni = document.getElementById('panelUnified');
                        if (rowUni && pUni) {
                            const targetTop = rowUni.offsetTop - 80;
                            pUni.scrollTop = Math.max(0, targetTop);
                        }
                    }
                });

                minimapTrack.appendChild(tick);
            }
        });
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
});
