// 망고월드 - 게임 데이터 (장소 / 아이템 / 메뉴)

const LOCATIONS = [
  {
    id: "home",
    name: "우리집",
    emoji: "🏠",
    theme: "home",
    desc: "당정동 행복빌라 302호, 우리 가족의 아늑한 우리집이에요.",
    hasWardrobe: true,
  },
  {
    id: "school",
    name: "당정초등학교",
    emoji: "🏫",
    theme: "school",
    desc: "당정초등학교 2학년 3반 교실이에요. 여기서 공부하고 놀아요.",
  },
  {
    id: "cafe",
    name: "무인카페",
    emoji: "☕",
    theme: "cafe",
    desc: "셀프 키오스크로 주문하는 당정동 무인카페예요.",
    menu: true,
  },
  {
    id: "taekwondo",
    name: "태권도학원",
    emoji: "🥋",
    theme: "taekwondo",
    desc: "얍! 기합소리 가득한 군포 태권도학원이에요.",
    activity: "taekwondo",
  },
  {
    id: "playground",
    name: "놀이터",
    emoji: "🛝",
    theme: "playground",
    desc: "친구들과 뛰노는 아파트 단지 놀이터예요.",
    activity: "playground",
  },
  {
    id: "piano",
    name: "피아노학원",
    emoji: "🎹",
    theme: "piano",
    desc: "도레미파솔라시도~ 피아노학원이에요.",
    activity: "piano",
  },
  {
    id: "restaurant",
    name: "음식점",
    emoji: "🍽️",
    theme: "restaurant",
    desc: "떡볶이부터 라면까지! 군포 분식&식당이에요.",
    menu: true,
  },
  {
    id: "stationery",
    name: "문구야놀자",
    emoji: "✏️",
    theme: "stationery",
    desc: "갖고싶은 문구가 가득한 동네 문구점이에요.",
  },
  {
    id: "mart",
    name: "마트",
    emoji: "🛒",
    theme: "mart",
    desc: "필요한 건 다 있는 군포 동네 마트예요.",
    menu: true,
  },
  {
    id: "bank",
    name: "은행",
    emoji: "🏦",
    theme: "bank",
    desc: "용돈을 저금하고 찾을 수 있는 군포 은행이에요.",
    activity: "bank",
  },
];

// 장소별 꾸미기 아이템 (room 값이 배치되는 장소, starter:true 면 처음부터 소유)
// 각 장소 배경 사진에 이미 가구가 그려져 있어서, 아이템은 그 위에 더 놓는
// "소품/스티커" 개념으로 구성했어요 (침대·책상 같은 큰 가구는 배경에 이미 있음).
const ITEMS = [
  // ---- 우리집 (핑크 거실: 소파·테이블·선반·러그가 이미 있음) ----
  { id: "home_teddy", name: "곰인형", emoji: "🧸", price: 0, room: "home", starter: true },
  { id: "home_balloon", name: "풍선", emoji: "🎈", price: 150, room: "home" },
  { id: "home_photo", name: "액자", emoji: "🖼️", price: 200, room: "home" },
  { id: "home_flower", name: "꽃다발", emoji: "💐", price: 150, room: "home" },
  { id: "home_candle", name: "캔들", emoji: "🕯️", price: 120, room: "home" },
  { id: "home_cat", name: "고양이", emoji: "🐱", price: 300, room: "home" },

  // ---- 당정초등학교 (파란 교실: 책상·칠판·책장·지구본이 이미 있음) ----
  { id: "school_bag", name: "책가방", emoji: "🎒", price: 0, room: "school", starter: true },
  { id: "school_pencil", name: "연필", emoji: "✏️", price: 100, room: "school" },
  { id: "school_apple", name: "사과", emoji: "🍎", price: 120, room: "school" },
  { id: "school_trophy", name: "트로피", emoji: "🏆", price: 250, room: "school" },
  { id: "school_star", name: "칭찬 스티커", emoji: "⭐", price: 90, room: "school" },
  { id: "school_ruler", name: "각도기", emoji: "📐", price: 90, room: "school" },

  // ---- 무인카페 (베이지 카페: 진열대·커피머신·테이블이 이미 있음) ----
  { id: "cafe_cup", name: "커피잔", emoji: "☕", price: 0, room: "cafe", starter: true },
  { id: "cafe_cupcake", name: "컵케이크", emoji: "🧁", price: 150, room: "cafe" },
  { id: "cafe_cookie", name: "쿠키", emoji: "🍪", price: 120, room: "cafe" },
  { id: "cafe_flower", name: "꽃병", emoji: "🌷", price: 100, room: "cafe" },
  { id: "cafe_candle", name: "캔들", emoji: "🕯️", price: 100, room: "cafe" },
  { id: "cafe_art", name: "벽 그림", emoji: "🎨", price: 200, room: "cafe" },

  // ---- 태권도학원 (초록 도장: 샌드백·도복·매트가 이미 있음) ----
  { id: "tkd_dobok", name: "도복", emoji: "🥋", price: 0, room: "taekwondo", starter: true },
  { id: "tkd_trophy", name: "트로피", emoji: "🏆", price: 250, room: "taekwondo" },
  { id: "tkd_medal", name: "메달", emoji: "🏅", price: 200, room: "taekwondo" },
  { id: "tkd_glove", name: "글러브", emoji: "🥊", price: 150, room: "taekwondo" },
  { id: "tkd_star", name: "승급 스티커", emoji: "⭐", price: 90, room: "taekwondo" },

  // ---- 놀이터 (노을 지는 공원: 미끄럼틀·그네·모래놀이터가 이미 있음) ----
  { id: "pg_ball", name: "공", emoji: "⚽", price: 0, room: "playground", starter: true },
  { id: "pg_kite", name: "연", emoji: "🪁", price: 150, room: "playground" },
  { id: "pg_flower", name: "들꽃", emoji: "🌼", price: 90, room: "playground" },
  { id: "pg_butterfly", name: "나비", emoji: "🦋", price: 100, room: "playground" },
  { id: "pg_picnic", name: "돗자리 바구니", emoji: "🧺", price: 180, room: "playground" },
  { id: "pg_watermelon", name: "수박", emoji: "🍉", price: 120, room: "playground" },

  // ---- 피아노학원 (보라 음악실: 그랜드피아노·악보대·책장이 이미 있음) ----
  { id: "piano_note", name: "음표 스티커", emoji: "🎵", price: 0, room: "piano", starter: true },
  { id: "piano_medal", name: "연주 메달", emoji: "🏅", price: 200, room: "piano" },
  { id: "piano_flower", name: "꽃병", emoji: "🌸", price: 100, room: "piano" },
  { id: "piano_teddy", name: "곰인형", emoji: "🧸", price: 180, room: "piano" },
  { id: "piano_candle", name: "캔들", emoji: "🕯️", price: 100, room: "piano" },

  // ---- 음식점 (빨간 피자&버거집: 화덕·진열대·테이블이 이미 있음) ----
  { id: "rest_pizza", name: "피자", emoji: "🍕", price: 0, room: "restaurant", starter: true },
  { id: "rest_burger", name: "버거", emoji: "🍔", price: 150, room: "restaurant" },
  { id: "rest_fries", name: "감자튀김", emoji: "🍟", price: 100, room: "restaurant" },
  { id: "rest_drink", name: "음료", emoji: "🥤", price: 100, room: "restaurant" },
  { id: "rest_candle", name: "캔들", emoji: "🕯️", price: 100, room: "restaurant" },
  { id: "rest_plant", name: "화분", emoji: "🌿", price: 120, room: "restaurant" },

  // ---- 문구야놀자 (민트 문구점: 진열대에 문구가 이미 가득함) ----
  { id: "st_mascot", name: "마스코트 인형", emoji: "🧸", price: 0, room: "stationery", starter: true },
  { id: "st_ribbon", name: "리본", emoji: "🎀", price: 100, room: "stationery" },
  { id: "st_star", name: "반짝 스티커", emoji: "⭐", price: 90, room: "stationery" },

  // ---- 문구야놀자에서 사서 집에 꾸미는 아이템 ----
  { id: "st_pencil", name: "캐릭터 연필", emoji: "✏️", price: 300, room: "home", shopAt: "stationery" },
  { id: "st_sticker", name: "반짝 스티커", emoji: "🌟", price: 200, room: "home", shopAt: "stationery" },
  { id: "st_diary", name: "다이어리", emoji: "📔", price: 800, room: "home", shopAt: "stationery" },
  { id: "st_gelpen", name: "젤펜 세트", emoji: "🖊️", price: 500, room: "home", shopAt: "stationery" },
  { id: "st_case", name: "필통", emoji: "🧰", price: 600, room: "home", shopAt: "stationery" },
  { id: "st_eraser", name: "캐릭터 지우개", emoji: "🧽", price: 150, room: "home", shopAt: "stationery" },

  // ---- 마트 (보라 마트: 진열대·과일·카트가 이미 있음) ----
  { id: "mart_cart", name: "카트", emoji: "🛒", price: 0, room: "mart", starter: true },
  { id: "mart_basket", name: "장바구니", emoji: "🧺", price: 100, room: "mart" },
  { id: "mart_apple", name: "사과", emoji: "🍎", price: 90, room: "mart" },

  // ---- 마트에서 사서 집에 두는 생필품/장난감 ----
  { id: "mart_tissue", name: "휴지", emoji: "🧻", price: 200, room: "home", shopAt: "mart" },
  { id: "mart_toy", name: "장난감 자동차", emoji: "🚗", price: 700, room: "home", shopAt: "mart" },
  { id: "mart_basket_home", name: "과일 바구니", emoji: "🧺", price: 400, room: "home", shopAt: "mart" },
  { id: "mart_balloon", name: "풍선", emoji: "🎈", price: 300, room: "home", shopAt: "mart" },

  // ---- 은행 (초록&골드 은행: 금고·창구가 이미 있음) ----
  { id: "bank_bag", name: "돈주머니", emoji: "💰", price: 0, room: "bank", starter: true },
  { id: "bank_coin", name: "금화", emoji: "🪙", price: 100, room: "bank" },
  { id: "bank_medal", name: "저축왕 메달", emoji: "🏅", price: 200, room: "bank" },
  { id: "bank_plant", name: "화분", emoji: "🌿", price: 120, room: "bank" },
];

// 카페 / 음식점 메뉴 (소모성 - 사서 바로 먹기)
const MENUS = {
  cafe: [
    { id: "m_ade", name: "망고에이드", emoji: "🥤", price: 2500 },
    { id: "m_latte", name: "딸기라떼", emoji: "🍓", price: 3000 },
    { id: "m_cookie", name: "초코쿠키", emoji: "🍪", price: 1500 },
    { id: "m_macaron", name: "마카롱", emoji: "🧁", price: 2000 },
  ],
  restaurant: [
    { id: "f_tteok", name: "떡볶이", emoji: "🌶️", price: 3000 },
    { id: "f_gimbap", name: "김밥", emoji: "🍙", price: 3500 },
    { id: "f_ramen", name: "라면", emoji: "🍜", price: 4000 },
    { id: "f_eomuk", name: "어묵꼬치", emoji: "🍢", price: 1500 },
    { id: "f_sundae", name: "순대", emoji: "🍖", price: 3000 },
  ],
  mart: [
    { id: "g_snack", name: "과자", emoji: "🍿", price: 1500 },
    { id: "g_icecream", name: "아이스크림", emoji: "🍦", price: 2000 },
    { id: "g_milk", name: "딸기우유", emoji: "🥛", price: 1200 },
    { id: "g_fruit", name: "과일 한 봉지", emoji: "🍎", price: 2500 },
  ],
};

// 캐릭터 커스터마이징 옵션
const CHARACTER_OPTIONS = {
  hair: ["#3b2313", "#6b3f1d", "#1c1c1c", "#a45c2e", "#7a4a9a", "#d67ba0"],
  outfit: ["#ff8fab", "#ffb703", "#8ecae6", "#8ac926", "#ffafcc", "#bde0fe"],
};

const TAEKWONDO_BELTS = ["흰띠", "노란띠", "초록띠", "파란띠", "빨간띠", "검은띠"];
