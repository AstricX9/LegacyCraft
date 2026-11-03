// Local packages are optional. Unavailable packages keep a working Releases link.
(function () {
  'use strict';
  var VERSION = 'a1.0.2';
  var MC_VERSION = '1.9';

  function apply() {
    document.querySelectorAll('.lc-version').forEach(function (el) { el.textContent = VERSION; });
    document.querySelectorAll('.mc-version').forEach(function (el) { el.textContent = MC_VERSION; });
    if (location.protocol === 'file:') return;
    document.querySelectorAll('a[data-pkg]').forEach(function (link) {
      var local = 'downloads/LegacyCraft-' + VERSION + '-' + link.dataset.pkg + '.zip';
      fetch(local, { method: 'HEAD' }).then(function (response) {
        // Some static hosts return their HTML fallback with a 200 status.
        if (!response.ok || /text\/html/i.test(response.headers.get('content-type') || '')) return;
        link.href = local;
        link.setAttribute('download', '');
        link.title = 'Download LegacyCraft ' + VERSION + ' (' + link.dataset.pkg + ')';
      }).catch(function () { /* Keep the Releases link when hosted packages are absent. */ });
    });
  }

  if (location.protocol === 'file:') { apply(); return; }
  fetch('../version.txt').then(function (response) { return response.ok ? response.text() : ''; })
    .then(function (text) {
      text.split(/\r?\n/).forEach(function (line) {
        var match = /^\s*(lc|mc)\s*:\s*([\w.-]+)\s*$/i.exec(line);
        if (!match) return;
        if (match[1].toLowerCase() === 'lc') VERSION = match[2];
        else MC_VERSION = match[2];
      });
    }).catch(function () { /* The fallback version supports a standalone site. */ })
    .then(apply);
})();
