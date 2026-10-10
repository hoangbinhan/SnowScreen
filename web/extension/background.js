// Chrome does not run content scripts in tabs that were already open when the extension
// is installed, updated or reloaded, so inject them here to keep every tab in sync.
chrome.runtime.onInstalled.addListener(async () => {
  const { js: files, matches: url } = chrome.runtime.getManifest().content_scripts[0];
  const tabs = await chrome.tabs.query({ url });
  await Promise.all(tabs.filter(tab => !tab.discarded).map(tab =>
    // Restricted pages such as the Chrome Web Store reject injection.
    chrome.scripting.executeScript({ target: { tabId: tab.id }, files }).catch(() => {})));
});
