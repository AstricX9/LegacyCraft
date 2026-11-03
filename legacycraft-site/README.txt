LegacyCraft website (plain HTML/CSS/JS, no framework or build step)

  index.html        homepage: game screenshot, feature list, setup and project links
  download.html     getting started: source setup, platform packages, help
  css/global.css    compact dark layout, Calibri, navigation and footer
  css/elements.css  plain notes, code and package table
  css/home.css      homepage only
  css/download.css  download page only
  js/downloads.js   optional local packages and project version
  assets/           images, see assets/README.txt
  downloads/        put the package zips here, see downloads/README.txt

Open index.html in a browser, or serve the folder from any static host.

All showcase images live inside assets/showcase, so the site can be hosted on
its own. Screenshots are ordinary image links; setup instructions and navigation
work without JavaScript. The homepage has no carousel, animation or dialog.

The current source workflow is the main getting-started path. Package links
lead to GitHub Releases unless a matching ZIP is present in downloads/. No
release asset URL is guessed. Keep VERSION in js/downloads.js in sync with
version.txt when publishing this folder by itself; serving from the repository
also lets the script read ../version.txt.
