// 망고월드 - 김하린 캐릭터 아바타 (SVG 생성)

function renderAvatarSVG(character, size) {
  const hair = character.hair;
  const outfit = character.outfit;
  const s = size || 64;
  return `
  <svg viewBox="0 0 100 100" width="${s}" height="${s}" xmlns="http://www.w3.org/2000/svg">
    <!-- 몸 / 옷 -->
    <path d="M28 92 Q28 62 50 62 Q72 62 72 92 Z" fill="${outfit}" />
    <circle cx="38" cy="80" r="4" fill="${outfit}" />
    <circle cx="62" cy="80" r="4" fill="${outfit}" />

    <!-- 목 -->
    <rect x="45" y="50" width="10" height="10" fill="#ffd9b3" />

    <!-- 얼굴 -->
    <circle cx="50" cy="38" r="22" fill="#ffd9b3" />

    <!-- 볼터치 -->
    <circle cx="36" cy="44" r="3.5" fill="#ff9eb0" opacity="0.7" />
    <circle cx="64" cy="44" r="3.5" fill="#ff9eb0" opacity="0.7" />

    <!-- 눈 -->
    <circle cx="41" cy="37" r="2.6" fill="#2b2b2b" />
    <circle cx="59" cy="37" r="2.6" fill="#2b2b2b" />

    <!-- 웃는 입 -->
    <path d="M43 46 Q50 52 57 46" stroke="#c1543f" stroke-width="2.2" fill="none" stroke-linecap="round" />

    <!-- 머리카락 (뒤) -->
    <path d="M50 14 C30 14 22 30 24 48 C26 46 29 44 30 40 C31 50 33 54 36 57
             C34 46 35 38 38 33 C40 40 40 46 42 50
             C41 40 43 32 47 28 C47 38 49 44 51 48
             C51 38 53 32 56 29 C55 39 57 46 60 52
             C62 47 63 42 64 38 C66 42 68 46 70 48
             C73 30 66 14 50 14 Z" fill="${hair}" />

    <!-- 양갈래 머리 리본 -->
    <circle cx="22" cy="46" r="4.5" fill="${hair}" />
    <circle cx="78" cy="46" r="4.5" fill="${hair}" />
    <path d="M17 42 L23 46 L17 50 Z" fill="#ff5d8f" />
    <path d="M83 42 L77 46 L83 50 Z" fill="#ff5d8f" />
  </svg>`;
}

function mountAvatar(el, character, size) {
  if (!el) return;
  el.innerHTML = renderAvatarSVG(character, size);
}
