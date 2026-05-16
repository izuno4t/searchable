// Searchable admin UI client-side helpers. Kept minimal; most pages are
// rendered server-side by Thymeleaf.

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      const message = form.getAttribute('data-confirm') || '実行してよろしいですか？';
      if (!window.confirm(message)) {
        event.preventDefault();
      }
    });
  });
});
