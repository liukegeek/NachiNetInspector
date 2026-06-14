const RESULT_KEYS = Object.freeze({
    robotName: '机器人名称',
    success: '成功解析',
    robotSelf: '机器人自身网络信息',
    subDevices: '子设备网络信息',
    warnings: '异常信息'
});

const DEVICE_KEYS = Object.freeze([
    '名称',
    'IP',
    '掩码',
    '网关',
    '来源文件',
    '记录头',
    '记录起始偏移量',
    '名称长度偏移量',
    '名称偏移量',
    'IP偏移量',
    '掩码偏移量',
    '网关偏移量'
]);

const state = {
    files: [],
    batch: null,
    selectedItemIndex: null,
    selectedDeviceKey: null,
    appVersion: ''
};

const dom = {};
let topologyGraph = null;

document.addEventListener('DOMContentLoaded', () => {
    [
        'fileUpload', 'fileUploadButton', 'startInspectionButton', 'exportExcelButton',
        'selectedFileCount', 'appVersion', 'emptyState', 'resultSidebar', 'resultWorkspace',
        'summaryCards', 'topology', 'warningSummary', 'deviceTableBody',
        'deviceDetailPanel', 'loadingOverlay'
    ].forEach((id) => {
        dom[id] = document.getElementById(id);
    });

    try {
        if (typeof cytoscapeDagre !== 'undefined') {
            cytoscape.use(cytoscapeDagre);
        }
    } catch (error) {
        console.warn('拓扑布局插件注册失败', error);
    }

    dom.fileUploadButton.addEventListener('click', () => dom.fileUpload.click());
    dom.fileUpload.addEventListener('change', (event) => {
        addSelectedDirectories(Array.from(event.target.files || []));
        event.target.value = '';
    });
    dom.startInspectionButton.addEventListener('click', startInspection);
    dom.exportExcelButton.addEventListener('click', exportExcel);

    if (typeof lucide !== 'undefined') {
        lucide.createIcons();
    }
    updateActionState();
    loadRuntimeStatus();
});

async function loadRuntimeStatus() {
    try {
        const response = await fetch('/api/runtime/status');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const payload = await response.json();
        state.appVersion = text(payload && payload.appVersion);
        dom.appVersion.textContent = state.appVersion ? `v${state.appVersion.replace(/^v/i, '')}` : '';
    } catch (error) {
        console.warn('读取应用版本失败', error);
        state.appVersion = '';
        dom.appVersion.textContent = '';
    }
}

async function startInspection() {
    if (!state.files.length || isLoading()) {
        return;
    }
    setLoading(true);
    showMessage('正在解析备份目录...', false);
    try {
        const response = await fetch('/api/inspection', {method: 'POST', body: buildFormData()});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        state.batch = await response.json();
        const items = batchItems();
        const usableIndex = items.findIndex((item) => item && item.status !== 'FAILED');
        state.selectedItemIndex = usableIndex >= 0 ? usableIndex : (items.length ? 0 : null);
        state.selectedDeviceKey = defaultDeviceKey(selectedItem());
        renderAll();
    } catch (error) {
        state.batch = null;
        state.selectedItemIndex = null;
        state.selectedDeviceKey = null;
        renderAll();
        showMessage(`解析失败：${error.message || '请求失败'}`, true);
    } finally {
        setLoading(false);
    }
}

async function exportExcel() {
    if (!state.files.length || isLoading()) {
        return;
    }
    setLoading(true);
    try {
        const response = await fetch('/api/inspection/excel', {method: 'POST', body: buildFormData()});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = 'Nachi网络信息.xlsx';
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    } catch (error) {
        showMessage(`Excel 导出失败：${error.message || '请求失败'}`, true);
    } finally {
        setLoading(false);
    }
}

function buildFormData() {
    const formData = new FormData();
    state.files.forEach(({file, relativePath}) => formData.append('files', file, relativePath));
    return formData;
}

function addSelectedDirectories(files) {
    const selected = files.map((file) => {
        const relativePath = text(file.webkitRelativePath || file.name).replaceAll('\\', '/');
        return {file, relativePath, directoryName: relativePath.split('/')[0]};
    }).filter((entry) => entry.directoryName);
    const selectedNames = new Set(selected.map((entry) => entry.directoryName));
    state.files = state.files
        .filter((entry) => !selectedNames.has(entry.directoryName))
        .concat(selected);
    state.batch = null;
    state.selectedItemIndex = null;
    state.selectedDeviceKey = null;
    renderAll();
}

function selectResult(index) {
    const item = batchItems()[index];
    if (!item) {
        return;
    }
    state.selectedItemIndex = index;
    state.selectedDeviceKey = defaultDeviceKey(item);
    renderAll();
}

function selectDevice(deviceKey) {
    if (!deviceByKey(selectedItem(), deviceKey)) {
        return;
    }
    state.selectedDeviceKey = deviceKey;
    renderDeviceTable();
    renderDeviceDetail();
    syncTopologySelection();
}

function renderAll() {
    const hasBatch = Boolean(state.batch && batchItems().length);
    dom.emptyState.classList.toggle('hidden', hasBatch);
    dom.resultSidebar.classList.toggle('hidden', !hasBatch);
    dom.resultWorkspace.classList.toggle('hidden', !hasBatch);
    dom.emptyState.classList.remove('error');
    if (!hasBatch && state.batch) {
        showMessage('没有可展示的解析结果。', true);
    }
    renderSidebar();
    renderSummary();
    renderTopology();
    renderDeviceTable();
    renderWarnings();
    renderDeviceDetail();
    updateActionState();
}

function renderSidebar() {
    dom.resultSidebar.replaceChildren();
    if (!state.batch) {
        return;
    }
    const title = element('h2', 'sidebar-title', `解析结果 · ${batchItems().length}`);
    const list = element('div', 'result-list');
    batchItems().forEach((item, index) => {
        const button = element('button', `result-item${index === state.selectedItemIndex ? ' active' : ''}`);
        button.type = 'button';
        const status = text(item && item.status) || 'FAILED';
        const sourceDirectory = text(item && item.sourceFileName) || `备份目录 ${index + 1}`;
        const robotName = text(value(resultOf(item), RESULT_KEYS.robotName));
        button.append(
            element('strong', '', robotName || sourceDirectory),
            element('span', 'source-directory', `源目录：${sourceDirectory}`),
            element('span', `status-${status.toLowerCase()}`, statusLabel(status))
        );
        if (status === 'FAILED' && item && item.errorMessage) {
            button.append(element('span', 'status-failed', text(item.errorMessage)));
        }
        button.addEventListener('click', () => selectResult(index));
        list.append(button);
    });
    dom.resultSidebar.append(title, list);
}

function renderSummary() {
    dom.summaryCards.replaceChildren();
    const item = selectedItem();
    if (!item) {
        return;
    }
    const result = resultOf(item);
    const root = devicesOf(item).root;
    const cards = [
        ['机器人', value(result, RESULT_KEYS.robotName), 'orange'],
        ['当前状态', statusLabel(item.status), 'blue'],
        ['子设备', String(devicesOf(item).children.length), 'blue'],
        ['机器人本体 IP', value(root, 'IP'), 'orange']
    ];
    cards.forEach(([label, content, tone]) => {
        const card = element('article', `panel summary-card ${tone}`);
        card.append(element('span', '', label), element('strong', '', content || '--'));
        dom.summaryCards.append(card);
    });
}

function renderTopology() {
    destroyTopology();
    const item = selectedItem();
    if (!item) {
        return;
    }
    if (item.status === 'FAILED') {
        topologyMessage(item.errorMessage || '该备份目录解析失败，无法生成拓扑。');
        return;
    }
    const devices = devicesOf(item);
    if (!devices.root && !devices.children.length) {
        topologyMessage('当前结果没有可用的网络设备数据。');
        return;
    }
    if (typeof cytoscape === 'undefined') {
        topologyMessage('拓扑组件加载失败，请刷新页面重试。');
        return;
    }

    const root = devices.root || {};
    const elements = [{
        data: {
            id: 'robot-body',
            label: value(root, '名称') || value(resultOf(item), RESULT_KEYS.robotName) || '机器人本体'
        },
        classes: 'robot'
    }];
    devices.children.forEach(({device, key}) => {
        elements.push({data: {id: key, label: value(device, '名称') || key}, classes: 'child'});
        elements.push({data: {id: `edge-${key}`, source: 'robot-body', target: key}});
    });

    topologyGraph = cytoscape({
        container: dom.topology,
        elements,
        style: [
            {selector: 'node', style: {
                'label': 'data(label)', 'shape': 'round-rectangle', 'width': 138, 'height': 58,
                'background-color': '#3b82f6', 'color': '#ffffff', 'font-size': 12,
                'font-weight': 700, 'text-wrap': 'wrap', 'text-max-width': 116,
                'border-width': 3, 'border-color': '#ffffff',
                'shadow-blur': 14, 'shadow-color': '#2563eb', 'shadow-opacity': 0.18
            }},
            {selector: 'node.robot', style: {'background-color': '#f97316', 'shadow-color': '#f97316'}},
            {selector: 'node:selected', style: {'border-width': 4, 'border-color': '#0f172a'}},
            {selector: 'edge', style: {
                'width': 2, 'line-color': '#94a3b8', 'target-arrow-color': '#94a3b8',
                'target-arrow-shape': 'triangle', 'curve-style': 'bezier'
            }}
        ],
        layout: {name: 'dagre', rankDir: 'LR', nodeSep: 36, rankSep: 80, padding: 28}
    });
    topologyGraph.on('tap', 'node', (event) => selectDevice(event.target.id()));
    syncTopologySelection();
}

function renderDeviceTable() {
    dom.deviceTableBody.replaceChildren();
    const item = selectedItem();
    if (!item || item.status === 'FAILED') {
        return;
    }
    allDevices(item).forEach(({device, key, type}) => {
        const row = element('tr', `device-row${state.selectedDeviceKey === key ? ' selected' : ''}`);
        row.tabIndex = 0;
        row.setAttribute('aria-selected', String(state.selectedDeviceKey === key));
        [value(device, '名称') || type, value(device, 'IP'), value(device, '掩码'), value(device, '网关'), value(device, '来源文件')]
            .forEach((content) => row.append(element('td', '', content || '--')));
        row.addEventListener('click', () => selectDevice(key));
        row.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                selectDevice(key);
            }
        });
        dom.deviceTableBody.append(row);
    });
}

function renderWarnings() {
    dom.warningSummary.replaceChildren();
    const item = selectedItem();
    if (!item) {
        return;
    }
    if (item.status === 'FAILED') {
        dom.warningSummary.append(element('div', 'warning-card error', item.errorMessage || '备份目录解析失败'));
        return;
    }
    const warnings = value(resultOf(item), RESULT_KEYS.warnings);
    if (Array.isArray(warnings)) {
        warnings.filter(Boolean).forEach((warning) => {
            dom.warningSummary.append(element('div', 'warning-card', text(warning)));
        });
    }
}

function renderDeviceDetail() {
    dom.deviceDetailPanel.replaceChildren();
    const item = selectedItem();
    const device = deviceByKey(item, state.selectedDeviceKey);
    if (!device || (item && item.status === 'FAILED')) {
        dom.deviceDetailPanel.append(element('p', 'detail-empty', '选择拓扑节点或表格行查看设备全部字段。'));
        return;
    }
    const header = element('div', 'detail-header');
    header.append(
        element('h2', '', value(device, '名称') || '未命名设备'),
        element('span', 'detail-badge', state.selectedDeviceKey === 'robot-body' ? '机器人本体' : '子设备')
    );
    const grid = element('div', 'detail-grid');
    DEVICE_KEYS.forEach((key) => {
        const field = element('div', 'detail-field');
        field.append(element('span', '', key), element('strong', '', displayField(value(device, key))));
        grid.append(field);
    });
    dom.deviceDetailPanel.append(header, grid);
}

function updateActionState() {
    const busy = isLoading();
    const hasFiles = state.files.length > 0;
    dom.startInspectionButton.disabled = !hasFiles || busy;
    dom.exportExcelButton.disabled = !hasFiles || !batchHasExportableData() || busy;
    dom.fileUploadButton.disabled = busy;
    const directoryCount = new Set(state.files.map((entry) => entry.directoryName)).size;
    dom.selectedFileCount.textContent = directoryCount
        ? `已累加 ${directoryCount} 个备份目录`
        : '未选择备份目录';
}

function batchHasExportableData() {
    return batchItems().some((item) =>
        item && (item.status === 'SUCCESS' || item.status === 'PARTIAL') && allDevices(item).length > 0);
}

function selectedItem() {
    return state.selectedItemIndex === null ? null : batchItems()[state.selectedItemIndex] || null;
}

function batchItems() {
    return state.batch && Array.isArray(state.batch.items) ? state.batch.items : [];
}

function resultOf(item) {
    return item && item.result && typeof item.result === 'object' ? item.result : {};
}

function devicesOf(item) {
    const result = resultOf(item);
    const rawChildren = value(result, RESULT_KEYS.subDevices);
    return {
        root: objectOrNull(value(result, RESULT_KEYS.robotSelf)),
        children: (Array.isArray(rawChildren) ? rawChildren : [])
            .map((device, index) => ({device: objectOrNull(device), key: `device-${index}`}))
            .filter((entry) => entry.device)
    };
}

function allDevices(item) {
    const devices = devicesOf(item);
    const list = devices.root ? [{device: devices.root, key: 'robot-body', type: '机器人本体'}] : [];
    return list.concat(devices.children.map((entry) => ({...entry, type: '子设备'})));
}

function defaultDeviceKey(item) {
    if (!item || item.status === 'FAILED') {
        return null;
    }
    const devices = devicesOf(item);
    if (devices.root) {
        return 'robot-body';
    }
    return devices.children.length ? devices.children[0].key : null;
}

function deviceByKey(item, key) {
    if (!item || !key) {
        return null;
    }
    const entry = allDevices(item).find((candidate) => candidate.key === key);
    return entry ? entry.device : null;
}

function syncTopologySelection() {
    if (!topologyGraph) {
        return;
    }
    topologyGraph.nodes().unselect();
    const selected = topologyGraph.getElementById(state.selectedDeviceKey || '');
    if (selected && selected.length) {
        selected.select();
    }
}

function destroyTopology() {
    if (topologyGraph) {
        topologyGraph.destroy();
        topologyGraph = null;
    }
    dom.topology.replaceChildren();
}

function topologyMessage(message) {
    dom.topology.append(element('div', 'topology-message', message));
}

function showMessage(message, isError) {
    dom.emptyState.classList.remove('hidden');
    dom.emptyState.classList.toggle('error', Boolean(isError));
    const heading = dom.emptyState.querySelector('h1');
    const paragraph = dom.emptyState.querySelector('p');
    if (heading) {
        heading.textContent = isError ? '操作未完成' : '正在处理';
    }
    if (paragraph) {
        paragraph.textContent = message;
    }
}

function setLoading(visible) {
    dom.loadingOverlay.classList.toggle('hidden', !visible);
    updateActionState();
}

function isLoading() {
    return dom.loadingOverlay && !dom.loadingOverlay.classList.contains('hidden');
}

async function readError(response) {
    const raw = await response.text();
    if (!raw) {
        return `HTTP ${response.status}`;
    }
    try {
        const payload = JSON.parse(raw);
        return text(payload.message) || raw;
    } catch (error) {
        return raw;
    }
}

function value(object, key) {
    return object && Object.prototype.hasOwnProperty.call(object, key) ? object[key] : null;
}

function displayField(input) {
    const result = text(input).trim();
    return result || '--';
}

function text(input) {
    return input === null || input === undefined ? '' : String(input);
}

function number(input) {
    const parsed = Number(input);
    return Number.isFinite(parsed) ? parsed : 0;
}

function objectOrNull(input) {
    return input && typeof input === 'object' && !Array.isArray(input) ? input : null;
}

function statusLabel(status) {
    return {SUCCESS: '解析成功', PARTIAL: '部分成功', FAILED: '解析失败'}[status] || text(status) || '--';
}

function element(tag, className, content) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (content !== undefined) {
        node.textContent = text(content);
    }
    return node;
}
