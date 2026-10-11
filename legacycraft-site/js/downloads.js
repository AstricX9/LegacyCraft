// Fills in the download links on every page.
//
// Each link has data-pkg="<platform>" (windows, windowsX64, linuxX64, macosArm64, ...), the same names
// `gradlew package<Platform>` uses. The file is LegacyCraft-<version>-<platform>.zip.
//   - If that file is in downloads/ next to this site, the link points there.
//   - Otherwise it points at the GitHub release tagged <version>, so publishing a release with those
//     zips attached makes every link work.
// Change VERSION when you release; when the site is served from inside the repo it reads ../version.txt.
(function(){
  var VERSION = 'a1.0.1';
  var MC_VERSION = '1.8.9';
  var RELEASES = 'https://github.com/AstricX9/LegacyCraft/releases/download/';

  function fileName(pkg){ return 'LegacyCraft-' + VERSION + '-' + pkg + '.zip'; }

  function apply(){
    document.querySelectorAll('.lc-version').forEach(function(el){ el.textContent = VERSION; });
    document.querySelectorAll('.mc-version').forEach(function(el){ el.textContent = MC_VERSION; });
    document.querySelectorAll('a[data-pkg]').forEach(function(a){
      var name = fileName(a.dataset.pkg), local = 'downloads/' + name;
      if (a.hasAttribute('data-name') || /^LegacyCraft-/.test(a.textContent)) a.textContent = name;
      a.href = RELEASES + encodeURIComponent(VERSION) + '/' + name;
      a.setAttribute('data-missing', '');
      if (location.protocol === 'file:') return; // can't check files when opened straight from disk
      fetch(local, {method:'HEAD'}).then(function(r){
        if (!r.ok) return;
        a.href = local;
        a.removeAttribute('data-missing');
      }).catch(function(){});
    });
  }

  function readVersionTxt(text){
    text.split(/\r?\n/).forEach(function(line){
      var m = /^\s*(lc|mc)\s*:\s*(\S+)/i.exec(line);
      if (!m) return;
      if (m[1].toLowerCase() === 'lc') VERSION = m[2]; else MC_VERSION = m[2];
    });
  }

  apply();
  if (location.protocol !== 'file:') {
    fetch('../version.txt').then(function(r){ return r.ok ? r.text() : null; })
      .then(function(t){ if (t && /lc\s*:/i.test(t)) { readVersionTxt(t); apply(); } })
      .catch(function(){});
  }
})();
