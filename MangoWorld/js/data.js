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
const ITEMS = [
  // ---- 우리집 ----
  { id: "home_bed", name: "침대", emoji: "🛏️", price: 0, room: "home", starter: true },
  { id: "home_desk", name: "책상", emoji: "📚", price: 0, room: "home", starter: true },
  { id: "home_wardrobe", name: "옷장", emoji: "🚪", price: 300, room: "home" },
  { id: "home_plant", name: "화분", emoji: "🪴", price: 150, room: "home" },
  { id: "home_teddy", name: "곰인형", emoji: "🧸", price: 200, room: "home" },
  { id: "home_lamp", name: "스탠드", emoji: "💡", price: 120, room: "home" },
  { id: "home_rug", name: "러그", emoji: "🟧", price: 100, room: "home" },
  { id: "home_bookshelf", name: "책장", emoji: "📖", price: 250, room: "home" },

  // ---- 당정초등학교 ----
  { id: "school_desk", name: "책상", emoji: "🪑", price: 0, room: "school", starter: true },
  { id: "school_locker", name: "사물함", emoji: "🗄️", price: 0, room: "school", starter: true },
  { id: "school_blackboard", name: "칠판 꾸미기", emoji: "🖍️", price: 200, room: "school" },
  { id: "school_timetable", name: "시간표", emoji: "📅", price: 100, room: "school" },
  { id: "school_bag", name: "책가방", emoji: "🎒", price: 180, room: "school" },
  { id: "school_plant", name: "교실 화분", emoji: "🌱", price: 90, room: "school" },

  // ---- 무인카페 (꾸미기) ----
  { id: "cafe_table", name: "테이블", emoji: "🪑", price: 0, room: "cafe", starter: true },
  { id: "cafe_kiosk", name: "키오스크", emoji: "🖥️", price: 0, room: "cafe", starter: true },
  { id: "cafe_plant", name: "화분", emoji: "🪴", price: 100, room: "cafe" },
  { id: "cafe_sofa", name: "소파", emoji: "🛋️", price: 250, room: "cafe" },
  { id: "cafe_board", name: "메뉴판", emoji: "📋", price: 120, room: "cafe" },

  // ---- 태권도학원 (꾸미기) ----
  { id: "tkd_mat", name: "매트", emoji: "🟦", price: 0, room: "taekwondo", starter: true },
  { id: "tkd_trophy", name: "트로피", emoji: "🏆", price: 300, room: "taekwondo" },
  { id: "tkd_beltrack", name: "띠걸이", emoji: "🎽", price: 150, room: "taekwondo" },
  { id: "tkd_mirror", name: "거울", emoji: "🪞", price: 200, room: "taekwondo" },
  { id: "tkd_flag", name: "태극기", emoji: "🚩", price: 100, room: "taekwondo" },

  // ---- 놀이터 (꾸미기) ----
  { id: "pg_slide", name: "미끄럼틀", emoji: "🛝", price: 0, room: "playground", starter: true },
  { id: "pg_swing", name: "그네", emoji: "🎠", price: 0, room: "playground", starter: true },
  { id: "pg_seesaw", name: "시소", emoji: "⚖️", price: 150, room: "playground" },
  { id: "pg_bench", name: "벤치", emoji: "🪑", price: 100, room: "playground" },
  { id: "pg_sandbox", name: "모래놀이터", emoji: "🏖️", price: 180, room: "playground" },
  { id: "pg_ball", name: "공", emoji: "⚽", price: 90, room: "playground" },

  // ---- 피아노학원 (꾸미기) ----
  { id: "piano_main", name: "피아노", emoji: "🎹", price: 0, room: "piano", starter: true },
  { id: "piano_bench", name: "의자", emoji: "🪑", price: 0, room: "piano", starter: true },
  { id: "piano_stand", name: "악보대", emoji: "🎼", price: 150, room: "piano" },
  { id: "piano_metronome", name: "메트로놈", emoji: "⏱️", price: 120, room: "piano" },
  { id: "piano_plant", name: "화분", emoji: "🪴", price: 100, room: "piano" },

  // ---- 음식점 (꾸미기) ----
  { id: "rest_table", name: "식탁", emoji: "🍽️", price: 0, room: "restaurant", starter: true },
  { id: "rest_board", name: "메뉴판", emoji: "📋", price: 100, room: "restaurant" },
  { id: "rest_fridge", name: "냉장고", emoji: "🧊", price: 200, room: "restaurant" },
  { id: "rest_chair", name: "의자", emoji: "🪑", price: 80, room: "restaurant" },

  // ---- 문구야놀자에서 사서 집에 꾸미는 아이템 ----
  { id: "st_pencil", name: "캐릭터 연필", emoji: "✏️", price: 300, room: "home", shopAt: "stationery" },
  { id: "st_sticker", name: "반짝 스티커", emoji: "🌟", price: 200, room: "home", shopAt: "stationery" },
  { id: "st_diary", name: "다이어리", emoji: "📔", price: 800, room: "home", shopAt: "stationery" },
  { id: "st_gelpen", name: "젤펜 세트", emoji: "🖊️", price: 500, room: "home", shopAt: "stationery" },
  { id: "st_case", name: "필통", emoji: "🧰", price: 600, room: "home", shopAt: "stationery" },
  { id: "st_eraser", name: "캐릭터 지우개", emoji: "🧽", price: 150, room: "home", shopAt: "stationery" },

  // ---- 마트 (꾸미기) ----
  { id: "mart_cart", name: "카트", emoji: "🛒", price: 0, room: "mart", starter: true },
  { id: "mart_shelf", name: "진열대", emoji: "🗄️", price: 0, room: "mart", starter: true },
  { id: "mart_register", name: "계산대", emoji: "🧾", price: 200, room: "mart" },
  { id: "mart_fridge", name: "냉장 코너", emoji: "🧊", price: 250, room: "mart" },

  // ---- 마트에서 사서 집에 두는 생필품/장난감 ----
  { id: "mart_tissue", name: "휴지", emoji: "🧻", price: 200, room: "home", shopAt: "mart" },
  { id: "mart_toy", name: "장난감 자동차", emoji: "🚗", price: 700, room: "home", shopAt: "mart" },
  { id: "mart_basket", name: "과일 바구니", emoji: "🧺", price: 400, room: "home", shopAt: "mart" },
  { id: "mart_balloon", name: "풍선", emoji: "🎈", price: 300, room: "home", shopAt: "mart" },
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
