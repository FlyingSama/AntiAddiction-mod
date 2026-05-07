/**
 * 防沉迷系统后台服务
 * ==================
 * 功能：
 *   - 节假日管理（增删改查）
 *   - 游玩时段自定义（可按用户配置时间窗口）
 *   - 玩家登录日志查询
 *   - 静态管理面板（/public/index.html）
 *   - REST API 供 Mod 客户端调用
 *
 * 默认端口: 3000
 * 运行: node server.js
 *
 * 管理员默认账号: admin / admin123  （首次启动自动创建，请及时修改）
 */

'use strict';

const express       = require('express');
const session       = require('express-session');
const cors          = require('cors');
const morgan        = require('morgan');
const bcrypt        = require('bcryptjs');
const Database      = require('better-sqlite3');
const path          = require('path');
const fs            = require('fs');
const https         = require('https');
const iconv         = require('iconv-lite');

// ─── 初始化数据库 ─────────────────────────────────────────────────────────────
const DB_PATH = path.join(__dirname, 'antiaddiction.db');
const db      = new Database(DB_PATH);

db.pragma('journal_mode = WAL');
db.exec(`
  CREATE TABLE IF NOT EXISTS holidays (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    date     TEXT UNIQUE NOT NULL,   -- YYYY-MM-DD
    label    TEXT DEFAULT '',        -- 节日名称，如"元旦"
    created  INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS time_rules (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_name    TEXT NOT NULL,      -- 规则名称
    days_of_week TEXT NOT NULL,      -- 逗号分隔的星期数 (1=Sun,2=Mon...7=Sat)
    start_hour   INTEGER NOT NULL,   -- 开始小时 (0-23)
    end_hour     INTEGER NOT NULL,   -- 结束小时 (0-23)
    enabled      INTEGER DEFAULT 1,
    created      INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS date_rules (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    date         TEXT UNIQUE NOT NULL,  -- YYYY-MM-DD
    label        TEXT DEFAULT '',       -- 规则说明
    start_hour   INTEGER NOT NULL,      -- 开始小时 (0-23)
    end_hour     INTEGER NOT NULL,      -- 结束小时 (0-23)
    max_minutes  INTEGER DEFAULT 0,     -- 可玩时长（分钟），0=不受限
    enabled      INTEGER DEFAULT 1,
    created      INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS sessions_log (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_name TEXT NOT NULL,
    minor     INTEGER DEFAULT 0,
    action    TEXT NOT NULL,
    ip        TEXT DEFAULT '',
    location  TEXT DEFAULT '',
    ts        INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS admins (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    created  INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS users (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT NOT NULL,
    id_card  TEXT NOT NULL,
    created  INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS game_days (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    date                 TEXT UNIQUE NOT NULL,
    playable             INTEGER DEFAULT 1,
    is_holiday           INTEGER DEFAULT 0,
    is_workday_override  INTEGER DEFAULT 0,
    start_hour           INTEGER DEFAULT 19,
    end_hour             INTEGER DEFAULT 21,
    max_minutes          INTEGER DEFAULT 0,
    label                TEXT DEFAULT '',
    created              INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS default_config (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
`);

// 兼容旧数据库：安全添加新列
try { db.prepare("ALTER TABLE sessions_log ADD COLUMN ip TEXT DEFAULT ''").run(); } catch(e) {}
try { db.prepare("ALTER TABLE sessions_log ADD COLUMN location TEXT DEFAULT ''").run(); } catch(e) {}
try { db.prepare("ALTER TABLE game_days ADD COLUMN start_minute INTEGER DEFAULT 0").run(); } catch(e) {}
try { db.prepare("ALTER TABLE game_days ADD COLUMN end_minute INTEGER DEFAULT 0").run(); } catch(e) {}

const insertCfg = db.prepare('INSERT OR IGNORE INTO default_config (key, value) VALUES (?, ?)');
insertCfg.run('default_start_hour', '19');
insertCfg.run('default_end_hour', '21');
insertCfg.run('default_start_minute', '0');
insertCfg.run('default_end_minute', '0');

// 插入默认节假日（2025-2026 年）
const defaultHolidays = [
  ['2025-01-01','元旦'],
  ['2025-01-28','春节'],['2025-01-29','春节'],['2025-01-30','春节'],
  ['2025-01-31','春节'],['2025-02-01','春节'],['2025-02-02','春节'],
  ['2025-02-03','春节'],['2025-02-04','春节'],
  ['2025-04-04','清明'],['2025-04-05','清明'],['2025-04-06','清明'],
  ['2025-05-01','劳动节'],['2025-05-02','劳动节'],['2025-05-03','劳动节'],
  ['2025-05-04','劳动节'],['2025-05-05','劳动节'],
  ['2025-05-31','端午'],['2025-06-01','端午'],['2025-06-02','端午'],
  ['2025-10-01','国庆'],['2025-10-02','国庆'],['2025-10-03','国庆'],
  ['2025-10-04','国庆'],['2025-10-05','国庆'],['2025-10-06','国庆'],
  ['2025-10-07','国庆'],['2025-10-08','国庆'],
  ['2026-01-01','元旦'],['2026-01-02','元旦'],['2026-01-03','元旦'],
];
const insertHoliday = db.prepare(
  'INSERT OR IGNORE INTO holidays (date, label) VALUES (?, ?)'
);
const insertMany = db.transaction((rows) => {
  for (const [d, l] of rows) insertHoliday.run(d, l);
});
insertMany(defaultHolidays);

// 插入默认时间规则
const ruleCount = db.prepare('SELECT COUNT(*) as c FROM time_rules').get();
if (ruleCount.c === 0) {
  db.prepare(`
    INSERT INTO time_rules (rule_name, days_of_week, start_hour, end_hour)
    VALUES (?, ?, ?, ?)
  `).run('周末默认规则', '6,7,1', 20, 21); // 6=Fri,7=Sat,1=Sun
}

// 创建默认管理员
const adminCount = db.prepare('SELECT COUNT(*) as c FROM admins').get();
if (adminCount.c === 0) {
  const hash = bcrypt.hashSync('admin123', 10);
  db.prepare('INSERT INTO admins (username, password) VALUES (?, ?)').run('admin', hash);
  console.log('[防沉迷后台] 已创建默认管理员账号: admin / admin123  ← 请尽快修改密码！');
}

// 创建默认用户
const userCount = db.prepare('SELECT COUNT(*) as c FROM users').get();
if (userCount.c === 0) {
  db.prepare('INSERT INTO users (name, id_card) VALUES (?, ?)').run('胡墨凡', '429004201712150350');
  console.log('[防沉迷后台] 已创建默认用户: 胡墨凡');
}

// ─── Express 应用 ──────────────────────────────────────────────────────────────
const app = express();

app.use(cors({ origin: true, credentials: true }));
app.use(morgan('dev'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(session({
  secret: 'aa-secret-change-me-' + Date.now(),
  resave: false,
  saveUninitialized: false,
  cookie: { maxAge: 8 * 3600 * 1000 } // 8 小时
}));

// 静态文件（管理面板）
app.use(express.static(path.join(__dirname, 'public')));

// Demo 演示页面（无需登录）
app.get('/demo', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'demo.html'));
});

// ─── 鉴权中间件 ────────────────────────────────────────────────────────────────
function requireLogin(req, res, next) {
  if (req.session && req.session.admin) return next();
  if (req.path.startsWith('/api/')) return res.status(401).json({ error: '未登录' });
  return res.redirect('/login.html');
}

// ─── 工具函数 ────────────────────────────────────────────────────────────────
function getClientIP(req) {
  return (req.headers['cf-connecting-ip'] ||
          req.headers['x-forwarded-for']?.split(',')[0]?.trim() ||
          req.headers['x-real-ip'] ||
          req.ip).replace(/^::ffff:/, '');
}

function safeDecode(buf) {
  const utf8 = buf.toString('utf8');
  try {
    JSON.parse(utf8);
    return utf8;
  } catch (e) {
    return iconv.decode(buf, 'gbk');
  }
}

function lookupGeo(ip) {
  return new Promise((resolve) => {
    if (!ip || ip === '127.0.0.1' || ip === '::1' || ip.startsWith('192.168.') || ip.startsWith('10.')) {
      return resolve('本地/内网');
    }
    // 主：ipip.net（国内服务商，免费，稳定）
    https.get(`https://freeapi.ipip.net/${ip}`, (resp) => {
      const chunks = [];
      resp.on('data', chunk => chunks.push(chunk));
      resp.on('end', () => {
        try {
          const data = safeDecode(Buffer.concat(chunks));
          const arr = JSON.parse(data);
          if (Array.isArray(arr) && arr.length >= 3) {
            resolve(`${arr[1] || ''} ${arr[2] || ''}`.trim() || ip);
          } else { resolve(ip); }
        } catch (e) { resolve(ip); }
      });
    }).on('error', () => {
      // 备：Pconline 太平洋（国内，免费）
      https.get(`https://whois.pconline.com.cn/ipJson.jsp?ip=${ip}&json=true`, (resp2) => {
        const chunks2 = [];
        resp2.on('data', chunk => chunks2.push(chunk));
        resp2.on('end', () => {
          try {
            const data2 = safeDecode(Buffer.concat(chunks2));
            const j = JSON.parse(data2);
            resolve(`${j.pro || ''} ${j.city || ''}`.trim() || ip);
          } catch (e) { resolve(ip); }
        });
      }).on('error', () => resolve(ip));
    });
  });
}

// ─── 公开 API（Mod 客户端调用，无需鉴权）─────────────────────────────────────

// GET /api/holidays  → 返回节假日日期字符串数组
app.get('/api/holidays', (req, res) => {
  const rows = db.prepare('SELECT date FROM holidays ORDER BY date').all();
  res.json(rows.map(r => r.date));
});

// GET /api/rules  → 返回统一游戏日规则 + 默认时段配置（Mod 客户端调用）
app.get('/api/rules', (req, res) => {
  const ds = db.prepare("SELECT value FROM default_config WHERE key='default_start_hour'").get();
  const de = db.prepare("SELECT value FROM default_config WHERE key='default_end_hour'").get();
  const dsm = db.prepare("SELECT value FROM default_config WHERE key='default_start_minute'").get();
  const dem = db.prepare("SELECT value FROM default_config WHERE key='default_end_minute'").get();
  const default_start_hour   = parseInt(ds ? ds.value : '19');
  const default_end_hour     = parseInt(de ? de.value : '21');
  const default_start_minute = parseInt(dsm ? dsm.value : '0');
  const default_end_minute   = parseInt(dem ? dem.value : '0');
  const rows = db.prepare(`
    SELECT date, playable, is_holiday, is_workday_override,
           start_hour, start_minute, end_hour, end_minute, max_minutes, label
    FROM game_days ORDER BY date
  `).all();
  res.json({ default_start_hour, default_start_minute, default_end_hour, default_end_minute, days: rows });
});

// POST /api/report  → 接收 Mod 上报的会话事件（立即响应，异步查IP位置）
app.post('/api/report', (req, res) => {
  const { userName = 'unknown', minor = false, action = 'session_start' } = req.body;
  const ip = getClientIP(req);
  const info = db.prepare('INSERT INTO sessions_log (user_name, minor, action, ip, location) VALUES (?, ?, ?, ?, ?)')
    .run(String(userName).slice(0, 50), minor ? 1 : 0, String(action).slice(0, 30), ip, '');
  res.json({ ok: true });
  // 异步更新地理位置
  lookupGeo(ip).then(loc => {
    if (loc) db.prepare('UPDATE sessions_log SET location=? WHERE id=?').run(loc, info.lastInsertRowid);
  });
});

// POST /api/report/batch  → 批量上报（本地缓存刷新用）
app.post('/api/report/batch', (req, res) => {
  const entries = req.body;
  if (!Array.isArray(entries)) return res.status(400).json({ error: '需为数组' });
  const ip = getClientIP(req);
  const insert = db.prepare('INSERT INTO sessions_log (user_name, minor, action, ip, location) VALUES (?, ?, ?, ?, ?)');
  const doBatch = db.transaction((items) => {
    for (const e of items) {
      insert.run(String(e.userName || 'unknown').slice(0, 50), e.minor ? 1 : 0,
                 String(e.action || 'game_start').slice(0, 30), ip, '');
    }
  });
  doBatch(entries);
  res.json({ ok: true, count: entries.length });
  // 异步更新地理位置
  lookupGeo(ip).then(loc => {
    if (loc) db.prepare("UPDATE sessions_log SET location=? WHERE ip=? AND location=''").run(loc, ip);
  });
});

// GET /api/users  → 返回用户列表供 Mod 实名认证使用
app.get('/api/users', (req, res) => {
  const rows = db.prepare('SELECT name, id_card FROM users ORDER BY id').all();
  res.json(rows);
});

// ─── 管理员登录 ───────────────────────────────────────────────────────────────

// POST /admin/login
app.post('/admin/login', (req, res) => {
  const { username, password } = req.body;
  const admin = db.prepare('SELECT * FROM admins WHERE username = ?').get(username);
  if (!admin || !bcrypt.compareSync(password, admin.password)) {
    return res.status(401).json({ error: '用户名或密码错误' });
  }
  req.session.admin = { id: admin.id, username: admin.username };
  res.json({ ok: true, username: admin.username });
});

// POST /admin/logout
app.post('/admin/logout', (req, res) => {
  req.session.destroy();
  res.json({ ok: true });
});

// GET /admin/me
app.get('/admin/me', (req, res) => {
  if (req.session && req.session.admin) {
    return res.json({ logged: true, username: req.session.admin.username });
  }
  res.json({ logged: false });
});

// ─── 受保护 API（需要管理员登录）─────────────────────────────────────────────

// ── 节假日管理 ──

// GET  /api/admin/holidays
app.get('/api/admin/holidays', requireLogin, (req, res) => {
  const rows = db.prepare('SELECT * FROM holidays ORDER BY date').all();
  res.json(rows);
});

// POST /api/admin/holidays  → 新增节假日（支持单日或日期范围）
app.post('/api/admin/holidays', requireLogin, (req, res) => {
  const { date, start_date, end_date, label = '' } = req.body;

  // 日期范围模式：start_date ~ end_date
  if (start_date && end_date) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start_date) || !/^\d{4}-\d{2}-\d{2}$/.test(end_date)) {
      return res.status(400).json({ error: '日期格式不正确，需为 YYYY-MM-DD' });
    }
    const start = new Date(start_date);
    const end   = new Date(end_date);
    if (start > end) return res.status(400).json({ error: '开始日期不能晚于结束日期' });

    const insert = db.prepare('INSERT OR IGNORE INTO holidays (date, label) VALUES (?, ?)');
    const insertMany = db.transaction((dates) => {
      for (const d of dates) insert.run(d, label);
    });

    const dates = [];
    const cur = new Date(start);
    while (cur <= end) {
      dates.push(cur.toISOString().slice(0, 10));
      cur.setDate(cur.getDate() + 1);
    }
    insertMany(dates);
    return res.json({ ok: true, count: dates.length });
  }

  // 单日模式（向后兼容）
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).json({ error: '日期格式不正确，需为 YYYY-MM-DD，或提供 start_date+end_date' });
  }
  try {
    db.prepare('INSERT INTO holidays (date, label) VALUES (?, ?)').run(date, label);
    res.json({ ok: true });
  } catch (e) {
    res.status(409).json({ error: '该日期已存在' });
  }
});

// POST /api/admin/holidays/sync  → 从 timor.tech 同步全年节假日
app.post('/api/admin/holidays/sync', requireLogin, (req, res) => {
  const year = req.body.year || new Date().getFullYear();
  const url  = `https://timor.tech/api/holiday/year/${year}`;

  https.get(url, (resp) => {
    let data = '';
    resp.on('data', chunk => data += chunk);
    resp.on('end', () => {
      try {
        const json = JSON.parse(data);
        if (json.code !== 0) {
          return res.status(502).json({ error: '上游 API 返回异常: ' + JSON.stringify(json) });
        }

        const insert = db.prepare('INSERT OR IGNORE INTO holidays (date, label) VALUES (?, ?)');
        let count = 0;
        const insertAll = db.transaction((entries) => {
          for (const [key, info] of entries) {
            if (info.holiday) {
              insert.run(info.date, info.name || '');
              count++;
            }
          }
        });
        insertAll(Object.entries(json.holiday || {}));
        res.json({ ok: true, year, count });
      } catch (e) {
        res.status(500).json({ error: '解析上游数据失败: ' + e.message });
      }
    });
  }).on('error', (e) => {
    res.status(502).json({ error: '连接上游 API 失败: ' + e.message });
  });
});

// DELETE /api/admin/holidays/:id
app.delete('/api/admin/holidays/:id', requireLogin, (req, res) => {
  db.prepare('DELETE FROM holidays WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

// POST /api/admin/holidays/delete-batch  → 批量删除节假日
app.post('/api/admin/holidays/delete-batch', requireLogin, (req, res) => {
  const { ids } = req.body;
  if (!Array.isArray(ids) || ids.length === 0) {
    return res.status(400).json({ error: 'ids 需为非空数组' });
  }
  const del = db.prepare('DELETE FROM holidays WHERE id = ?');
  const delMany = db.transaction((idList) => { for (const id of idList) del.run(id); });
  delMany(ids);
  res.json({ ok: true, count: ids.length });
});

// POST /api/admin/holidays/delete-all  → 清空所有节假日
app.post('/api/admin/holidays/delete-all', requireLogin, (req, res) => {
  const info = db.prepare('DELETE FROM holidays').run();
  res.json({ ok: true, count: info.changes });
});

// ── 时间规则管理 ──

// GET  /api/admin/rules
app.get('/api/admin/rules', requireLogin, (req, res) => {
  const rows = db.prepare('SELECT * FROM time_rules ORDER BY id').all();
  res.json(rows);
});

// POST /api/admin/rules  → 新增规则
app.post('/api/admin/rules', requireLogin, (req, res) => {
  const { rule_name, days_of_week, start_hour, end_hour } = req.body;
  if (!rule_name || !days_of_week || start_hour == null || end_hour == null) {
    return res.status(400).json({ error: '参数不完整' });
  }
  db.prepare(`
    INSERT INTO time_rules (rule_name, days_of_week, start_hour, end_hour)
    VALUES (?, ?, ?, ?)
  `).run(rule_name, days_of_week, Number(start_hour), Number(end_hour));
  res.json({ ok: true });
});

// PATCH /api/admin/rules/:id  → 启用/禁用规则
app.patch('/api/admin/rules/:id', requireLogin, (req, res) => {
  const { enabled } = req.body;
  db.prepare('UPDATE time_rules SET enabled = ? WHERE id = ?')
    .run(enabled ? 1 : 0, req.params.id);
  res.json({ ok: true });
});

// DELETE /api/admin/rules/:id
app.delete('/api/admin/rules/:id', requireLogin, (req, res) => {
  db.prepare('DELETE FROM time_rules WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

// ── 日期特定规则管理 ──

// GET  /api/admin/date-rules
app.get('/api/admin/date-rules', requireLogin, (req, res) => {
  const rows = db.prepare('SELECT * FROM date_rules ORDER BY date').all();
  res.json(rows);
});

// POST /api/admin/date-rules  → 新增日期规则
app.post('/api/admin/date-rules', requireLogin, (req, res) => {
  const { date, label = '', start_hour, end_hour, max_minutes = 0 } = req.body;
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).json({ error: '日期格式不正确，需为 YYYY-MM-DD' });
  }
  if (start_hour == null || end_hour == null || start_hour >= end_hour) {
    return res.status(400).json({ error: '开始时间必须小于结束时间' });
  }
  try {
    db.prepare(`
      INSERT INTO date_rules (date, label, start_hour, end_hour, max_minutes)
      VALUES (?, ?, ?, ?, ?)
    `).run(date, label, Number(start_hour), Number(end_hour), Number(max_minutes));
    res.json({ ok: true });
  } catch (e) {
    res.status(409).json({ error: '该日期规则已存在' });
  }
});

// PATCH /api/admin/date-rules/:id  → 启用/禁用
app.patch('/api/admin/date-rules/:id', requireLogin, (req, res) => {
  const { enabled } = req.body;
  db.prepare('UPDATE date_rules SET enabled = ? WHERE id = ?')
    .run(enabled ? 1 : 0, req.params.id);
  res.json({ ok: true });
});

// DELETE /api/admin/date-rules/:id
app.delete('/api/admin/date-rules/:id', requireLogin, (req, res) => {
  db.prepare('DELETE FROM date_rules WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

// ── 用户管理 ──

// GET  /api/admin/users
app.get('/api/admin/users', requireLogin, (req, res) => {
  const rows = db.prepare('SELECT * FROM users ORDER BY id').all();
  res.json(rows);
});

// POST /api/admin/users  → 新增用户
app.post('/api/admin/users', requireLogin, (req, res) => {
  const { name, id_card } = req.body;
  if (!name || !id_card || id_card.length !== 18) {
    return res.status(400).json({ error: '姓名不能为空，身份证号须为18位' });
  }
  db.prepare('INSERT INTO users (name, id_card) VALUES (?, ?)').run(name.trim(), id_card.trim().toUpperCase());
  res.json({ ok: true });
});

// DELETE /api/admin/users/:id
app.delete('/api/admin/users/:id', requireLogin, (req, res) => {
  db.prepare('DELETE FROM users WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

// ── 游戏日管理（统一节假日+周规则+日期规则）────────────────────────────────

// 判断某天是否为默认可玩日（周五/六/日，不考虑调休）
function isDefaultPlayableDay(dateStr) {
  const d = new Date(dateStr + 'T00:00:00');
  const dow = d.getDay(); // 0=Sun,1=Mon...6=Sat
  return dow === 0 || dow === 5 || dow === 6; // 周日/五/六
}

// GET /api/admin/game-days/calendar?year=&month=  → 日历视图数据
app.get('/api/admin/game-days/calendar', requireLogin, (req, res) => {
  const year  = parseInt(req.query.year)  || new Date().getFullYear();
  const month = parseInt(req.query.month) || (new Date().getMonth() + 1);
  const prefix = `${year}-${String(month).padStart(2, '0')}`;
  const rows = db.prepare(`
    SELECT date, playable, is_holiday, is_workday_override,
           start_hour, start_minute, end_hour, end_minute, max_minutes, label
    FROM game_days WHERE date LIKE ? ORDER BY date
  `).all(prefix + '%');
  const ds = db.prepare("SELECT value FROM default_config WHERE key='default_start_hour'").get();
  const de = db.prepare("SELECT value FROM default_config WHERE key='default_end_hour'").get();
  const dsm = db.prepare("SELECT value FROM default_config WHERE key='default_start_minute'").get();
  const dem = db.prepare("SELECT value FROM default_config WHERE key='default_end_minute'").get();
  const map = {};
  for (const r of rows) {
    map[r.date.slice(8, 10)] = r;
  }
  res.json({
    year, month, days: map,
    default_start_hour: parseInt(ds ? ds.value : '19'),
    default_start_minute: parseInt(dsm ? dsm.value : '0'),
    default_end_hour:   parseInt(de ? de.value : '21'),
    default_end_minute: parseInt(dem ? dem.value : '0')
  });
});

// PUT /api/admin/game-days/batch-time  → 批量设置默认游玩时间段（必须在 :date 之前）
app.put('/api/admin/game-days/batch-time', requireLogin, (req, res) => {
  const { start_hour, start_minute = 0, end_hour, end_minute = 0 } = req.body;
  if (start_hour == null || end_hour == null || Number(start_hour) > Number(end_hour) ||
      (Number(start_hour) === Number(end_hour) && Number(start_minute) >= Number(end_minute))) {
    return res.status(400).json({ error: '开始时间必须小于结束时间' });
  }
  db.prepare("INSERT OR REPLACE INTO default_config (key, value) VALUES ('default_start_hour', ?)").run(String(start_hour));
  db.prepare("INSERT OR REPLACE INTO default_config (key, value) VALUES ('default_start_minute', ?)").run(String(start_minute));
  db.prepare("INSERT OR REPLACE INTO default_config (key, value) VALUES ('default_end_hour', ?)").run(String(end_hour));
  db.prepare("INSERT OR REPLACE INTO default_config (key, value) VALUES ('default_end_minute', ?)").run(String(end_minute));
  res.json({ ok: true, default_start_hour: Number(start_hour), default_start_minute: Number(start_minute),
             default_end_hour: Number(end_hour), default_end_minute: Number(end_minute) });
});

// PUT /api/admin/game-days/:date  → 编辑某天的规则
app.put('/api/admin/game-days/:date', requireLogin, (req, res) => {
  const date = req.params.date;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return res.status(400).json({ error: '日期格式错误' });
  const { playable, start_hour, start_minute = 0, end_hour, end_minute = 0, max_minutes, label } = req.body;

  // upsert
  const existing = db.prepare('SELECT * FROM game_days WHERE date = ?').get(date);
  if (existing) {
    db.prepare(`
      UPDATE game_days SET playable=?, start_hour=?, start_minute=?, end_hour=?, end_minute=?, max_minutes=?, label=? WHERE date=?
    `).run(
      playable != null ? (playable ? 1 : 0) : existing.playable,
      start_hour != null ? Number(start_hour) : existing.start_hour,
      start_minute != null ? Number(start_minute) : (existing.start_minute || 0),
      end_hour   != null ? Number(end_hour)   : existing.end_hour,
      end_minute != null ? Number(end_minute) : (existing.end_minute || 0),
      max_minutes != null ? Number(max_minutes) : existing.max_minutes,
      label      != null ? String(label)        : existing.label,
      date
    );
  } else {
    const defPlayable = isDefaultPlayableDay(date) ? 1 : 0;
    db.prepare(`
      INSERT INTO game_days (date, playable, start_hour, start_minute, end_hour, end_minute, max_minutes, label)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(date, playable != null ? (playable ? 1 : 0) : defPlayable,
           start_hour || 19, start_minute || 0, end_hour || 21, end_minute || 0, max_minutes || 0, label || '');
  }
  res.json({ ok: true });
});

// POST /api/admin/game-days/sync  → 从 timor.tech 同步全年数据
app.post('/api/admin/game-days/sync', requireLogin, (req, res) => {
  const year = req.body.year || new Date().getFullYear();
  const url  = `https://timor.tech/api/holiday/year/${year}`;

  https.get(url, (resp) => {
    let data = '';
    resp.on('data', chunk => data += chunk);
    resp.on('end', () => {
      try {
        const json = JSON.parse(data);
        if (json.code !== 0) return res.status(502).json({ error: '上游 API 异常' });

        const upsert = db.prepare(`
          INSERT INTO game_days (date, playable, is_holiday, is_workday_override, start_hour, end_hour, max_minutes, label)
          VALUES (?, ?, ?, ?, 19, 21, 0, ?)
          ON CONFLICT(date) DO UPDATE SET
            playable = excluded.playable,
            is_holiday = excluded.is_holiday,
            is_workday_override = excluded.is_workday_override,
            label = excluded.label
        `);

        let count = 0;
        const doSync = db.transaction((entries) => {
          for (const [key, info] of entries) {
            const date = info.date;
            const isHoliday = info.holiday === true;
            const isWorkdayOverride = info.holiday === false; // 调休上班日
            const isDefaultPlayable = isDefaultPlayableDay(date);
            // 可玩 = 法定假日 OR (默认可玩日 AND 不是调休)
            const playable = isHoliday || (isDefaultPlayable && !isWorkdayOverride) ? 1 : 0;
            upsert.run(date, playable, isHoliday ? 1 : 0, isWorkdayOverride ? 1 : 0, info.name || '');
            count++;
          }
        });
        doSync(Object.entries(json.holiday || {}));
        res.json({ ok: true, year, count });
      } catch (e) {
        res.status(500).json({ error: '解析失败: ' + e.message });
      }
    });
  }).on('error', (e) => {
    res.status(502).json({ error: '连接上游失败: ' + e.message });
  });
});

// ── 日志查询 ──

// GET /api/admin/logs?page=1&limit=50
app.get('/api/admin/logs', requireLogin, (req, res) => {
  const page  = Math.max(1, parseInt(req.query.page)  || 1);
  const limit = Math.min(200, parseInt(req.query.limit) || 50);
  const offset = (page - 1) * limit;
  const total = db.prepare('SELECT COUNT(*) as c FROM sessions_log').get().c;
  const rows  = db.prepare(`
    SELECT * FROM sessions_log ORDER BY ts DESC LIMIT ? OFFSET ?
  `).all(limit, offset);
  res.json({ total, page, limit, rows });
});

// ── 修改密码 ──

// POST /api/admin/change-password
app.post('/api/admin/change-password', requireLogin, (req, res) => {
  const { oldPassword, newPassword } = req.body;
  if (!newPassword || newPassword.length < 6) {
    return res.status(400).json({ error: '新密码至少6位' });
  }
  const admin = db.prepare('SELECT * FROM admins WHERE id = ?').get(req.session.admin.id);
  if (!bcrypt.compareSync(oldPassword, admin.password)) {
    return res.status(401).json({ error: '旧密码错误' });
  }
  const hash = bcrypt.hashSync(newPassword, 10);
  db.prepare('UPDATE admins SET password = ? WHERE id = ?').run(hash, admin.id);
  res.json({ ok: true });
});

// ─── 启动时自动同步当年节假日 ─────────────────────────────────────────────────
function autoSyncCurrentYear() {
  const year = new Date().getFullYear();
  const count = db.prepare('SELECT COUNT(*) as c FROM game_days WHERE date LIKE ?').get(year + '%');
  if (count.c > 0) {
    console.log('[防沉迷后台] 游戏日数据已存在（' + count.c + ' 天），跳过自动同步');
    return;
  }
  console.log('[防沉迷后台] 首次启动，自动同步 ' + year + ' 年节假日...');
  const url = 'https://timor.tech/api/holiday/year/' + year;
  https.get(url, (resp) => {
    let data = '';
    resp.on('data', chunk => data += chunk);
    resp.on('end', () => {
      try {
        const json = JSON.parse(data);
        if (json.code !== 0) { console.log('[防沉迷后台] 自动同步失败: API 异常'); return; }
        const upsert = db.prepare(`
          INSERT INTO game_days (date, playable, is_holiday, is_workday_override, start_hour, end_hour, max_minutes, label)
          VALUES (?, ?, ?, ?, 19, 21, 0, ?)
          ON CONFLICT(date) DO UPDATE SET
            playable=excluded.playable, is_holiday=excluded.is_holiday,
            is_workday_override=excluded.is_workday_override, label=excluded.label
        `);
        let cnt = 0;
        const doSync = db.transaction((entries) => {
          for (const [key, info] of entries) {
            const d = info.date;
            const isHoliday = info.holiday === true;
            const isWorkdayOverride = info.holiday === false;
            const isDefaultPlayable = isDefaultPlayableDay(d);
            const playable = isHoliday || (isDefaultPlayable && !isWorkdayOverride) ? 1 : 0;
            upsert.run(d, playable, isHoliday ? 1 : 0, isWorkdayOverride ? 1 : 0, info.name || '');
            cnt++;
          }
        });
        doSync(Object.entries(json.holiday || {}));
        console.log('[防沉迷后台] 自动同步完成：' + year + ' 年 ' + cnt + ' 天');
      } catch (e) { console.log('[防沉迷后台] 自动同步解析失败: ' + e.message); }
    });
  }).on('error', (e) => console.log('[防沉迷后台] 自动同步连接失败: ' + e.message));
}

// ─── 启动 ─────────────────────────────────────────────────────────────────────
autoSyncCurrentYear();
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`\n╔══════════════════════════════════════════╗`);
  console.log(`║   防沉迷系统后台服务已启动                ║`);
  console.log(`║   管理面板: http://localhost:${PORT}        ║`);
  console.log(`║   API 基地址: http://localhost:${PORT}/api  ║`);
  console.log(`╚══════════════════════════════════════════╝\n`);
});
