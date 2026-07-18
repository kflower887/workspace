// 망고월드 - 장소별 배경 SVG (창문/가구 실루엣 등 디테일 추가)

const ROOM_COLORS = {
  home: { wall: "#ffe6ef", floor: "#ffc9d9" },
  school: { wall: "#e3f3fc", floor: "#bfe4f6" },
  cafe: { wall: "#f3e3cf", floor: "#dcc3a0" },
  taekwondo: { wall: "#e2f5e0", floor: "#b9e3b4" },
  playground: { wall: "#fff2cf", floor: "#ffdd94" },
  piano: { wall: "#ece3fb", floor: "#d3c1f5" },
  restaurant: { wall: "#ffe3da", floor: "#ffbfae" },
  stationery: { wall: "#dbf7f5", floor: "#aeeae6" },
  mart: { wall: "#e3e4ff", floor: "#c2c4ff" },
  bank: { wall: "#eaf1e2", floor: "#cfe0bd" },
};

function sceneShell(wall, floor, decor) {
  return `<svg class="room-bg" viewBox="0 0 400 240" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
    <rect x="0" y="0" width="400" height="144" fill="${wall}" />
    <rect x="0" y="144" width="400" height="96" fill="${floor}" />
    <rect x="0" y="142" width="400" height="3" fill="rgba(255,255,255,0.7)" />
    ${decor}
  </svg>`;
}

const ROOM_DECOR = {
  home: `
    <rect x="24" y="18" width="70" height="58" rx="6" fill="#fff7fb" stroke="#ffffff" stroke-width="4"/>
    <line x1="59" y1="18" x2="59" y2="76" stroke="#ffffff" stroke-width="3"/>
    <line x1="24" y1="47" x2="94" y2="47" stroke="#ffffff" stroke-width="3"/>
    <path d="M6 14 Q24 -4 42 14 L42 20 L6 20 Z" fill="#ffb6c9"/>
    <path d="M76 14 Q94 -4 112 14 L112 20 L76 20 Z" fill="#ffb6c9"/>
    <rect x="296" y="14" width="80" height="66" rx="8" fill="none" stroke="#ffffff" stroke-width="4"/>
    <path d="M330 36 q9 -12 18 0 q9 -12 18 0 q0 14 -18 26 q-18 -12 -18 -26 Z" fill="#ff9eb0"/>
    <ellipse cx="200" cy="205" rx="96" ry="20" fill="rgba(255,255,255,0.4)"/>
    <ellipse cx="200" cy="205" rx="70" ry="14" fill="rgba(255,255,255,0.3)"/>
  `,
  school: `
    <rect x="150" y="14" width="150" height="66" rx="4" fill="#3f5a4a"/>
    <rect x="150" y="14" width="150" height="66" rx="4" fill="none" stroke="#7a5c3c" stroke-width="6"/>
    <text x="225" y="54" font-size="20" fill="#eafaf0" text-anchor="middle" font-family="'Apple SD Gothic Neo', sans-serif">당정초</text>
    <rect x="20" y="24" width="54" height="46" rx="4" fill="#fbfeff" stroke="#ffffff" stroke-width="4"/>
    <line x1="47" y1="24" x2="47" y2="70" stroke="#bfe4f6" stroke-width="3"/>
    <line x1="20" y1="47" x2="74" y2="47" stroke="#bfe4f6" stroke-width="3"/>
    <rect x="0" y="150" width="400" height="6" fill="rgba(255,255,255,0.35)"/>
    <rect x="0" y="180" width="400" height="6" fill="rgba(255,255,255,0.3)"/>
    <rect x="0" y="210" width="400" height="6" fill="rgba(255,255,255,0.3)"/>
  `,
  cafe: `
    <line x1="200" y1="0" x2="200" y2="34" stroke="#8a6a45" stroke-width="3"/>
    <path d="M182 34 h36 l-6 20 h-24 z" fill="#4a3826"/>
    <ellipse cx="200" cy="60" rx="26" ry="7" fill="rgba(255,220,150,0.55)"/>
    <rect x="24" y="18" width="60" height="52" rx="6" fill="#fffaf1" stroke="#ffffff" stroke-width="4"/>
    <line x1="54" y1="18" x2="54" y2="70" stroke="#dcc3a0" stroke-width="3"/>
    <rect x="300" y="24" width="70" height="46" rx="6" fill="#7a5636"/>
    <rect x="306" y="30" width="58" height="14" rx="2" fill="#fff3d6" opacity="0.8"/>
    <circle cx="60" cy="190" r="5" fill="#6b4a37" opacity="0.5"/>
    <circle cx="120" cy="205" r="5" fill="#6b4a37" opacity="0.5"/>
    <circle cx="300" cy="195" r="5" fill="#6b4a37" opacity="0.5"/>
    <circle cx="340" cy="215" r="5" fill="#6b4a37" opacity="0.5"/>
  `,
  taekwondo: `
    <rect x="150" y="20" width="60" height="60" rx="30" fill="#ffffff" stroke="#c33" stroke-width="3"/>
    <circle cx="180" cy="50" r="14" fill="#c33"/>
    <path d="M180 36 A14 14 0 0 1 180 64 A7 7 0 0 1 180 50 A7 7 0 0 0 180 36" fill="#003478"/>
    <rect x="24" y="18" width="66" height="56" rx="6" fill="#eaf6ea" stroke="#ffffff" stroke-width="5"/>
    <rect x="30" y="24" width="54" height="44" rx="4" fill="#d7ecd7"/>
    <rect x="290" y="20" width="86" height="60" rx="6" fill="none" stroke="#ffffff" stroke-width="4"/>
    ${gridLines(0, 150, 400, 90, 40)}
  `,
  playground: `
    <circle cx="340" cy="34" r="24" fill="#ffd166"/>
    <g opacity="0.9">
      <ellipse cx="60" cy="34" rx="26" ry="14" fill="#ffffff"/>
      <ellipse cx="84" cy="30" rx="20" ry="12" fill="#ffffff"/>
      <ellipse cx="40" cy="30" rx="18" ry="11" fill="#ffffff"/>
    </g>
    <path d="M0 160 Q40 148 80 160 T160 160 T240 160 T320 160 T400 160 V240 H0 Z" fill="rgba(120,180,90,0.28)"/>
    <path d="M330 150 L330 190 M330 160 L316 148 M330 165 L344 152" stroke="#5a8a4a" stroke-width="4" stroke-linecap="round"/>
  `,
  piano: `
    <rect x="20" y="16" width="56" height="60" rx="4" fill="#f4edff" stroke="#ffffff" stroke-width="4"/>
    <path d="M10 12 Q48 -6 86 12 L86 22 L10 22 Z" fill="#c8b6ff" opacity="0.8"/>
    <g fill="#c8b6ff">
      <text x="180" y="40" font-size="26">♪</text>
      <text x="230" y="26" font-size="20">♫</text>
      <text x="270" y="46" font-size="22">♪</text>
      <text x="310" y="30" font-size="18">♬</text>
    </g>
    <rect x="300" y="18" width="76" height="58" rx="8" fill="none" stroke="#ffffff" stroke-width="4"/>
    <ellipse cx="200" cy="204" rx="100" ry="18" fill="rgba(255,255,255,0.35)"/>
  `,
  restaurant: `
    <line x1="200" y1="0" x2="200" y2="30" stroke="#8a6a45" stroke-width="3"/>
    <path d="M178 30 h44 l-8 22 h-28 z" fill="#ef5b45"/>
    <ellipse cx="200" cy="58" rx="24" ry="6" fill="rgba(255,200,150,0.5)"/>
    <rect x="24" y="18" width="64" height="54" rx="6" fill="#fff3ee" stroke="#ffffff" stroke-width="4"/>
    <line x1="56" y1="18" x2="56" y2="72" stroke="#ffbfae" stroke-width="3"/>
    ${checker(280, 20, 96, 56, 16)}
  `,
  stationery: `
    <rect x="20" y="20" width="360" height="8" fill="#ffffff" opacity="0.85"/>
    <rect x="20" y="46" width="360" height="8" fill="#ffffff" opacity="0.75"/>
    <rect x="20" y="72" width="360" height="8" fill="#ffffff" opacity="0.65"/>
    <circle cx="60" cy="190" r="7" fill="#ff9eb0"/>
    <circle cx="110" cy="205" r="6" fill="#ffd166"/>
    <circle cx="160" cy="185" r="7" fill="#8ecae6"/>
    <circle cx="230" cy="210" r="6" fill="#8ac926"/>
    <circle cx="290" cy="190" r="7" fill="#c8b6ff"/>
    <circle cx="340" cy="205" r="6" fill="#ff9eb0"/>
  `,
  mart: `
    ${gridLines(20, 16, 200, 64, 34)}
    <rect x="20" y="16" width="200" height="64" fill="none" stroke="#ffffff" stroke-width="4"/>
    <rect x="250" y="24" width="30" height="18" rx="3" fill="#ffffff" opacity="0.8"/>
    <rect x="290" y="24" width="30" height="18" rx="3" fill="#ffffff" opacity="0.8"/>
    <rect x="330" y="24" width="30" height="18" rx="3" fill="#ffffff" opacity="0.8"/>
    <rect x="250" y="48" width="30" height="18" rx="3" fill="#ffffff" opacity="0.6"/>
    <rect x="290" y="48" width="30" height="18" rx="3" fill="#ffffff" opacity="0.6"/>
    <rect x="330" y="48" width="30" height="18" rx="3" fill="#ffffff" opacity="0.6"/>
  `,
  bank: `
    <rect x="30" y="10" width="18" height="70" fill="#ffffff" opacity="0.7"/>
    <rect x="352" y="10" width="18" height="70" fill="#ffffff" opacity="0.7"/>
    <rect x="150" y="30" width="100" height="50" rx="4" fill="#ffffff" opacity="0.85"/>
    <rect x="160" y="38" width="30" height="20" rx="2" fill="#cfe0bd"/>
    <rect x="210" y="38" width="30" height="20" rx="2" fill="#cfe0bd"/>
    <circle cx="90" cy="196" r="16" fill="#7e9c6f" opacity="0.6"/>
    <rect x="86" y="196" width="8" height="24" fill="#6b4a37"/>
  `,
};

function gridLines(x, y, w, h, step) {
  let lines = "";
  for (let gx = x; gx <= x + w; gx += step) {
    lines += `<line x1="${gx}" y1="${y}" x2="${gx}" y2="${y + h}" stroke="rgba(255,255,255,0.5)" stroke-width="2"/>`;
  }
  for (let gy = y; gy <= y + h; gy += step) {
    lines += `<line x1="${x}" y1="${gy}" x2="${x + w}" y2="${gy}" stroke="rgba(255,255,255,0.5)" stroke-width="2"/>`;
  }
  return lines;
}

function checker(x, y, w, h, cell) {
  let squares = "";
  let row = 0;
  for (let cy = y; cy < y + h; cy += cell, row++) {
    let col = 0;
    for (let cx = x; cx < x + w; cx += cell, col++) {
      if ((row + col) % 2 === 0) {
        squares += `<rect x="${cx}" y="${cy}" width="${cell}" height="${cell}" fill="rgba(255,255,255,0.4)"/>`;
      }
    }
  }
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="none" stroke="#ffffff" stroke-width="4"/>` + squares;
}

function roomBackgroundSVG(theme) {
  const colors = ROOM_COLORS[theme] || ROOM_COLORS.home;
  const decor = ROOM_DECOR[theme] || "";
  return sceneShell(colors.wall, colors.floor, decor);
}
