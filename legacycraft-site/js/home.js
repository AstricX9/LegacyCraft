// The gallery stays usable as image links when JavaScript is unavailable.
(function () {
  'use strict';
  var image = document.getElementById('gallery-image');
  var caption = document.getElementById('gallery-caption');
  var announcement = document.querySelector('.gallery-announcement');
  var choices = document.querySelectorAll('.gallery-choices a');
  var expand = document.getElementById('expand-image');
  var dialog = document.getElementById('screenshot-dialog');
  var dialogImage = document.getElementById('dialog-image');

  choices.forEach(function (choice) {
    choice.addEventListener('click', function (event) {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      image.src = choice.href;
      image.alt = choice.dataset.alt;
      caption.textContent = choice.dataset.caption;
      choices.forEach(function (item) { item.removeAttribute('aria-current'); });
      choice.setAttribute('aria-current', 'true');
      announcement.textContent = choice.dataset.caption;
    });
  });

  if (typeof dialog.showModal === 'function') {
    expand.hidden = false;
    expand.addEventListener('click', function () {
      dialogImage.src = image.src;
      dialogImage.alt = image.alt;
      dialog.showModal();
    });
    dialog.querySelector('.dialog-close').addEventListener('click', function () { dialog.close(); });
    dialog.addEventListener('click', function (event) {
      if (event.target !== dialog) return;
      var bounds = dialog.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
  }
})();
