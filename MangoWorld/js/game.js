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
    coins: 10000,
    savings: 0,
    savingsUpdatedAt: Date.now(),
    characters: [],
    activeCharacterId: null,
    owned,
    placed: {},
    charPlaced: {},
    taekwondoBeltIndex: 0,
    daily: { date: todayStr(), taekwondoCount: 0, pianoCount: 0, allowanceClaimed: false, jobCounts: {}, choreCounts: {} },
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
let selectedCharId = null;
let pianoNotesThisSession = 0;
let toastTimer = null;
let charFormOpen = false;
let editingCharacterId = null;
let charFormDraft = { name: "", preset: null, accessory: null };

function checkNewDay() {
  const t = todayStr();
  if (!state.daily || state.daily.date !== t) {
    state.daily = { date: t, taekwondoCount: 0, pianoCount: 0, allowanceClaimed: false, jobCounts: {}, choreCounts: {} };
    save();
  }
  if (!state.daily.jobCounts) state.daily.jobCounts = {};
  if (!state.daily.choreCounts) state.daily.choreCounts = {};
}
checkNewDay();

// ---- DOM refs ----
const btnHome = document.getElementById("btn-home");
const topbarAvatar = document.getElementById("topbar-avatar");
const coinCountEl = document.getElementById("coin-count");
const screenMap = document.getElementById("screen-map");
const screenLocation = document.getElementById("screen-location");
const mapHotspots = document.getElementById("map-hotspots");
const mapTrees = document.getElementById("map-trees");
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
const appEl = document.getElementById("app");
const uiToggleBtn = document.getElementById("ui-toggle");

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
  const target = foot || container.querySelector(".avatar-preset-img");
  if (!target) return;
  const cls = foot ? "kick-anim" : "kick-anim-whole";
  target.classList.remove(cls);
  void target.offsetWidth;
  target.classList.add(cls);
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

function accessoryOverlayHtml(character, size) {
  if (!character || !character.accessory) return "";
  const acc = ACCESSORY_OPTIONS.find((a) => a.id === character.accessory);
  if (!acc) return "";
  return `<span class="avatar-accessory" style="font-size:${Math.round(size * 0.34)}px;">${acc.emoji}</span>`;
}

function avatarWithAccessoryHtml(character, size) {
  return `<span class="avatar-wrap" style="width:${size}px;height:${size}px;">${renderAvatarSVG(character, size)}${accessoryOverlayHtml(character, size)}</span>`;
}

function updateTopbarAvatar() {
  topbarAvatar.innerHTML = avatarWithAccessoryHtml(getActiveCharacter(), 40);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
}

function itemVisualHtml(item, cls) {
  return item.icon
    ? `<img class="${cls} icon-img" src="img/items/${item.icon}.webp" alt="${escapeHtml(item.name)}" />`
    : `<span class="${cls}">${item.emoji}</span>`;
}

// ---- 마을 지도 ----
function showMap() {
  currentLocation = null;
  appEl.classList.remove("immersive");
  screenMap.classList.remove("hidden");
  screenLocation.classList.add("hidden");
  renderMapHotspots();
  renderMapIntro();
  playScreenEnter(screenMap);
}

function toggleImmersive() {
  const on = appEl.classList.toggle("immersive");
  uiToggleBtn.textContent = on ? "⤢" : "⛶";
  uiToggleBtn.title = on ? "화면 원래대로" : "화면 전체보기";
}

function renderMapIntro() {
  const active = getActiveCharacter();
  mapLead.textContent = active
    ? `${active.name}과 함께 군포 곳곳을 자유롭게 탐험해보세요!`
    : "나만의 캐릭터를 만들고 군포 곳곳을 자유롭게 탐험해보세요! (우리집에서 캐릭터 만들기)";
}

function renderMapHotspots() {
  mapHotspots.innerHTML = LOCATIONS.map((loc, idx) => {
    return `<button class="map-hotspot" data-loc-id="${loc.id}" style="animation-delay:${idx * 0.04}s;" onclick="enterLocation('${loc.id}')" title="${loc.name}" aria-label="${loc.name}"></button>`;
  }).join("");
  renderMangoTrees();
  requestAnimationFrame(positionMapHotspots);
}

function renderMangoTrees() {
  if (!mapTrees) return;
  mapTrees.innerHTML = MANGO_TREES.map(
    (t, idx) =>
      `<button class="map-tree-spot" data-tree-idx="${idx}" style="animation-delay:${idx * 0.03}s;" onclick="clickMangoTree(event, ${idx})" title="망고나무" aria-label="망고나무 탭해서 코인 받기"></button>`
  ).join("");
}

function clickMangoTree(e, idx) {
  const el = e.currentTarget;
  state.coins += 50;
  save();
  updateCoinDisplay();
  floatCoinPopup(50);
  bounce(el);
  spawnSparkles(el);
}

// 지도 이미지는 background-size:contain으로 표시되므로(가로/세로 어느 화면비에서도
// 잘리지 않게), 핫스팟 좌표도 실제로 그려지는 이미지 영역(letterbox 제외)을
// 계산해서 픽셀 단위로 맞춰줘야 정확히 건물 위에 위치합니다.
const MAP_IMG_W = 1400;
const MAP_IMG_H = 663;

function positionMapHotspots() {
  const canvas = document.getElementById("map-canvas");
  if (!canvas || screenMap.classList.contains("hidden")) return;
  const rect = canvas.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  const containerRatio = rect.width / rect.height;
  const imgRatio = MAP_IMG_W / MAP_IMG_H;
  let renderW, renderH;
  if (imgRatio > containerRatio) {
    renderW = rect.width;
    renderH = rect.width / imgRatio;
  } else {
    renderH = rect.height;
    renderW = rect.height * imgRatio;
  }
  const offsetX = (rect.width - renderW) / 2;
  const offsetY = (rect.height - renderH) / 2;
  document.querySelectorAll(".map-hotspot").forEach((el) => {
    const loc = LOCATIONS.find((l) => l.id === el.dataset.locId);
    if (!loc) return;
    const p = loc.mapPos;
    el.style.left = offsetX + (p.x / 100) * renderW + "px";
    el.style.top = offsetY + (p.y / 100) * renderH + "px";
    el.style.width = (p.w / 100) * renderW + "px";
    el.style.height = (p.h / 100) * renderH + "px";
  });
  const treeSize = renderW * 0.09;
  document.querySelectorAll(".map-tree-spot").forEach((el) => {
    const t = MANGO_TREES[Number(el.dataset.treeIdx)];
    if (!t) return;
    el.style.left = offsetX + (t.x / 100) * renderW + "px";
    el.style.top = offsetY + (t.y / 100) * renderH + "px";
    el.style.width = treeSize + "px";
    el.style.height = treeSize + "px";
  });
}

window.addEventListener("resize", () => {
  if (!screenMap.classList.contains("hidden")) positionMapHotspots();
});

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
  if (!state.charPlaced[locationId]) {
    state.charPlaced[locationId] = [];
  }
}

function enterLocation(id) {
  currentLocation = LOCATIONS.find((l) => l.id === id);
  ensureRoomInit(id);
  selectedItemId = null;
  selectedCharId = null;
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
uiToggleBtn.addEventListener("click", toggleImmersive);

tabButtons.forEach((btn) => {
  btn.addEventListener("click", () => {
    tabButtons.forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const tab = btn.dataset.tab;
    tabInventory.classList.toggle("hidden", tab !== "inventory");
    tabShop.classList.toggle("hidden", tab !== "shop");
  });
});

// ---- 방 꾸미기 (배치 + 드래그 이동) ----
function makeDraggable(el, { onTap, onDragEnd }) {
  const DRAG_THRESHOLD = 6;
  let startX = 0,
    startY = 0,
    dragging = false,
    active = false;

  el.addEventListener("pointerdown", (e) => {
    e.stopPropagation();
    active = true;
    dragging = false;
    startX = e.clientX;
    startY = e.clientY;
    try {
      el.setPointerCapture(e.pointerId);
    } catch (err) {
      /* 캡처 미지원 환경은 무시 */
    }
  });

  el.addEventListener("pointermove", (e) => {
    if (!active) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    if (!dragging && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
      dragging = true;
      el.classList.add("dragging");
    }
    if (dragging) {
      const rect = roomCanvas.getBoundingClientRect();
      const x = Math.min(94, Math.max(2, ((e.clientX - rect.left) / rect.width) * 100));
      const y = Math.min(92, Math.max(4, ((e.clientY - rect.top) / rect.height) * 100));
      el.style.left = x + "%";
      el.style.top = y + "%";
      el.dataset.pendingX = x;
      el.dataset.pendingY = y;
    }
  });

  const finish = () => {
    if (!active) return;
    active = false;
    el.classList.remove("dragging");
    if (dragging) {
      onDragEnd(parseFloat(el.dataset.pendingX), parseFloat(el.dataset.pendingY));
    } else {
      onTap();
    }
    dragging = false;
  };

  el.addEventListener("pointerup", finish);
  el.addEventListener("pointercancel", finish);
}

function renderRoom() {
  const placedList = state.placed[currentLocation.id] || [];
  const itemsHtml = placedList
    .map((p) => {
      const item = ITEMS.find((i) => i.id === p.itemId);
      if (!item) return "";
      return `<div class="placed-item" data-kind="item" data-uid="${p.uid}" style="left:${p.x}%; top:${p.y}%;" title="탭해서 치우기 · 드래그로 이동">
        ${itemVisualHtml(item, "placed-emoji")}
      </div>`;
    })
    .join("");

  const charPlacedList = state.charPlaced[currentLocation.id] || [];
  const charsHtml = charPlacedList
    .map((p) => {
      const c = state.characters.find((x) => x.id === p.charId);
      if (!c) return "";
      return `<div class="placed-item placed-char" data-kind="char" data-uid="${p.uid}" style="left:${p.x}%; top:${p.y}%;" title="탭해서 치우기 · 드래그로 이동">
        ${avatarWithAccessoryHtml(c, 92)}
      </div>`;
    })
    .join("");

  const hint = selectedItemId
    ? `<div class="room-hint active">✋ 놓을 위치를 탭하세요</div>`
    : selectedCharId
    ? `<div class="room-hint active">✋ 캐릭터를 놓을 위치를 탭하세요</div>`
    : `<div class="room-hint">보관함에서 아이템이나 캐릭터를 골라 배치해보세요</div>`;

  roomCanvas.innerHTML = itemsHtml + charsHtml + hint;

  roomCanvas.querySelectorAll(".placed-item").forEach((el) => {
    const kind = el.dataset.kind;
    const itemUid = el.dataset.uid;
    makeDraggable(el, {
      onTap: () => {
        if (kind === "char") requestRemoveCharPlaced(currentLocation.id, itemUid, el);
        else requestRemovePlaced(currentLocation.id, itemUid, el);
      },
      onDragEnd: (x, y) => {
        if (kind === "char") commitCharPosition(currentLocation.id, itemUid, x, y);
        else commitPlacedPosition(currentLocation.id, itemUid, x, y);
      },
    });
  });
}

roomCanvas.addEventListener("click", (e) => {
  if (e.target.closest(".placed-item")) return;
  if (!selectedItemId && !selectedCharId) return;
  const rect = roomCanvas.getBoundingClientRect();
  const x = ((e.clientX - rect.left) / rect.width) * 100;
  const y = ((e.clientY - rect.top) / rect.height) * 100;
  const clampedX = Math.min(92, Math.max(4, x));
  const clampedY = Math.min(88, Math.max(8, y));
  if (selectedCharId) {
    state.charPlaced[currentLocation.id].push({ uid: uid(), charId: selectedCharId, x: clampedX, y: clampedY });
    selectedCharId = null;
  } else {
    state.placed[currentLocation.id].push({ uid: uid(), itemId: selectedItemId, x: clampedX, y: clampedY });
    selectedItemId = null;
  }
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

function commitPlacedPosition(locId, itemUid, x, y) {
  const list = state.placed[locId] || [];
  const p = list.find((p) => p.uid === itemUid);
  if (p) {
    p.x = x;
    p.y = y;
    save();
  }
}

function requestRemoveCharPlaced(locId, itemUid, el) {
  if (!el || el.classList.contains("removing")) return;
  el.classList.add("removing");
  setTimeout(() => removeCharPlaced(locId, itemUid), 200);
}

function removeCharPlaced(locId, itemUid) {
  const list = state.charPlaced[locId] || [];
  const idx = list.findIndex((p) => p.uid === itemUid);
  if (idx === -1) return;
  list.splice(idx, 1);
  save();
  renderRoom();
  toast("캐릭터를 화면에서 치웠어요");
}

function commitCharPosition(locId, itemUid, x, y) {
  const list = state.charPlaced[locId] || [];
  const p = list.find((p) => p.uid === itemUid);
  if (p) {
    p.x = x;
    p.y = y;
    save();
  }
}

function selectInvItem(id) {
  selectedItemId = selectedItemId === id ? null : id;
  selectedCharId = null;
  renderInventoryTab();
  renderRoom();
}

function selectCharForPlacement(id) {
  selectedCharId = selectedCharId === id ? null : id;
  selectedItemId = null;
  renderInventoryTab();
  renderRoom();
}

function charChipsForPlacementHTML() {
  if (!state.characters.length) return "";
  return `<p class="section-label">🧍 캐릭터 배치 (탭해서 놓기)</p>
    <div class="char-place-row">${state.characters
      .map(
        (c) => `
      <button class="item-chip char-place-chip ${selectedCharId === c.id ? "selected" : ""}" onclick="selectCharForPlacement('${c.id}')">
        ${avatarWithAccessoryHtml(c, 28)}<span class="chip-name">${escapeHtml(c.name)}</span>
      </button>`
      )
      .join("")}</div>`;
}

function renderInventoryTab() {
  const items = ITEMS.filter((i) => i.room === currentLocation.id && state.owned[i.id]);
  const itemsHtml = items.length
    ? items
        .map(
          (it) => `
      <button class="item-chip ${selectedItemId === it.id ? "selected" : ""}" onclick="selectInvItem('${it.id}')">
        ${itemVisualHtml(it, "chip-emoji")}<span class="chip-name">${it.name}</span>
      </button>`
        )
        .join("")
    : `<p class="empty-hint">아직 보관함이 비었어요. 상점에서 아이템을 구매해보세요!</p>`;
  tabInventory.innerHTML = charChipsForPlacementHTML() + itemsHtml;
}

function renderShopTab() {
  const items = ITEMS.filter((i) => !i.starter && ((i.room === currentLocation.id && !i.shopAt) || i.shopAt === currentLocation.id));
  tabShop.innerHTML = items.length
    ? items
        .map((it) => {
          const owned = !!state.owned[it.id];
          return `<div class="item-chip shop ${owned ? "owned" : ""}">
          ${itemVisualHtml(it, "chip-emoji")}
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
  } else if (loc.menu) {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = `<div class="activity-box">${jobBlockHTML(loc.id)}</div>` + menuHTML(loc.id);
  } else if (JOBS[loc.id]) {
    locActivity.classList.remove("hidden");
    locActivity.innerHTML = jobHTML(loc.id);
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
    ${charFormOpen ? characterFormHTML() : choresBlockHTML()}
  </div>`;
}

function choresBlockHTML() {
  return `<div class="chores-box">
    <p class="wardrobe-title">집안일 알바</p>
    <div class="action-row">
      ${CHORES.map((c) => {
        const count = state.daily.choreCounts[c.id] || 0;
        const done = count >= 5;
        return `<button class="action-btn small" onclick="doChore('${c.id}')" ${done ? "disabled" : ""}>${c.emoji} ${c.label} (${count}/5)</button>`;
      }).join("")}
    </div>
  </div>`;
}

function doChore(id) {
  checkNewDay();
  const count = state.daily.choreCounts[id] || 0;
  if (count >= 5) {
    toast("오늘 이 집안일은 다 했어요! 내일 또 해봐요 💪");
    return;
  }
  const chore = CHORES.find((c) => c.id === id);
  state.daily.choreCounts[id] = count + 1;
  state.coins += chore.reward;
  save();
  updateCoinDisplay();
  floatCoinPopup(chore.reward);
  renderActivityPanel();
  bounce("#topbar-avatar");
  toast(`${chore.emoji} ${chore.label} 완료! 망고코인 +${chore.reward} 🥭`);
}

function jobBlockHTML(locId) {
  const job = JOBS[locId];
  if (!job) return "";
  const count = state.daily.jobCounts[locId] || 0;
  const done = count >= 5;
  return `<p class="job-count">오늘 알바 ${count}/5</p>
    <div class="action-row">
      <button class="action-btn" onclick="doJob('${locId}')" ${done ? "disabled" : ""}>${job.emoji} ${job.label} (+${job.reward})</button>
    </div>`;
}

function jobHTML(locId) {
  return `<div class="activity-box">${jobBlockHTML(locId)}</div>`;
}

function doJob(locId) {
  checkNewDay();
  const count = state.daily.jobCounts[locId] || 0;
  if (count >= 5) {
    toast("오늘 알바는 다 했어요! 내일 또 해봐요 💪");
    return;
  }
  const job = JOBS[locId];
  state.daily.jobCounts[locId] = count + 1;
  state.coins += job.reward;
  save();
  updateCoinDisplay();
  floatCoinPopup(job.reward);
  renderActivityPanel();
  bounce("#topbar-avatar");
  toast(`${job.emoji} ${job.label} 완료! 망고코인 +${job.reward} 🥭`);
}

function characterChipHTML(c) {
  const isActive = c.id === state.activeCharacterId;
  return `<div class="char-chip ${isActive ? "active" : ""}">
    <div class="char-chip-avatar" onclick="selectCharacter('${c.id}')">${avatarWithAccessoryHtml(c, 56)}</div>
    <div class="char-chip-name" onclick="selectCharacter('${c.id}')">${c.gender === "boy" ? "👦" : "👧"} ${escapeHtml(c.name)}</div>
    <div class="char-chip-actions">
      <button class="chip-icon-btn" onclick="startEditCharacter('${c.id}')" title="수정">✏️</button>
      <button class="chip-icon-btn" onclick="deleteCharacterConfirm('${c.id}')" title="삭제">🗑️</button>
    </div>
  </div>`;
}

function characterFormHTML() {
  const preview = charFormDraft.preset ? { preset: charFormDraft.preset, accessory: charFormDraft.accessory } : null;
  const girls = CHARACTER_PRESETS.filter((p) => p.gender === "girl");
  const boys = CHARACTER_PRESETS.filter((p) => p.gender === "boy");
  const presetTile = (p) => `
    <button class="preset-tile ${charFormDraft.preset === p.id ? "active" : ""}" onclick="setDraftPreset('${p.id}')" title="${p.label}">
      <img src="img/characters/${p.id}.webp" alt="${p.label}" />
    </button>`;
  const accessoryTile = (a) => `
    <button class="preset-tile accessory-tile ${charFormDraft.accessory === a.id ? "active" : ""}" onclick="setDraftAccessory('${a.id}')" title="${a.label}">
      <span class="accessory-emoji">${a.emoji}</span>
    </button>`;
  return `<div class="char-form">
    <div class="avatar-preview">${renderAvatarSVG(preview, 110)}${accessoryOverlayHtml(preview, 110)}</div>
    <input id="char-name-input" class="char-name-input" type="text" maxlength="8" placeholder="이름을 입력해주세요" value="${escapeHtml(charFormDraft.name)}" />
    <div class="swatch-group">
      <p>👧 여자아이</p>
      <div class="preset-grid">${girls.map(presetTile).join("")}</div>
    </div>
    <div class="swatch-group">
      <p>👦 남자아이</p>
      <div class="preset-grid">${boys.map(presetTile).join("")}</div>
    </div>
    <div class="swatch-group">
      <p>✨ 꾸미기 (선택)</p>
      <div class="preset-grid">
        <button class="preset-tile accessory-tile ${!charFormDraft.accessory ? "active" : ""}" onclick="setDraftAccessory(null)" title="없음">
          <span class="accessory-emoji">🚫</span>
        </button>
        ${ACCESSORY_OPTIONS.map(accessoryTile).join("")}
      </div>
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
  charFormDraft = { name: "", preset: null, accessory: null };
  renderActivityPanel();
}

function startEditCharacter(id) {
  const c = state.characters.find((x) => x.id === id);
  if (!c) return;
  charFormOpen = true;
  editingCharacterId = id;
  charFormDraft = { name: c.name, preset: c.preset, accessory: c.accessory || null };
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

function setDraftPreset(id) {
  syncDraftName();
  charFormDraft.preset = id;
  renderActivityPanel();
}

function setDraftAccessory(id) {
  syncDraftName();
  charFormDraft.accessory = id;
  renderActivityPanel();
}

function saveCharacterForm() {
  syncDraftName();
  const name = charFormDraft.name.trim();
  if (!name) {
    toast("이름을 입력해주세요");
    return;
  }
  if (!charFormDraft.preset) {
    toast("캐릭터 모습을 골라주세요");
    return;
  }
  const presetDef = CHARACTER_PRESETS.find((p) => p.id === charFormDraft.preset);
  const gender = presetDef ? presetDef.gender : "girl";
  if (editingCharacterId) {
    const c = state.characters.find((x) => x.id === editingCharacterId);
    if (c) {
      c.name = name;
      c.preset = charFormDraft.preset;
      c.gender = gender;
      c.accessory = charFormDraft.accessory || null;
    }
    toast(`${name} 정보를 수정했어요!`);
  } else {
    const c = { id: uid(), name, preset: charFormDraft.preset, gender, accessory: charFormDraft.accessory || null };
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
      ${avatarWithAccessoryHtml(active, 100)}
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
    <hr class="activity-divider" />
    ${jobBlockHTML("playground")}
  </div>`;
}

function havingFun(name) {
  toast(`${name} 타고 신나게 놀았어요! 하하호호 🎉`);
  bounce("#topbar-avatar");
}

function bankHTML() {
  return `<div class="activity-box bank-box">
    <p id="bank-balance-text">지갑: 🥭 ${state.coins} &nbsp;|&nbsp; 저금통: 🥭 ${state.savings}</p>
    <p class="interest-note" id="bank-interest-note" style="${state.savings > 0 ? "" : "display:none;"}">📈 저금통은 1분마다 🥭 100씩 저절로 불어나요!</p>
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

const INTEREST_TICK_MS = 60000;
const INTEREST_AMOUNT = 100;

function accrueSavingsInterest() {
  const now = Date.now();
  if (!state.savingsUpdatedAt) state.savingsUpdatedAt = now;
  if (state.savings <= 0) {
    state.savingsUpdatedAt = now;
    return false;
  }
  const elapsedTicks = Math.floor((now - state.savingsUpdatedAt) / INTEREST_TICK_MS);
  if (elapsedTicks <= 0) return false;
  state.savings += elapsedTicks * INTEREST_AMOUNT;
  state.savingsUpdatedAt += elapsedTicks * INTEREST_TICK_MS;
  save();
  return true;
}

// 은행 화면이 떠 있는 동안은 패널 전체를 다시 그리지 않고 잔액 텍스트만
// 갱신합니다. (전체 재렌더링을 하면 저금/출금 입력창(#bank-amount)이
// 매번 새로 만들어지면서 사용자가 입력 중이던 금액이 사라지는 버그가 있었음)
function updateBankLiveDisplay() {
  if (!currentLocation || currentLocation.id !== "bank") return;
  const balanceEl = document.getElementById("bank-balance-text");
  if (balanceEl) balanceEl.textContent = `지갑: 🥭 ${state.coins}  |  저금통: 🥭 ${state.savings}`;
  const noteEl = document.getElementById("bank-interest-note");
  if (noteEl) noteEl.style.display = state.savings > 0 ? "" : "none";
}

setInterval(() => {
  const changed = accrueSavingsInterest();
  if (changed) updateBankLiveDisplay();
}, 1000);

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
  accrueSavingsInterest();
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
  state.savingsUpdatedAt = Date.now();
  save();
  updateCoinDisplay();
  renderActivityPanel();
  toast(`🥭 ${amt} 저금했어요! 이제 1초마다 100씩 불어나요 📈`);
}

function withdrawAmount() {
  accrueSavingsInterest();
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
  state.savingsUpdatedAt = Date.now();
  save();
  updateCoinDisplay();
  renderActivityPanel();
  toast(`🥭 ${amt} 찾았어요!`);
}

// ---- 전체화면 ----
function requestFullscreenOnce() {
  document.removeEventListener("pointerdown", requestFullscreenOnce);
  const el = document.documentElement;
  const request = el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen;
  if (request) {
    try {
      request.call(el).catch(() => {});
    } catch (err) {
      /* 풀스크린 미지원/거부 시 조용히 무시 */
    }
  }
}
document.addEventListener("pointerdown", requestFullscreenOnce);

// ---- 시작 ----
accrueSavingsInterest();
updateCoinDisplay();
updateTopbarAvatar();
showMap();
