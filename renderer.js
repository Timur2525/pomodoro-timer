const timeText = document.getElementById("timeText");
const modeBadge = document.getElementById("modeBadge");
const roundText = document.getElementById("roundText");
const progressBar = document.getElementById("progressBar");
const messageText = document.getElementById("messageText");

const startPauseBtn = document.getElementById("startPauseBtn");
const resetBtn = document.getElementById("resetBtn");
const clearStatsBtn = document.getElementById("clearStatsBtn");
const musicBtn = document.getElementById("musicBtn");
const volumeInput = document.getElementById("volumeInput");
const musicAudio = document.getElementById("musicAudio");

const workInput = document.getElementById("workInput");
const breakInput = document.getElementById("breakInput");
const longBreakInput = document.getElementById("longBreakInput");
const roundsInput = document.getElementById("roundsInput");

const doneText = document.getElementById("doneText");
const minutesText = document.getElementById("minutesText");
const breaksText = document.getElementById("breaksText");

let mode = "work";
let isRunning = false;
let timerId = null;
let round = 1;
let totalSeconds = readMinutes(workInput) * 60;
let secondsLeft = totalSeconds;

let audioContext = null;
let gainNode = null;
let ambientNodes = [];
let ambientTimer = null;
let musicOn = false;

let stats = loadStats();

function readMinutes(input) {
  const value = Number(input.value);
  const min = Number(input.min);
  const max = Number(input.max);
  return Math.min(max, Math.max(min, value || min));
}

function getCurrentDuration() {
  if (mode === "work") {
    return readMinutes(workInput) * 60;
  }

  if (round % readMinutes(roundsInput) === 0) {
    return readMinutes(longBreakInput) * 60;
  }

  return readMinutes(breakInput) * 60;
}

function formatTime(value) {
  const minutes = Math.floor(value / 60).toString().padStart(2, "0");
  const seconds = (value % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function updateView() {
  timeText.textContent = formatTime(secondsLeft);
  roundText.textContent = `Интервал ${round}`;
  progressBar.value = 100 - Math.round((secondsLeft / totalSeconds) * 100);

  if (mode === "work") {
    modeBadge.textContent = "Работа";
    modeBadge.classList.remove("break");
  } else {
    modeBadge.textContent = "Перерыв";
    modeBadge.classList.add("break");
  }

  startPauseBtn.textContent = isRunning ? "Пауза" : "Старт";
  doneText.textContent = stats.done;
  minutesText.textContent = stats.minutes;
  breaksText.textContent = stats.breaks;

  document.title = `${formatTime(secondsLeft)} - Pomodoro`;
}

function startTimer() {
  if (isRunning) {
    return;
  }

  isRunning = true;
  messageText.textContent = mode === "work" ? "Работаем. Телефон лучше убрать." : "Перерыв, можно выдохнуть.";

  timerId = setInterval(() => {
    secondsLeft -= 1;

    if (secondsLeft <= 0) {
      finishInterval();
    }

    updateView();
  }, 1000);

  updateView();
}

function pauseTimer() {
  isRunning = false;
  clearInterval(timerId);
  timerId = null;
  messageText.textContent = "Пауза. Таймер не сброшен.";
  updateView();
}

function resetTimer() {
  pauseTimer();
  mode = "work";
  round = 1;
  totalSeconds = getCurrentDuration();
  secondsLeft = totalSeconds;
  progressBar.value = 0;
  messageText.textContent = "Таймер сброшен.";
  updateView();
}

function finishInterval(countStats = true) {
  clearInterval(timerId);
  timerId = null;
  isRunning = false;

  if (countStats) {
    playSignal();
  }

  if (mode === "work") {
    if (countStats) {
      stats.done += 1;
      stats.minutes += readMinutes(workInput);
      saveStats();
      sendNotification("Работа закончена", "Пора сделать перерыв.");
    }

    mode = "break";
    messageText.textContent = countStats ? "Рабочий интервал завершен." : "Перешли к перерыву без записи в статистику.";
  } else {
    if (countStats) {
      stats.breaks += 1;
      saveStats();
      sendNotification("Перерыв закончен", "Можно начинать следующий рабочий интервал.");
    }

    mode = "work";
    round += 1;
    messageText.textContent = countStats ? "Перерыв завершен." : "Перешли к работе без записи в статистику.";
  }

  totalSeconds = getCurrentDuration();
  secondsLeft = totalSeconds;

  if (countStats) {
    startTimer();
  }
}

function skipInterval() {
  finishInterval(false);
  updateView();
}

function sendNotification(title, body) {
  if (window.pomodoroApp) {
    window.pomodoroApp.notify(title, body);
    return;
  }

  if (!("Notification" in window)) {
    alert(`${title}\n${body}`);
    return;
  }

  if (Notification.permission === "granted") {
    new Notification(title, { body });
  } else if (Notification.permission !== "denied") {
    Notification.requestPermission().then((permission) => {
      if (permission === "granted") {
        new Notification(title, { body });
      }
    });
  }
}

function loadStats() {
  const emptyStats = { done: 0, minutes: 0, breaks: 0 };
  const saved = localStorage.getItem("pomodoroStats");

  if (!saved) {
    return emptyStats;
  }

  try {
    return { ...emptyStats, ...JSON.parse(saved) };
  } catch (error) {
    return emptyStats;
  }
}

function saveStats() {
  localStorage.setItem("pomodoroStats", JSON.stringify(stats));
}

function clearStats() {
  stats = { done: 0, minutes: 0, breaks: 0 };
  saveStats();
  updateView();
}

function createAudio() {
  if (!audioContext) {
    audioContext = new AudioContext();
    gainNode = audioContext.createGain();
    gainNode.gain.value = Number(volumeInput.value) / 100;
    gainNode.connect(audioContext.destination);
  }
}

function makeNoiseSource(seconds) {
  const sampleRate = audioContext.sampleRate;
  const buffer = audioContext.createBuffer(1, sampleRate * seconds, sampleRate);
  const data = buffer.getChannelData(0);

  for (let i = 0; i < data.length; i += 1) {
    data[i] = Math.random() * 2 - 1;
  }

  const source = audioContext.createBufferSource();
  source.buffer = buffer;
  source.loop = true;
  return source;
}

function addNoiseLayer(filterType, frequency, q, level) {
  const source = makeNoiseSource(2);
  const filter = audioContext.createBiquadFilter();
  const layerGain = audioContext.createGain();

  filter.type = filterType;
  filter.frequency.value = frequency;
  filter.Q.value = q;
  layerGain.gain.value = level;

  source.connect(filter);
  filter.connect(layerGain);
  layerGain.connect(gainNode);
  source.start();
  ambientNodes.push(source, filter, layerGain);
}

function playTone(frequency, duration, level = 0.18, type = "sine") {
  if (!audioContext || !gainNode) {
    return;
  }

  const oscillator = audioContext.createOscillator();
  const noteGain = audioContext.createGain();

  oscillator.type = type;
  oscillator.frequency.value = frequency;
  noteGain.gain.setValueAtTime(0.0001, audioContext.currentTime);
  noteGain.gain.exponentialRampToValueAtTime(level, audioContext.currentTime + 0.04);
  noteGain.gain.exponentialRampToValueAtTime(0.0001, audioContext.currentTime + duration);

  oscillator.connect(noteGain);
  noteGain.connect(gainNode);
  oscillator.start();
  oscillator.stop(audioContext.currentTime + duration + 0.05);
}

function stopAmbient() {
  clearInterval(ambientTimer);
  ambientTimer = null;

  ambientNodes.forEach((node) => {
    try {
      if (node.stop) {
        node.stop();
      }

      node.disconnect();
    } catch (error) {
      // Узлы могли уже остановиться сами.
    }
  });

  ambientNodes = [];
}

function startRain() {
  addNoiseLayer("bandpass", 1800, 0.8, 0.24);
  addNoiseLayer("lowpass", 420, 0.5, 0.08);
}

function playBird() {
  const now = audioContext.currentTime;

  for (let i = 0; i < 3; i += 1) {
    const oscillator = audioContext.createOscillator();
    const birdGain = audioContext.createGain();
    const start = now + i * 0.16;
    const frequency = 1100 + Math.random() * 800;

    oscillator.type = "sine";
    oscillator.frequency.setValueAtTime(frequency, start);
    oscillator.frequency.linearRampToValueAtTime(frequency + 240, start + 0.12);
    birdGain.gain.setValueAtTime(0.0001, start);
    birdGain.gain.exponentialRampToValueAtTime(0.16, start + 0.03);
    birdGain.gain.exponentialRampToValueAtTime(0.0001, start + 0.18);
    oscillator.connect(birdGain);
    birdGain.connect(gainNode);
    oscillator.start(start);
    oscillator.stop(start + 0.2);
  }
}

function startForest() {
  addNoiseLayer("lowpass", 750, 0.7, 0.13);
  addNoiseLayer("bandpass", 2200, 0.9, 0.035);
  playBird();
  ambientTimer = setInterval(playBird, 4200);
}

function playCricket() {
  const now = audioContext.currentTime;

  for (let i = 0; i < 4; i += 1) {
    const oscillator = audioContext.createOscillator();
    const cricketGain = audioContext.createGain();
    const start = now + i * 0.11;

    oscillator.type = "square";
    oscillator.frequency.setValueAtTime(3400 + Math.random() * 320, start);
    cricketGain.gain.setValueAtTime(0.0001, start);
    cricketGain.gain.exponentialRampToValueAtTime(0.08, start + 0.015);
    cricketGain.gain.exponentialRampToValueAtTime(0.0001, start + 0.075);
    oscillator.connect(cricketGain);
    cricketGain.connect(gainNode);
    oscillator.start(start);
    oscillator.stop(start + 0.08);
  }
}

function startNight() {
  addNoiseLayer("lowpass", 320, 0.6, 0.08);
  addNoiseLayer("bandpass", 3600, 1.2, 0.018);
  playCricket();
  ambientTimer = setInterval(playCricket, 1450);
}

function startAmbient() {
  stopAmbient();
  createAudio();
  audioContext.resume();

  if (selectedSound === "forest") {
    startForest();
  } else if (selectedSound === "night") {
    startNight();
  } else {
    startRain();
  }
}

function toggleMusic() {
  if (musicOn) {
    musicOn = false;
    musicAudio.pause();
    musicBtn.textContent = "Включить";
    return;
  }

  musicOn = true;
  musicBtn.textContent = "Выключить";
  musicAudio.volume = Number(volumeInput.value) / 100;
  musicAudio.play().catch(() => {
    musicOn = false;
    musicBtn.textContent = "Включить";
  });
}

function playSignal() {
  createAudio();
  audioContext.resume();
  playTone(660, 0.2);

  setTimeout(() => {
    playTone(880, 0.2);
  }, 230);
}

startPauseBtn.addEventListener("click", () => {
  if (isRunning) {
    pauseTimer();
  } else {
    startTimer();
  }
});

resetBtn.addEventListener("click", resetTimer);
clearStatsBtn.addEventListener("click", clearStats);
musicBtn.addEventListener("click", toggleMusic);

volumeInput.addEventListener("input", () => {
  musicAudio.volume = Number(volumeInput.value) / 100;

  if (gainNode) {
    gainNode.gain.value = Number(volumeInput.value) / 100;
  }
});

[workInput, breakInput, longBreakInput, roundsInput].forEach((input) => {
  input.addEventListener("change", () => {
    input.value = readMinutes(input);

    if (!isRunning) {
      totalSeconds = getCurrentDuration();
      secondsLeft = totalSeconds;
      updateView();
    }
  });
});

updateView();
