// 망고월드 - 메인 게임 로직

const SAVE_KEY = "mangoWorldSave_v1";

function todayStr() {
  return new Date().toISOString().slice(0, 10);
}

function uid() {
  return "id" + Math.random().toString(36).slice(2, 10);
}

function defaultState() {
  const owned = {};
  ITEMS.forEach((it) => {
    if (it.starter) owned[it.id] = true;
  });
  return {
    coins: 3000,
    savings: 0,
    characters: [],
    activeCharacterId: null,
    owned,
    placed: {},
    taekwondoBeltIndex: 0,
    daily: { date: todayStr(), taekwondoCount: 0, pianoCount: 0, allowanceClaimed: false },
  };
}

function loadState() {
  try {
    const raw = localStorage.getItem(SAVE_KEY);
    if (raw) {
      const saved = JSON.parse(raw);
      // 예전 저장 형식(고정 캐릭터 1명)을 캐릭터 목록으로 변환
      if (saved.character && !saved.characters) {
        const legacy = { id: uid(), name: "나", gender: "girl", hair: saved.character.hair, outfit: saved.character.outfit };
        saved.characters = [legacy];
        saved.activeCharacterId = legacy.id;
        delete saved.character;
      }
      return Object.assign(defaultState(), saved);
    }
  } catch (e) {
    /* 저장 데이터를 읽지 못하면 새로 시작 */
  }
  return defaultState();
}

function save() {
  localStorage.setItem(SAVE_KEY, JSON.stringify(state));
}

let state = loadState();
let currentLocation = null;
let selectedItemId = null;
let pianoNotesThisSession = 0;
let toastTimer = null;
let charFormOpen = false;
let editingCharacterId = null;
let charFormDraft = { name: "", gender: "girl", hair: CHARACTER_OPTIONS.hair[0], outfit: CHARACTER_OPTIONS.outfit[0] };

function checkNewDay() {
  const t = todayStr();
  if (!state.daily || state.daily.date !== t) {
    state.daily = { date: t, taekwondoCount: 0, pianoCount: 0, allowanceClaimed: false };
    save();
  }
}
checkNewDay();

// ---- DOM refs ----
const btnHome = document.getElementById("btn-home");
const topbarAvatar = document.getElementById("topbar-avatar");
const coinCountEl = document.getElementById("coin-count");
const screenMap = document.getElementById("screen-map");
const screenLocation = document.getElementById("screen-location");
const townGrid = document.getElementById("town-grid");
const mapLead = document.getElementById("map-lead");
const locTitle = document.getElementById("loc-title");
const locDesc = document.getElementById("loc-desc");
const locActivity = document.getElementById("loc-activity");
const roomCanvas = document.getElementById("room-canvas");
const tabInventory = document.getElementById("tab-inventory");
const tabShop = document.getElementById("tab-shop");
const tabButtons = document.querySelectorAll(".tab-btn");
const toastEl = document.getElementById("toast");
const coinDisplayEl = document.getElementById("coin-display");
const fxLayer = document.getElementById("fx-layer");

// ---- 공용 UI 헬퍼 ----
function toast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove("show"), 2200);
}

function bounce(selector) {
  const el = typeof selector === "string" ? document.querySelector(selector) : selector;
  if (!el) return;
  el.classList.remove("bounce");
  void el.offsetWidth;
  el.classList.add("bounce");
}

function playScreenEnter(el) {
  if (!el) return;
  el.classList.remove("enter");
  void el.offsetWidth;
  el.classList.add("enter");
}

function spawnFx(el) {
  fxLayer.appendChild(el);
  el.addEventListener("animationend", () => el.remove());
}

function pulseCoinDisplay() {
  coinDisplayEl.classList.remove("pulse");
  void coinDisplayEl.offsetWidth;
  coinDisplayEl.classList.add("pulse");
}

function floatCoinPopup(amount) {
  const rect = coinDisplayEl.getBoundingClientRect();
  const el = document.createElement("div");
  el.className = "coin-popup " + (amount >= 0 ? "gain" : "spend");
  el.textContent = (amount >= 0 ? "+" : "") + amount + " 🥭";
  el.style.left = rect.left + rect.width / 2 + "px";
  el.style.top = rect.bottom + "px";
  spawnFx(el);
  pulseCoinDisplay();
}

function spawnSparkles(anchorEl) {
  if (!anchorEl) return;
  const rect = anchorEl.getBoundingClientRect();
  const emojis = ["✨", "🌟", "💫"];
  for (let i = 0; i < 6; i++) {
    const el = document.createElement("div");
    el.className = "sparkle-burst";
    el.textContent = emojis[i % emojis.length];
    el.style.left = rect.left + rect.width / 2 + (Math.random() * 44 - 22) + "px";
    el.style.top = rect.top + rect.height / 2 + "px";
    el.style.animationDelay = i * 0.05 + "s";
    spawnFx(el);
  }
}

function kickAnimate(containerSelector) {
  const container = document.querySelector(containerSelector);
  if (!container) return;
  const foot = container.querySelector(".avatar-foot-r");
  if (!foot) return;
  foot.classList.remove("kick-anim");
  void foot.offsetWidth;
  foot.classList.add("kick-anim");
}

function triggerBurst(selector) {
  const el = typeof selector === "string" ? document.querySelector(selector) : selector;
  if (!el) return;
  el.classList.remove("show");
  void el.offsetWidth;
  el.classList.add("show");
}

function updateCoinDisplay() {
  coinCountEl.textContent = state.coins;
}

function getActiveCharacter() {
  return state.characters.find((c) => c.id === state.activeCharacterId) || null;
}

function updateTopbarAvatar() {
  mountAvatar(topbarAvatar, getActiveCharacter(), 40);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
}

// ---- 마을 지도 ----
function showMap() {
  currentLocation = null;
  screenMap.classList.remove("hidden");
  screenLocation.classList.add("hidden");
  renderTownGrid();
  renderMapIntro();
  playScreenEnter(screenMap);
}

function renderMapIntro() {
  const active = getActiveCharacter();
  mapLead.textContent = active
    ? `${active.name}과 함께 군포 곳곳을 자유롭게 탐험해보세요!`
    : "나만의 캐릭터를 만들고 군포 곳곳을 자유롭게 탐험해보세요! (우리집에서 캐릭터 만들기)";
}

function renderTownGrid() {
  townGrid.innerHTML = LOCATIONS.map(
    (loc) => `
    <button class="town-card theme-${loc.theme}" onclick="enterLocation('${loc.id}')">
      <span class="town-emoji">${loc.emoji}</span>
      <span class="town-name">${loc.name}</span>
    </button>`
  ).join("");
}

// ---- 장소 화면 ----
function ensureRoomInit(locationId) {
  if (!state.placed[locationId]) {
    state.placed[locationId] = [];
    const starters = ITEMS.filter((i) => i.room === locationId && i.starter);
    starters.forEach((it, idx) => {
      state.placed[locationId].push({ uid: uid(), itemId: it.id, x: 20 + idx * 22, y: 55 + (idx % 2) * 15 });
    });
    save();
  }
}

function enterLocation(id) {
  currentLocation = LOCATIONS.find((l) => l.id === id);
  ensureRoomInit(id);
  selectedItemId = null;
  pianoNotesThisSession = 0;

  screenMap.classList.add("hidden");
  screenLocation.classList.remove("hidden");
  screenLocation.className = "screen theme-" + currentLocation.theme;

  locTitle.textContent = currentLocation.emoji + " " + currentLocation.name;
  locDesc.textContent = currentLocation.desc;

  tabButtons.forEach((b) => b.classList.remove("active"));
  tabButtons[0].classList.add("active");
  tabInventory.classList.remove("hidden");
  tabShop.classList.add("hidden");

  renderActivityPanel();
  renderRoom();
  renderInventoryTab();
  renderShopTab();
  playScreenEnter(screenLocation);
}

btnHome.addEventListener("click", showMap);

tabButtons.forEach((btn) => {
  btn.addEventListener("click", () => {
    tabButtons.forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const tab = btn.dataset.tab;
    tabInventory.classList.toggle("hidden", tab !== "inventory");
    tabShop.classList.toggle("hidden", tab !== "shop");
  });
});

// ---- 방 꾸미기 (배치) ----
function renderRoom() {
  const placedList = state.placed[currentLocation.id] || [];
  const itemsHtml = placedList
    .map((p) => {
      const item = ITEMS.find((i) => i.id === p.itemId);
      if (!item) return "";
      return `<div class="placed-item" style="left:${p.x}%; top:${p.y}%;" onclick="event.stopPropagation(); requestRemovePlaced('${currentLocation.id}','${p.uid}', event.currentTarget)" title="탭해서 치우기">
        <span class="placed-emoji">${item.emoji}</span>
      </div>`;
    })
    .join("");
  const hint = selectedItemId
    ? `<div class="room-hint active">✋ 놓을 위치를 탭하세요</div>`
    : `<div class="room-hint">보관함에서 아이템을 골라 배치해보세요</div>`;
  roomCanvas.innerHTML = itemsHtml + hint;
}

roomCanvas.addEventListener("click", (e) => {
  if (!selectedItemId) return;
  const rect = roomCanvas.getBoundingClientRect();
  const x = ((e.clientX - rect.left) / rect.width) * 100;
  const y = ((e.clientY - rect.top) / rect.height) * 100;
  const clampedX = Math.min(92, Math.max(4, x));
  const clampedY = Math.min(88, Math.max(8, y));
  state.placed[currentLocation.id].push({ uid: uid(), itemId: selectedItemId, x: clampedX, y: clampedY });
  selectedItemId = null;
  save();
  renderRoom();
  renderInventoryTab();
});

function requestRemovePlaced(locId, itemUid, el) {
  if (!el || el.classList.contains("removing")) return;
  el.classList.add("removing");
  setTimeout(() => removePlaced(locId, itemUid), 200);
}

function removePlaced(locId, itemUid) {
  const list = state.placed[locId] || [];
  const idx = list.findIndex((p) => p.uid === itemUid);
  if (idx === -1) return;
  const item = ITEMS.find((i) => i.id === list[idx].itemId);
  list.splice(idx, 1);
  save();
  renderRoom();
  if (item) toast(`${item.name}을(를) 치웠어요`);
}

function selectInvItem(id) {
  selectedItemId = selectedItemId === id ? null : id;
  renderInventoryTab();
  renderRoom();
}

function renderInventoryTab() {
  const items = ITEMS.filter((i) => i.room === currentLocation.id && state.owned[i.id]);
  tabInventory.innerHTML = items.length
    ? items
        .map(
          (it) => `
      <button class="item-chip ${selectedItemId === it.id ? "selected" : ""}" onclick="selectInvItem('${it.id}')">
        <span class="chip-emoji">${it.emoji}</span><span class="chip-name">${it.name}</span>
      </button>`
        )
        .join("")
    : `<p class="empty-hint">아직 보관함이 비었어요. 상점에서 아이템을 구매해보세요!</p>`;
}

function renderShopTab() {
  const items = ITEMS.filter((i) => !i.starter && ((i.room === currentLocation.id && !i.shopAt) || i.shopAt === currentLocation.id));
  tabShop.innerHTML = items.length
    ? items
        .map((it) => {
          const owned = !!state.owned[it.id];
          return `<div class="item-chip shop ${owned ? "owned" : ""}">
          <span class="chip-emoji">${it.emoji}</span>
          <span class="chip-name">${it.name}</span>
          <span class="chip-price">${owned ? "보유중 ✓" : "🥭 " + it.price}</span>
          ${owned ? "" : `<button class="buy-btn" onclick="buyDecor('${it.id}')">구매</button>`}
        </div>`;
        })
        .join("")
    : `<p class="empty-hint">이 장소에는 특별한 상점 아이템이 없어요.</p>`;
}

function buyDecor(id) {
  const item = ITEMS.find((i) => i.id === id);
  if (!item || state.owned[id]) return;
  if (state.coins < item.price) {
    toast("망고코인이 부족해요! 은행에서 용돈을 받아보세요 🏦");
    return;
  }
  state.coins -= item.price;
  state.owned[id] = true;
  save();
  updateCoinDisplay();
  floatCoinPopup(-item.price);
  renderShopTab();
  renderInventoryTab();
  toast(`${item.name}을(를) 구매했어요! 보관함에서 꺼내 꾸며보세요 ✨`);
}

function buyMenu(id) {
  const item = MENUS[currentLocation.id].find((i) => i.id === id);
  if (!item) return;
  if (state.coins < item.price) {
    toast("망고코인이 부족해요!");
    return;
  }
  state.coins -= item.price;
  save();
  updateCoinDisplay();
  floatCoinPopup(-item.price);
  toast(`${item.emoji} ${item.name} 냠냠! 맛있게 먹었어요 😋`);
}

// ---- 장소별 특별 활동 패널 ----
function renderActivityPanel() {
  const loc = currentLocation;
  checkNewDay();

  if (loc.hasWardrobe) {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = wardrobeHTML();
  } else if (loc.menu) {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = menuHTML(loc.id);
  } else if (loc.activity === "taekwondo") {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = taekwondoHTML();
  } else if (loc.activity === "piano") {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = pianoHTML();
  } else if (loc.activity === "playground") {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = playgroundHTML();
  } else if (loc.activity === "bank") {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = bankHTML();
  } else {
    locActivity.classList.add("hidden");
    locActivity.innerHTML = "";
  }
}

function wardrobeHTML() {
  const chips = state.characters.map((c) => characterChipHTML(c)).join("");
  return `<div class="wardrobe">
    <p class="wardrobe-title">우리 가족 캐릭터</p>
    <div class="char-list">
      ${chips || '<p class="empty-hint">아직 만든 캐릭터가 없어요. 아래에서 첫 캐릭터를 만들어보세요!</p>'}
    </div>
    <button class="action-btn" onclick="${charFormOpen ? "closeCharForm()" : "openCharCreateForm()"}">${charFormOpen ? "취소" : "➕ 새 캐릭터 만들기"}</button>
    ${charFormOpen ? characterFormHTML() : ""}
  </div>`;
}

function characterChipHTML(c) {
  const isActive = c.id === state.activeCharacterId;
  return `<div class="char-chip ${isActive ? "active" : ""}">
    <div class="char-chip-avatar" onclick="selectCharacter('${c.id}')">${renderAvatarSVG(c, 56)}</div>
    <div class="char-chip-name" onclick="selectCharacter('${c.id}')">${c.gender === "boy" ? "👦" : "👧"} ${escapeHtml(c.name)}</div>
    <div class="char-chip-actions">
      <button class="chip-icon-btn" onclick="startEditCharacter('${c.id}')" title="수정">✏️</button>
      <button class="chip-icon-btn" onclick="deleteCharacterConfirm('${c.id}')" title="삭제">🗑️</button>
    </div>
  </div>`;
}

function characterFormHTML() {
  return `<div class="char-form">
    <div class="avatar-preview">${renderAvatarSVG(charFormDraft, 110)}</div>
    <input id="char-name-input" class="char-name-input" type="text" maxlength="8" placeholder="이름을 입력해주세요" value="${escapeHtml(charFormDraft.name)}" />
    <div class="swatch-group">
      <p>성별</p>
      <div class="gender-toggle">
        <button class="gender-btn ${charFormDraft.gender === "girl" ? "active" : ""}" onclick="setDraftGender('girl')">👧 여자아이</button>
        <button class="gender-btn ${charFormDraft.gender === "boy" ? "active" : ""}" onclick="setDraftGender('boy')">👦 남자아이</button>
      </div>
    </div>
    <div class="swatch-group">
      <p>머리 색</p>
      <div class="swatches">${CHARACTER_OPTIONS.hair
        .map((c) => `<button class="swatch ${charFormDraft.hair === c ? "active" : ""}" style="background:${c}" onclick="setDraftHair('${c}')"></button>`)
        .join("")}</div>
    </div>
    <div class="swatch-group">
      <p>옷 색</p>
      <div class="swatches">${CHARACTER_OPTIONS.outfit
        .map((c) => `<button class="swatch ${charFormDraft.outfit === c ? "active" : ""}" style="background:${c}" onclick="setDraftOutfit('${c}')"></button>`)
        .join("")}</div>
    </div>
    <div class="action-row">
      <button class="action-btn" onclick="saveCharacterForm()">${editingCharacterId ? "수정 완료 ✅" : "만들기 ✨"}</button>
      <button class="action-btn small" onclick="closeCharForm()">취소</button>
    </div>
  </div>`;
}

function openCharCreateForm() {
  charFormOpen = true;
  editingCharacterId = null;
  charFormDraft = { name: "", gender: "girl", hair: CHARACTER_OPTIONS.hair[0], outfit: CHARACTER_OPTIONS.outfit[0] };
  renderActivityPanel();
}

function startEditCharacter(id) {
  const c = state.characters.find((x) => x.id === id);
  if (!c) return;
  charFormOpen = true;
  editingCharacterId = id;
  charFormDraft = { name: c.name, gender: c.gender, hair: c.hair, outfit: c.outfit };
  renderActivityPanel();
}

function closeCharForm() {
  charFormOpen = false;
  editingCharacterId = null;
  renderActivityPanel();
}

function syncDraftName() {
  const el = document.getElementById("char-name-input");
  if (el) charFormDraft.name = el.value;
}

function setDraftGender(g) {
  syncDraftName();
  charFormDraft.gender = g;
  renderActivityPanel();
}
function setDraftHair(c) {
  syncDraftName();
  charFormDraft.hair = c;
  renderActivityPanel();
}
function setDraftOutfit(c) {
  syncDraftName();
  charFormDraft.outfit = c;
  renderActivityPanel();
}

function saveCharacterForm() {
  syncDraftName();
  const name = charFormDraft.name.trim();
  if (!name) {
    toast("이름을 입력해주세요");
    return;
  }
  if (editingCharacterId) {
    const c = state.characters.find((x) => x.id === editingCharacterId);
    if (c) {
      c.name = name;
      c.gender = charFormDraft.gender;
      c.hair = charFormDraft.hair;
      c.outfit = charFormDraft.outfit;
    }
    toast(`${name} 정보를 수정했어요!`);
  } else {
    const c = { id: uid(), name, gender: charFormDraft.gender, hair: charFormDraft.hair, outfit: charFormDraft.outfit };
    state.characters.push(c);
    state.activeCharacterId = c.id;
    toast(`${name}을(를) 만들었어요! 🎉`);
  }
  const isNew = !editingCharacterId;
  charFormOpen = false;
  editingCharacterId = null;
  save();
  updateTopbarAvatar();
  renderActivityPanel();
  if (isNew) {
    spawnSparkles(topbarAvatar);
    const chipEl = document.querySelector(".char-chip.active");
    if (chipEl) chipEl.classList.add("char-new-anim");
  }
}

function selectCharacter(id) {
  state.activeCharacterId = id;
  save();
  updateTopbarAvatar();
  renderActivityPanel();
}

function deleteCharacterConfirm(id) {
  const c = state.characters.find((x) => x.id === id);
  if (!c) return;
  state.characters = state.characters.filter((x) => x.id !== id);
  if (state.activeCharacterId === id) {
    state.activeCharacterId = state.characters.length ? state.characters[0].id : null;
  }
  if (editingCharacterId === id) {
    charFormOpen = false;
    editingCharacterId = null;
  }
  save();
  updateTopbarAvatar();
  renderActivityPanel();
  toast(`${c.name}을(를) 삭제했어요`);
}

function menuHTML(locId) {
  const items = MENUS[locId];
  return `<div class="menu-list">${items
    .map(
      (it) => `
    <button class="menu-item" onclick="buyMenu('${it.id}')">
      <span class="menu-emoji">${it.emoji}</span>
      <span class="menu-name">${it.name}</span>
      <span class="menu-price">🥭 ${it.price}</span>
    </button>`
    )
    .join("")}</div>`;
}

function taekwondoHTML() {
  const active = getActiveCharacter();
  if (!active) return characterGateHTML();
  const belt = TAEKWONDO_BELTS[state.taekwondoBeltIndex];
  return `<div class="activity-box">
    <p class="belt-info">현재 띠: <b>${belt}</b> &nbsp;(오늘 연습 ${state.daily.taekwondoCount}/5)</p>
    <div class="avatar-preview mid" id="tkd-avatar">
      ${renderAvatarSVG(active, 100)}
      <div class="impact-burst" id="tkd-burst"><span></span><span></span><span></span><span></span><span></span><span></span></div>
    </div>
    <div class="action-row">
      <button class="action-btn" onclick="practiceTaekwondo()">🥋 얍! 발차기 연습</button>
    </div>
  </div>`;
}

function characterGateHTML() {
  return `<div class="activity-box">
    <p>먼저 <b>우리집</b>에서 캐릭터를 만들어보세요! 🏠✨</p>
  </div>`;
}

function practiceTaekwondo() {
  checkNewDay();
  if (state.daily.taekwondoCount >= 5) {
    toast("오늘 연습은 다 했어요! 내일 또 만나요 💪");
    return;
  }
  state.daily.taekwondoCount++;
  state.coins += 30;
  let leveled = false;
  if (state.daily.taekwondoCount === 5 && state.taekwondoBeltIndex < TAEKWONDO_BELTS.length - 1) {
    state.taekwondoBeltIndex++;
    leveled = true;
  }
  save();
  updateCoinDisplay();
  renderActivityPanel();
  kickAnimate("#tkd-avatar");
  triggerBurst("#tkd-burst");
  floatCoinPopup(30);
  toast(leveled ? `승급했어요! 이제 ${TAEKWONDO_BELTS[state.taekwondoBeltIndex]}예요 🎉` : "얍! 기합소리와 함께 망고코인 +30 🥭");
}

function pianoHTML() {
  const notes = [
    ["도", 261.63],
    ["레", 293.66],
    ["미", 329.63],
    ["파", 349.23],
    ["솔", 392.0],
    ["라", 440.0],
    ["시", 493.88],
    ["도", 523.25],
  ];
  return `<div class="activity-box">
    <p>오늘 연습 ${state.daily.pianoCount}/5</p>
    <div class="piano-keys">${notes.map((n) => `<button class="piano-key" onclick="playNote(event, ${n[1]}, '${n[0]}')">${n[0]}</button>`).join("")}</div>
    <div class="action-row"><button class="action-btn" onclick="finishPiano()">🎵 연습 완료</button></div>
  </div>`;
}

function playNote(e, freq, label) {
  try {
    const ctx = window.__mangoAudioCtx || (window.__mangoAudioCtx = new (window.AudioContext || window.webkitAudioContext)());
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.2, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.5);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.5);
  } catch (err) {
    /* 오디오 미지원 환경은 조용히 무시 */
  }
  pianoNotesThisSession++;

  const btn = e.currentTarget;
  btn.classList.remove("key-press");
  void btn.offsetWidth;
  btn.classList.add("key-press");
  setTimeout(() => btn.classList.remove("key-press"), 150);

  const rect = btn.getBoundingClientRect();
  const note = document.createElement("div");
  note.className = "note-pop";
  note.textContent = "🎵" + label;
  note.style.left = rect.left + rect.width / 2 + "px";
  note.style.top = rect.top + "px";
  spawnFx(note);
}

function finishPiano() {
  checkNewDay();
  if (state.daily.pianoCount >= 5) {
    toast("오늘 연습은 다 했어요! 내일 또 쳐봐요 🎹");
    return;
  }
  if (pianoNotesThisSession < 3) {
    toast("건반을 3번 이상 눌러서 연주해봐요! 🎵");
    return;
  }
  pianoNotesThisSession = 0;
  state.daily.pianoCount++;
  state.coins += 30;
  save();
  updateCoinDisplay();
  floatCoinPopup(30);
  renderActivityPanel();
  toast("연습 완료! 망고코인 +30 🥭🎶");
}

function playgroundHTML() {
  return `<div class="activity-box">
    <p>친구들과 신나게 놀아봐요!</p>
    <div class="action-row">
      <button class="action-btn" onclick="havingFun('그네')">🎠 그네 타기</button>
      <button class="action-btn" onclick="havingFun('미끄럼틀')">🛝 미끄럼틀 타기</button>
      <button class="action-btn" onclick="havingFun('시소')">⚖️ 시소 타기</button>
    </div>
  </div>`;
}

function havingFun(name) {
  toast(`${name} 타고 신나게 놀았어요! 하하호호 🎉`);
  bounce("#topbar-avatar");
}

function bankHTML() {
  return `<div class="activity-box bank-box">
    <p>지갑: 🥭 ${state.coins} &nbsp;|&nbsp; 저금통: 🥭 ${state.savings}</p>
    <div class="action-row">
      <button class="action-btn" onclick="claimAllowance()">${state.daily.allowanceClaimed ? "오늘 용돈 받음 ✓" : "💌 오늘의 용돈 받기 (+500)"}</button>
    </div>
    <div class="bank-transfer">
      <input type="number" id="bank-amount" min="0" placeholder="금액" />
      <button class="action-btn small" onclick="depositAmount()">저금하기 ⬇️</button>
      <button class="action-btn small" onclick="withdrawAmount()">찾기 ⬆️</button>
    </div>
  </div>`;
}

function claimAllowance() {
  checkNewDay();
  if (state.daily.allowanceClaimed) {
    toast("오늘은 이미 용돈을 받았어요!");
    return;
  }
  state.daily.allowanceClaimed = true;
  state.coins += 500;
  save();
  updateCoinDisplay();
  floatCoinPopup(500);
  renderActivityPanel();
  toast("용돈 500 망고코인을 받았어요! 🥭💌");
}

function depositAmount() {
  const input = document.getElementById("bank-amount");
  const amt = Math.floor(Number(input.value));
  if (!amt || amt <= 0) {
    toast("금액을 입력해주세요");
    return;
  }
  if (amt > state.coins) {
    toast("가진 코인보다 많이 저금할 수 없어요");
    return;
  }
  state.coins -= amt;
  state.savings += amt;
  save();
  updateCoinDisplay();
  renderActivityPanel();
  toast(`🥭 ${amt} 저금했어요!`);
}

function withdrawAmount() {
  const input = document.getElementById("bank-amount");
  const amt = Math.floor(Number(input.value));
  if (!amt || amt <= 0) {
    toast("금액을 입력해주세요");
    return;
  }
  if (amt > state.savings) {
    toast("저금통에 그만큼 돈이 없어요");
    return;
  }
  state.savings -= amt;
  state.coins += amt;
  save();
  updateCoinDisplay();
  renderActivityPanel();
  toast(`🥭 ${amt} 찾았어요!`);
}

// ---- 시작 ----
updateCoinDisplay();
updateTopbarAvatar();
showMap();
