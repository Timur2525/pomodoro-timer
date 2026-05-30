const path = require("path");
const { app, BrowserWindow, ipcMain, Notification } = require("electron");

function createWindow() {
  const win = new BrowserWindow({
    width: 920,
    height: 720,
    minWidth: 760,
    minHeight: 600,
    title: "Таймер Pomodoro",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  win.loadFile("index.html");
}

app.whenReady().then(() => {
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

ipcMain.on("notify", (event, data) => {
  if (!Notification.isSupported()) {
    return;
  }

  const notification = new Notification({
    title: data.title || "Pomodoro",
    body: data.body || "Время вышло"
  });

  notification.show();
});
