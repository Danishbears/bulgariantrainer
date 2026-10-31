import { $, api, element } from './aspect-common.js';
import { mountPractice } from './aspect-practice.js';
import { mountManagement } from './aspect-management.js';
import { mountImport } from './aspect-import.js';
import { mountStatistics } from './aspect-statistics.js';

const practice = mountPractice();
let datasetLoading = false;
async function datasets() {
  if (datasetLoading) return;
  datasetLoading = true;
  try {
    const rows = await api('/api/aspect-pairs/datasets');
    $('aspect-datasets').replaceChildren(...rows.map(row => { const option = element('option'); option.value = row.namespace; return option; }));
  } catch { /* Dataset suggestions are optional; explicit namespace entry remains available. */ }
  finally { datasetLoading = false; }
}
const changed = () => { datasets(); practice.refreshAvailability(); };
const management = mountManagement(changed);
const importer = mountImport(changed);
const statistics = mountStatistics();
function show(focus = true) {
  const requested = location.hash.slice(1);
  const name = ['practice', 'pairs', 'import', 'statistics', 'help'].includes(requested) ? requested : practice.active() ? 'practice' : importer.pending() ? 'import' : management.pending() ? 'pairs' : 'practice';
  document.querySelectorAll('[data-panel]').forEach(node => { node.hidden = node.dataset.panel !== name; });
  document.querySelectorAll('[data-view]').forEach(node => { if (node.dataset.view === name) node.setAttribute('aria-current', 'location'); else node.removeAttribute('aria-current'); });
  if (name === 'pairs') management.show(); else management.hide();
  if (name === 'statistics') statistics.show();
  if (focus) document.querySelector(`[data-panel="${name}"] .view-title`)?.focus();
}
window.addEventListener('hashchange', () => show());
show(false); datasets();
