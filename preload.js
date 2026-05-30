const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("pomodoroApp", {
  notify(title, body) {
    ipcRenderer.send("notify", { title, body });
  }
});
