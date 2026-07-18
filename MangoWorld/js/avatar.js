// 망고월드 - 캐릭터 아바타 (SVG 생성, 애니메이션 훅 포함)

function shiftColor(hex, amt) {
  const num = parseInt(hex.replace("#", ""), 16);
  let r = (num >> 16) + amt;
  let g = ((num >> 8) & 0x00ff) + amt;
  let b = (num & 0x0000ff) + amt;
  r = Math.max(Math.min(255, r), 0);
  g = Math.max(Math.min(255, g), 0);
  b = Math.max(Math.min(255, b), 0);
  return "#" + (0x1000000 + r * 0x10000 + g * 0x100 + b).toString(16).slice(1);
}

function renderAvatarSVG(character, size) {
  const s = size || 64;
  if (!character) return renderPlaceholderAvatarSVG(s);

  const hair = character.hair;
  const outfit = character.outfit;
  const isBoy = character.gender === "boy";
  const gid = "g" + Math.random().toString(36).slice(2, 9);

  const hairShape = isBoy
    ? `<path d="M50 15 C33 15 25 27 26 41 C26 46 27 50 29 53 L29 41 C29 30 38 23 50 23 C62 23 71 30 71 41 L71 53 C73 50 74 46 74 41 C75 27 67 15 50 15 Z" fill="url(#${gid}-hair)" />`
    : `<path d="M50 14 C30 14 22 30 24 48 C26 46 29 44 30 40 C31 50 33 54 36 57
             C34 46 35 38 38 33 C40 40 40 46 42 50
             C41 40 43 32 47 28 C47 38 49 44 51 48
             C51 38 53 32 56 29 C55 39 57 46 60 52
             C62 47 63 42 64 38 C66 42 68 46 70 48
             C73 30 66 14 50 14 Z" fill="url(#${gid}-hair)" />
       <circle cx="22" cy="46" r="4.5" fill="url(#${gid}-hair)" />
       <circle cx="78" cy="46" r="4.5" fill="url(#${gid}-hair)" />
       <path d="M17 42 L23 46 L17 50 Z" fill="#ff5d8f" />
       <path d="M83 42 L77 46 L83 50 Z" fill="#ff5d8f" />`;

  return `
  <svg class="avatar-svg" viewBox="0 0 100 100" width="${s}" height="${s}" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="${gid}-skin" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#ffe8cf" />
        <stop offset="100%" stop-color="#ffd9b3" />
      </linearGradient>
      <linearGradient id="${gid}-outfit" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="${shiftColor(outfit, 40)}" />
        <stop offset="100%" stop-color="${outfit}" />
      </linearGradient>
      <linearGradient id="${gid}-hair" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="${shiftColor(hair, 30)}" />
        <stop offset="100%" stop-color="${hair}" />
      </linearGradient>
    </defs>

    <!-- 그림자 -->
    <ellipse cx="50" cy="95" rx="20" ry="3.5" fill="rgba(60,30,10,0.15)" />

    <!-- 발 -->
    <g class="avatar-foot avatar-foot-l"><rect x="34" y="82" width="10" height="11" rx="4" fill="#6b4a37" /></g>
    <g class="avatar-foot avatar-foot-r"><rect x="56" y="82" width="10" height="11" rx="4" fill="#6b4a37" /></g>

    <!-- 몸 / 옷 -->
    <path class="avatar-body" d="M28 92 Q28 62 50 62 Q72 62 72 92 Z" fill="url(#${gid}-outfit)" />
    <circle cx="38" cy="80" r="4" fill="url(#${gid}-outfit)" />
    <circle cx="62" cy="80" r="4" fill="url(#${gid}-outfit)" />

    <!-- 팔 -->
    <ellipse class="avatar-arm avatar-arm-l" cx="26" cy="73" rx="6" ry="12" fill="url(#${gid}-outfit)" transform="rotate(-12 26 73)" />
    <ellipse class="avatar-arm avatar-arm-r" cx="74" cy="73" rx="6" ry="12" fill="url(#${gid}-outfit)" transform="rotate(12 74 73)" />
    <circle cx="24" cy="83" r="4.6" fill="url(#${gid}-skin)" />
    <circle cx="76" cy="83" r="4.6" fill="url(#${gid}-skin)" />

    <!-- 목 -->
    <rect x="45" y="50" width="10" height="10" fill="url(#${gid}-skin)" />

    <!-- 얼굴 -->
    <circle class="avatar-head" cx="50" cy="38" r="22" fill="url(#${gid}-skin)" />

    <!-- 볼터치 -->
    <circle cx="36" cy="44" r="3.5" fill="#ff9eb0" opacity="0.7" />
    <circle cx="64" cy="44" r="3.5" fill="#ff9eb0" opacity="0.7" />

    <!-- 눈썹 -->
    <path d="M35 29 Q38.5 26 43.5 28" stroke="#5c3a22" stroke-width="1.8" fill="none" stroke-linecap="round" />
    <path d="M65 29 Q61.5 26 56.5 28" stroke="#5c3a22" stroke-width="1.8" fill="none" stroke-linecap="round" />

    <!-- 눈 (깜빡임 애니메이션 대상) -->
    <g class="avatar-eyes">
      <circle cx="41" cy="37" r="2.6" fill="#2b2b2b" />
      <circle cx="59" cy="37" r="2.6" fill="#2b2b2b" />
    </g>

    <!-- 웃는 입 -->
    <path d="M43 46 Q50 52 57 46" stroke="#c1543f" stroke-width="2.2" fill="none" stroke-linecap="round" />

    <!-- 머리카락 -->
    <g class="avatar-hair">${hairShape}</g>
  </svg>`;
}

function renderPlaceholderAvatarSVG(size) {
  const s = size || 64;
  return `
  <svg class="avatar-svg avatar-placeholder" viewBox="0 0 100 100" width="${s}" height="${s}" xmlns="http://www.w3.org/2000/svg">
    <circle cx="50" cy="50" r="44" fill="none" stroke="#d8c6a3" stroke-width="4" stroke-dasharray="8 6" />
    <text x="50" y="64" font-size="42" text-anchor="middle" fill="#d8c6a3">?</text>
  </svg>`;
}

function mountAvatar(el, character, size) {
  if (!el) return;
  el.innerHTML = renderAvatarSVG(character, size);
}
