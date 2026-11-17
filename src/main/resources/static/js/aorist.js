import { $, api, element, status } from './aorist-common.js';
import { mountManagement } from './aorist-management.js';
import { mountImport } from './aorist-import.js';
import { mountPractice } from './aorist-practice.js';

const practice = mountPractice();
let catalogBusy = false;
async function catalog() {
  if (catalogBusy) return; catalogBusy = true; $('aorist-catalog-retry').disabled = true;
  try {
    const [datasets, filters] = await Promise.all([api('/api/aorist-verbs/datasets'), api('/api/aorist-verbs/filters')]);
    $('aorist-datasets').replaceChildren(...datasets.map(d => { const o = element('option'); o.value = d.datasetId; return o; }));
    $('aorist-groups').replaceChildren(...filters.studyGroups.map(group => { const o = element('option'); o.value = group; return o; }));
    status('aorist-catalog-message', ''); $('aorist-catalog-retry').hidden = true;
  } catch (error) { status('aorist-catalog-message', `${error.message} Коды наборов и групп можно ввести вручную.`, true); $('aorist-catalog-retry').hidden = false; }
  finally { catalogBusy = false; $('aorist-catalog-retry').disabled = false; }
}
const changed = () => { catalog(); practice.refreshAvailability(); };
const management = mountManagement(changed), importer = mountImport(changed);
function show(focus = true) {
  const requested = location.hash.slice(1);
  const view = ['practice', 'verbs', 'import', 'help'].includes(requested) ? requested : practice.active() ? 'practice' : importer.pending() ? 'import' : management.pending() ? 'verbs' : 'practice';
  document.querySelectorAll('[data-panel]').forEach(node => { node.hidden = node.dataset.panel !== view; });
  document.querySelectorAll('[data-view]').forEach(node => { if (node.dataset.view === view) node.setAttribute('aria-current', 'location'); else node.removeAttribute('aria-current'); });
  if (view === 'verbs') management.show(); else management.hide();
  if (view === 'practice') practice.show(); else practice.hide();
  if (focus) document.querySelector(`[data-panel="${view}"] .view-title`)?.focus();
}
$('aorist-catalog-retry').addEventListener('click', catalog);
window.addEventListener('hashchange', () => { if (location.hash !== '#main-content') show(); });
show(false); catalog();
