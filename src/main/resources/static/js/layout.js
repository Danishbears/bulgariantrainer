const pages = [
  ['/', 'Обзор', 'M3 10 12 3l9 7v10H3Z M9 20v-7h6v7'],
  ['/vocabulary.html', 'Словарь', 'M12 5v16 M12 5C8 2 4 3 2 4v15c4-2 7-1 10 2 3-3 6-4 10-2V4c-3-1-6-2-10 1'],
  ['/import.html', 'Импорт слов', 'M12 16V3 M7 8l5-5 5 5 M4 15v6h16v-6'],
  ['/flashcards.html', 'Карточки', 'M6 7h15v14H6Z M3 17V3h14'],
  ['/typing.html', 'Ввод перевода', 'M2 5h20v14H2Z M5 9h1m3 0h1m3 0h1m3 0h1 M6 15h12'],
  ['/sentence-import.html', 'Импорт предложений', 'M12 16V3 M7 8l5-5 5 5 M4 15v6h16v-6'],
  ['/sentences.html', 'Предложения', 'M3 4h18v13H9l-6 4Z M7 8h10 M7 12h7'],
  ['/statistics.html', 'Статистика', 'M4 20V10 M12 20V4 M20 20v-7'],
];
const current = location.pathname === '/index.html' ? '/' : location.pathname;
const skip = document.createElement('a'); skip.className = 'skip-link'; skip.href = '#main-content'; skip.textContent = 'Перейти к содержимому';
const sidebar = document.createElement('header'); sidebar.className = 'site-sidebar';
const brand = document.createElement('a'); brand.href = '/'; brand.className = 'brand';
const mark = document.createElement('span'); mark.className = 'brand-mark'; mark.textContent = 'Б'; mark.setAttribute('aria-hidden', 'true');
const brandText = document.createElement('span'); brandText.textContent = 'Болгарский тренажёр';
const subtitle = document.createElement('small'); subtitle.textContent = 'Слова становятся ближе'; brandText.append(subtitle); brand.append(mark, brandText);
const toggle = document.createElement('button'); toggle.type = 'button'; toggle.className = 'menu-toggle secondary'; toggle.textContent = 'Меню'; toggle.setAttribute('aria-expanded', 'false'); toggle.setAttribute('aria-controls', 'main-navigation');
const label = document.createElement('p'); label.className = 'nav-label'; label.textContent = 'Ваше пространство';
const nav = document.createElement('nav'); nav.id = 'main-navigation'; nav.className = 'site-nav'; nav.setAttribute('aria-label', 'Основная навигация');
for (const [href, title, path] of pages) {
  const link = document.createElement('a'); link.href = href; link.textContent = title;
  const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  for (const [name, value] of Object.entries({ viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.7', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' })) icon.setAttribute(name, value);
  icon.classList.add('nav-icon');
  const drawing = document.createElementNS('http://www.w3.org/2000/svg', 'path'); drawing.setAttribute('d', path); icon.append(drawing); link.prepend(icon);
  if (href === current) link.setAttribute('aria-current', 'page');
  nav.append(link);
}
const footer = document.createElement('div'); footer.className = 'sidebar-footer';
const footerTitle = document.createElement('strong'); footerTitle.textContent = 'В своём темпе'; footer.append(footerTitle, 'Ваш словарь хранится на этом компьютере.');
sidebar.append(brand, toggle, label, nav, footer); document.body.prepend(skip, sidebar);
const main = document.querySelector('main'); main.id = 'main-content'; main.tabIndex = -1;
const top = document.createElement('div'); top.className = 'page-top';
const breadcrumb = document.createElement('span'); breadcrumb.textContent = 'Моё обучение / ' + (pages.find(page => page[0] === current)?.[1] ?? 'Обзор');
const local = document.createElement('span'); local.className = 'local-tag'; local.textContent = 'Личный словарь'; top.append(breadcrumb, local); main.prepend(top);
function closeMenu() { sidebar.classList.remove('menu-open'); toggle.setAttribute('aria-expanded', 'false'); }
toggle.addEventListener('click', () => { const open = sidebar.classList.toggle('menu-open'); toggle.setAttribute('aria-expanded', String(open)); });
sidebar.addEventListener('keydown', event => {
  if (event.key === 'Escape' && sidebar.classList.contains('menu-open')) { closeMenu(); toggle.focus(); }
});
document.addEventListener('click', event => { if (!sidebar.contains(event.target)) closeMenu(); });
skip.addEventListener('click', () => { closeMenu(); main.focus(); });

