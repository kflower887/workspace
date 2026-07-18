// 망고월드 - 게임 데이터 (장소 / 아이템 / 메뉴)

// mapPos: 지도 일러스트(img/map-town.webp) 위 건물 위치/탭 영역 (전부 %)
const LOCATIONS = [
  {
    id: "home",
    name: "우리집",
    emoji: "🏠",
    theme: "home",
    desc: "당정동 행복빌라 302호, 우리 가족의 아늑한 우리집이에요.",
    hasWardrobe: true,
    mapPos: { x: 47.8, y: 23.4, w: 19.1, h: 31.2 },
  },
  {
    id: "school",
    name: "당정초등학교",
    emoji: "🏫",
    theme: "school",
    desc: "당정초등학교 2학년 3반 교실이에요. 여기서 공부하고 놀아요.",
    mapPos: { x: 14.3, y: 32.6, w: 19.1, h: 33.9 },
  },
  {
    id: "cafe",
    name: "무인카페",
    emoji: "☕",
    theme: "cafe",
    desc: "셀프 키오스크로 주문하는 당정동 무인카페예요.",
    menu: true,
    mapPos: { x: 7.0, y: 61.2, w: 17.6, h: 33.9 },
  },
  {
    id: "taekwondo",
    name: "태권도학원",
    emoji: "🥋",
    theme: "taekwondo",
    desc: "얍! 기합소리 가득한 군포 태권도학원이에요.",
    activity: "taekwondo",
    mapPos: { x: 25.4, y: 62.5, w: 19.1, h: 33.9 },
  },
  {
    id: "playground",
    name: "놀이터",
    emoji: "🛝",
    theme: "playground",
    desc: "친구들과 뛰노는 아파트 단지 놀이터예요.",
    activity: "playground",
    mapPos: { x: 13.2, y: 85.3, w: 22.1, h: 31.2 },
  },
  {
    id: "piano",
    name: "피아노학원",
    emoji: "🎹",
    theme: "piano",
    desc: "도레미파솔라시도~ 피아노학원이에요.",
    activity: "piano",
    mapPos: { x: 50.0, y: 86.6, w: 19.1, h: 31.2 },
  },
  {
    id: "restaurant",
    name: "음식점",
    emoji: "🍽️",
    theme: "restaurant",
    desc: "떡볶이부터 라면까지! 군포 분식&식당이에요.",
    menu: true,
    mapPos: { x: 86.0, y: 85.9, w: 19.1, h: 28.6 },
  },
  {
    id: "stationery",
    name: "문구야놀자",
    emoji: "✏️",
    theme: "stationery",
    desc: "갖고싶은 문구가 가득한 동네 문구점이에요.",
    mapPos: { x: 90.1, y: 57.3, w: 19.1, h: 33.9 },
  },
  {
    id: "mart",
    name: "마트",
    emoji: "🛒",
    theme: "mart",
    desc: "필요한 건 다 있는 군포 동네 마트예요.",
    menu: true,
    mapPos: { x: 68.4, y: 31.2, w: 19.1, h: 31.2 },
  },
  {
    id: "bank",
    name: "은행",
    emoji: "🏦",
    theme: "bank",
    desc: "용돈을 저금하고 찾을 수 있는 군포 은행이에요.",
    activity: "bank",
    mapPos: { x: 73.5, y: 57.3, w: 17.6, h: 33.9 },
  },
];

// 장소별 꾸미기 아이템 (room 값이 배치되는 장소, starter:true 면 처음부터 소유)
// 각 장소 배경 사진에 이미 가구가 그려져 있어서, 아이템은 그 위에 더 놓는
// "소품/스티커" 개념으로 구성했어요 (침대·책상 같은 큰 가구는 배경에 이미 있음).
// icon이 있으면 img/items/{icon}.webp 이미지를 쓰고, 없으면 emoji로 대체 표시돼요.
const ITEMS = [
  // ---- 우리집 (핑크 거실: 소파·테이블·선반·러그가 이미 있음) ----
  { id: "home_pillow", name: "쿠션", icon: "home_pillow", emoji: "🛋️", price: 0, room: "home", starter: true },
  { id: "home_house", name: "미니 하우스", icon: "home_house", emoji: "🏠", price: 250, room: "home" },
  { id: "home_milk", name: "우유팩 소품", icon: "home_milk", emoji: "🥛", price: 100, room: "home" },
  { id: "home_armchair", name: "암체어", icon: "home_armchair", emoji: "🛋️", price: 300, room: "home" },
  { id: "home_plant", name: "화분", icon: "home_plant", emoji: "🪴", price: 150, room: "home" },
  { id: "home_toyblock", name: "장난감 블록", icon: "home_toyblock", emoji: "🧊", price: 130, room: "home" },

  // ---- 당정초등학교 (파란 교실: 책상·칠판·책장·지구본이 이미 있음) ----
  { id: "school_backpack", name: "책가방", icon: "school_backpack", emoji: "🎒", price: 0, room: "school", starter: true },
  { id: "school_building", name: "미니 학교 모형", icon: "school_building", emoji: "🏫", price: 250, room: "school" },
  { id: "school_book", name: "책", icon: "school_book", emoji: "📖", price: 100, room: "school" },
  { id: "school_pencilcase", name: "필통", icon: "school_pencilcase", emoji: "🧰", price: 150, room: "school" },
  { id: "school_apple", name: "사과", icon: "school_apple", emoji: "🍎", price: 90, room: "school" },
  { id: "school_pencil", name: "연필", icon: "school_pencil", emoji: "✏️", price: 80, room: "school" },

  // ---- 무인카페 (베이지 카페: 진열대·커피머신·테이블이 이미 있음) ----
  { id: "cafe_espresso", name: "에스프레소", icon: "cafe_espresso", emoji: "☕", price: 0, room: "cafe", starter: true },
  { id: "cafe_croissant", name: "크루아상", icon: "cafe_croissant", emoji: "🥐", price: 150, room: "cafe" },
  { id: "cafe_cake", name: "케이크", icon: "cafe_cake", emoji: "🍰", price: 200, room: "cafe" },
  { id: "cafe_beans", name: "원두 자루", icon: "cafe_beans", emoji: "☕", price: 130, room: "cafe" },
  { id: "cafe_pitcher", name: "우유 피쳐", icon: "cafe_pitcher", emoji: "🥛", price: 120, room: "cafe" },
  { id: "cafe_portafilter", name: "포터필터", icon: "cafe_portafilter", emoji: "⚙️", price: 140, room: "cafe" },

  // ---- 태권도학원 (초록 도장: 샌드백·도복·매트가 이미 있음) ----
  { id: "tkd_dobok", name: "도복", icon: "tkd_dobok", emoji: "🥋", price: 0, room: "taekwondo", starter: true },
  { id: "tkd_figure", name: "발차기 피규어", icon: "tkd_figure", emoji: "🤾", price: 220, room: "taekwondo" },
  { id: "tkd_pad", name: "격파 패드", icon: "tkd_pad", emoji: "🥊", price: 160, room: "taekwondo" },
  { id: "tkd_trophy", name: "트로피", icon: "tkd_trophy", emoji: "🏆", price: 250, room: "taekwondo" },
  { id: "tkd_medal", name: "메달", icon: "tkd_medal", emoji: "🏅", price: 200, room: "taekwondo" },

  // ---- 놀이터 (노을 지는 공원: 미끄럼틀·그네·모래놀이터가 이미 있음) ----
  { id: "pg_slide", name: "미끄럼틀 모형", icon: "pg_slide", emoji: "🛝", price: 0, room: "playground", starter: true },
  { id: "pg_swing", name: "그네 모형", icon: "pg_swing", emoji: "🎠", price: 150, room: "playground" },
  { id: "pg_balloon", name: "풍선", icon: "pg_balloon", emoji: "🎈", price: 120, room: "playground" },
  { id: "pg_sandbox", name: "모래놀이 세트", icon: "pg_sandbox", emoji: "🏖️", price: 180, room: "playground" },
  { id: "pg_ball", name: "비치볼", icon: "pg_ball", emoji: "⚽", price: 100, room: "playground" },
  { id: "pg_bucket", name: "양동이", icon: "pg_bucket", emoji: "🪣", price: 90, room: "playground" },

  // ---- 피아노학원 (보라 음악실: 그랜드피아노·악보대·책장이 이미 있음) ----
  { id: "piano_model", name: "그랜드피아노 모형", icon: "piano_model", emoji: "🎹", price: 0, room: "piano", starter: true },
  { id: "piano_clef", name: "음자리표 장식", icon: "piano_clef", emoji: "🎼", price: 150, room: "piano" },
  { id: "piano_note1", name: "음표 장식", icon: "piano_note1", emoji: "🎵", price: 100, room: "piano" },
  { id: "piano_headphones", name: "헤드폰", icon: "piano_headphones", emoji: "🎧", price: 180, room: "piano" },
  { id: "piano_mic", name: "마이크", icon: "piano_mic", emoji: "🎤", price: 170, room: "piano" },
  { id: "piano_note2", name: "리듬 음표", icon: "piano_note2", emoji: "🎶", price: 100, room: "piano" },

  // ---- 음식점 (빨간 피자&버거집: 화덕·진열대·테이블이 이미 있음) ----
  { id: "rest_pizza", name: "피자", icon: "rest_pizza", emoji: "🍕", price: 0, room: "restaurant", starter: true },
  { id: "rest_burger", name: "버거", icon: "rest_burger", emoji: "🍔", price: 150, room: "restaurant" },
  { id: "rest_sushi", name: "스시롤", icon: "rest_sushi", emoji: "🍣", price: 140, room: "restaurant" },
  { id: "rest_ramen", name: "라멘 모형", icon: "rest_ramen", emoji: "🍜", price: 160, room: "restaurant" },
  { id: "rest_drink", name: "음료컵", icon: "rest_drink", emoji: "🥤", price: 100, room: "restaurant" },

  // ---- 문구야놀자 (민트 문구점: 진열대에 문구가 이미 가득함) ----
  { id: "stx_pencil", name: "연필", icon: "stx_pencil", emoji: "✏️", price: 0, room: "stationery", starter: true },
  { id: "stx_eraser", name: "지우개", icon: "stx_eraser", emoji: "🧽", price: 90, room: "stationery" },
  { id: "stx_crayon", name: "크레파스", icon: "stx_crayon", emoji: "🖍️", price: 150, room: "stationery" },
  { id: "stx_notebook", name: "공책", icon: "stx_notebook", emoji: "📓", price: 180, room: "stationery" },
  { id: "stx_ruler", name: "자", icon: "stx_ruler", emoji: "📏", price: 100, room: "stationery" },
  { id: "stx_scissors", name: "가위", icon: "stx_scissors", emoji: "✂️", price: 120, room: "stationery" },
  { id: "stx_pouch", name: "필통", icon: "stx_pouch", emoji: "🧳", price: 200, room: "stationery" },
  { id: "stx_slime_strawberry", name: "딸기 슬라임", icon: "stx_slime_strawberry", emoji: "🍓", price: 280, room: "stationery" },
  { id: "stx_slime_cloud", name: "구름 슬라임", icon: "stx_slime_cloud", emoji: "☁️", price: 280, room: "stationery" },
  { id: "stx_slime_coffee", name: "커피 슬라임", icon: "stx_slime_coffee", emoji: "🍫", price: 280, room: "stationery" },
  { id: "stx_slime_orange", name: "귤 슬라임", icon: "stx_slime_orange", emoji: "🍊", price: 280, room: "stationery" },

  // ---- 문구야놀자에서 사서 집에 꾸미는 아이템 ----
  { id: "st_pencil", name: "캐릭터 연필", emoji: "✏️", price: 300, room: "home", shopAt: "stationery" },
  { id: "st_sticker", name: "반짝 스티커", emoji: "🌟", price: 200, room: "home", shopAt: "stationery" },
  { id: "st_diary", name: "다이어리", emoji: "📔", price: 800, room: "home", shopAt: "stationery" },
  { id: "st_gelpen", name: "젤펜 세트", emoji: "🖊️", price: 500, room: "home", shopAt: "stationery" },
  { id: "st_case", name: "필통", emoji: "🧰", price: 600, room: "home", shopAt: "stationery" },
  { id: "st_eraser", name: "캐릭터 지우개", emoji: "🧽", price: 150, room: "home", shopAt: "stationery" },

  // ---- 마트 (보라 마트: 진열대·과일·카트가 이미 있음) ----
  { id: "mart_cart", name: "카트", icon: "mart_cart", emoji: "🛒", price: 0, room: "mart", starter: true },
  { id: "mart_milk", name: "우유", icon: "mart_milk", emoji: "🥛", price: 100, room: "mart" },
  { id: "mart_apple", name: "사과", icon: "mart_apple", emoji: "🍎", price: 90, room: "mart" },
  { id: "mart_bread", name: "빵", icon: "mart_bread", emoji: "🍞", price: 110, room: "mart" },
  { id: "mart_bag", name: "장바구니", icon: "mart_bag", emoji: "🛍️", price: 130, room: "mart" },
  { id: "mart_fridge", name: "냉장 진열대", icon: "mart_fridge", emoji: "🧊", price: 220, room: "mart" },

  // ---- 마트에서 사서 집에 두는 생필품/장난감 ----
  { id: "mart_tissue", name: "휴지", emoji: "🧻", price: 200, room: "home", shopAt: "mart" },
  { id: "mart_toy", name: "장난감 자동차", emoji: "🚗", price: 700, room: "home", shopAt: "mart" },
  { id: "mart_basket_home", name: "과일 바구니", emoji: "🧺", price: 400, room: "home", shopAt: "mart" },
  { id: "mart_balloon", name: "풍선", emoji: "🎈", price: 300, room: "home", shopAt: "mart" },

  // ---- 은행 (초록&골드 은행: 금고·창구가 이미 있음) ----
  { id: "bank_cash", name: "현금 다발", icon: "bank_cash", emoji: "💵", price: 0, room: "bank", starter: true },
  { id: "bank_coins", name: "금화", icon: "bank_coins", emoji: "🪙", price: 100, room: "bank" },
  { id: "bank_chest", name: "보물상자", icon: "bank_chest", emoji: "🧰", price: 250, room: "bank" },
  { id: "bank_card", name: "카드", icon: "bank_card", emoji: "💳", price: 150, room: "bank" },
  { id: "bank_piggy", name: "저금통", icon: "bank_piggy", emoji: "🐷", price: 200, room: "bank" },
  { id: "bank_moneybag", name: "돈주머니", icon: "bank_moneybag", emoji: "💰", price: 130, room: "bank" },
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

// 캐릭터 프리셋 (여자아이 5명 + 남자아이 5명, img/characters/{id}.webp)
const CHARACTER_PRESETS = [
  { id: "girl1", gender: "girl", label: "리본 원피스" },
  { id: "girl2", gender: "girl", label: "웨이브 단발" },
  { id: "girl3", gender: "girl", label: "양갈래 후드" },
  { id: "girl4", gender: "girl", label: "단발머리" },
  { id: "girl5", gender: "girl", label: "멜빵 원피스" },
  { id: "boy1", gender: "boy", label: "멜빵바지" },
  { id: "boy2", gender: "boy", label: "볼캡 스트라이프" },
  { id: "boy3", gender: "boy", label: "후드티" },
  { id: "boy4", gender: "boy", label: "점퍼룩" },
  { id: "boy5", gender: "boy", label: "교복 조끼" },
];

const TAEKWONDO_BELTS = ["흰띠", "노란띠", "초록띠", "파란띠", "빨간띠", "검은띠"];

// 캐릭터 꾸미기 액세서리 (이모지 오버레이)
const ACCESSORY_OPTIONS = [
  { id: "ribbon", emoji: "🎀", label: "리본" },
  { id: "crown", emoji: "👑", label: "왕관" },
  { id: "glasses", emoji: "😎", label: "선글라스" },
  { id: "cap", emoji: "🧢", label: "모자" },
  { id: "flower", emoji: "🌸", label: "꽃" },
];

// 우리집 집안일 알바 (청소 / 빨래 / 공부)
const CHORES = [
  { id: "clean", label: "청소하기", emoji: "🧹", reward: 35 },
  { id: "laundry", label: "빨래 개기", emoji: "🧺", reward: 35 },
  { id: "study", label: "공부하기", emoji: "📖", reward: 40 },
];

// 다른 장소의 알바하기 (장소마다 다른 컨셉, 하루 5회 제한)
const JOBS = {
  school: { label: "숙제 도와주기 알바", emoji: "📚", reward: 40 },
  cafe: { label: "카페 홀서빙 알바", emoji: "☕", reward: 45 },
  restaurant: { label: "서빙 알바", emoji: "🍽️", reward: 45 },
  stationery: { label: "문구점 정리 알바", emoji: "✏️", reward: 40 },
  mart: { label: "마트 진열 알바", emoji: "🛒", reward: 40 },
  playground: { label: "동생 돌보기 알바", emoji: "🧒", reward: 35 },
};
