#!/usr/bin/env python3
"""审查整改台账工具（openspec development/audit-ledger）。

台账只给 AI 用：数据在 audit/09-remediation/ledger.jsonl，每行一个永久 ID、记录之间空一行；
只经本工具读写。合并、发布状态按 Git 实时推导，不手工维护。运行 `guide` 查看使用规则。
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

PROG = "python scripts/audit/ledger.py"
BEIJING = timezone(timedelta(hours=8))
CATEGORIES = ["BUS", "UI", "AI", "SEC", "OPS", "DEP", "DOC"]
PRIORITIES = ["P1", "P2", "P3"]
CLOSE_METHODS = ["本机", "生产", "自动化", "文档"]
ACT_NAMES = {"migrate": "迁移", "start": "认领", "handover": "接手", "note": "备注", "link": "关联",
             "defer": "暂缓", "na": "不适用", "close": "关闭", "reopen": "重开"}
HISTORY_DEV = "历史"
ID_RE = re.compile(r"^([A-Z]{2,5})-(\d{3,})$")

# 使用规则：唯一来源。AGENTS.md「审查整改记录」两个标记之间的内容必须与之一致（单元测试校验）。
GUIDE = f"""审查整改台账（audit/）使用规则
1. 范围：缺陷、审查发现、用户反馈的问题记在 audit/；新功能走 OpenSpec，功能验收台账放在变更目录。整改进度只记在 audit/，不写进知识库。
2. 开工先运行 `{PROG} open` 看未关闭条目，`show <ID>` 看单条；不要通读 ledger.jsonl。
3. 台账只经本工具写入，不手改 ledger.jsonl。Claude Code 自动识别身份；Codex 每条写入命令加 `--via Codex --session <当前任务ID>`；其他工具加 `--via <名称>`。
4. 新问题先用 `open`、`show` 查重，确认没有再 `new <类别> "<标题>" --pri P1|P2|P3 --task <任务记录>`（类别 BUS/UI/AI/SEC/OPS/DEP/DOC）。修复中发现未登记的问题一并新建并处理，不要只写「未处理」。
5. 认领用 `start <ID>`；提 PR 后 `link <ID> --pr <编号>`。合并、发布状态由工具按 Git 自动推导，不手工维护。
6. 过程写在 audit/09-remediation/tasks/ 的任务记录里（模板见 tasks/README.md），开头的「结论 / 下一步」随进展更新。
7. 关闭：验收后 `close <ID> --method 本机|生产|自动化|文档 --evidence "<PR、CI、测试或实测说明>"`。暂缓 `defer <ID> --reason ... --until YYYY-MM-DD`；不适用 `na <ID> --reason ... --confirmer ...`；复发 `reopen <ID> --reason ...`。
8. 负责人：同一开发者换工具或会话时，工具自动记录接手。别人负责的条目不改状态，只能 `note`；接手须对方同意，再 `handover <ID> --reason ... --consent "<同意来源>"`。迁移前的历史条目也要先确认归属。
9. 汇报：只讲自己负责、需要处理的条目；别人负责的最多一句说明归属；暂缓项不重复列出。
10. 命令被拒绝时按提示里的下一步执行。命令细节见 audit/09-remediation/README.md。"""


class Refused(Exception):
    """写入被拒绝：message 说明原因，hint 给出下一步"""

    def __init__(self, message, hint=""):
        super().__init__(message)
        self.hint = hint


# ---------------------------------------------------------------- 路径与数据

def repo_root() -> Path:
    env = os.environ.get("AUDIT_LEDGER_ROOT")
    return Path(env).resolve() if env else Path(__file__).resolve().parents[2]


def ledger_dir() -> Path:
    return repo_root() / "audit" / "09-remediation"


def ledger_path() -> Path:
    return ledger_dir() / "ledger.jsonl"


LEDGER_PREFIX = "audit/09-remediation/"


def norm_ref(ref: str) -> str:
    """任务记录路径统一写成相对 audit/09-remediation/ 的形式；从仓库根写起的去掉前缀"""
    ref = (ref or "").strip().replace("\\", "/")
    return ref[len(LEDGER_PREFIX):] if ref.startswith(LEDGER_PREFIX) else ref


def ref_path(ref: str) -> Path:
    """task / source 指向的文件；兼容早先从仓库根写起的路径"""
    return ledger_dir() / norm_ref(ref).split("#")[0]


def sort_key(rid: str):
    m = ID_RE.match(rid)
    cat, num = (m.group(1), int(m.group(2))) if m else (rid, 0)
    order = CATEGORIES.index(cat) if cat in CATEGORIES else len(CATEGORIES)
    return order, cat, num


def load() -> list:
    path = ledger_path()
    if not path.exists():
        return []
    records = []
    for no, line in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
        if not line.strip():
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError as e:
            raise SystemExit(f"ledger.jsonl 第 {no} 行不是合法 JSON：{e}。运行 `{PROG} check` 定位，或从 Git 恢复该行。")
    return records


def dump(records: list):
    """按 ID 排序，每行一条、之间空一行；写临时文件后原子替换"""
    records = sorted(records, key=lambda r: sort_key(r["id"]))
    text = "\n\n".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) for r in records) + "\n"
    path = ledger_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".ledger-", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def find(records: list, rid: str) -> dict:
    rid = rid.upper()
    for r in records:
        if r["id"] == rid:
            return r
    raise Refused(f"{rid} 不存在", f"运行 `{PROG} open` 查看未关闭条目；新问题用 `{PROG} new <类别> \"<标题>\" --pri P2`")


# ---------------------------------------------------------------- Git

def git(*args):
    """运行 git，成功返回输出（去掉首尾空白），失败返回 None"""
    try:
        out = subprocess.run(["git", *args], cwd=repo_root(), capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return out.stdout.strip() if out.returncode == 0 else None


def ref_exists(ref: str) -> bool:
    return git("rev-parse", "--verify", "-q", ref + "^{commit}") is not None


def base_refs() -> list:
    refs = []
    for name in ("develop", "main"):
        for ref in (f"origin/{name}", name):
            if ref_exists(ref):
                refs.append(ref)
                break
    return refs


def version_tuple(tag: str):
    m = re.match(r"^v(\d+)\.(\d+)\.(\d+)$", tag)
    return tuple(int(x) for x in m.groups()) if m else None


class Derivation:
    """按 Git 推导合并、发布状态；同一次命令内缓存查询结果"""

    def __init__(self):
        self.refs = base_refs()
        self._pr = {}
        self._tag = {}
        self._deploy = {}

    def pr_commit(self, pr: int):
        if pr not in self._pr:
            sha = None
            if self.refs:
                # 合并提交「Merge pull request #N from」，或压缩合并的标题「… (#N)」
                sha = git("log", "-1", "--format=%H", "-E", f"--grep=^Merge pull request #{pr} from ",
                          rf"--grep=\(#{pr}\)$", *self.refs) or None
            self._pr[pr] = sha
        return self._pr[pr]

    def merged(self, sha: str) -> bool:
        if not sha or git("cat-file", "-e", sha + "^{commit}") is None:
            return False
        return any(git("merge-base", "--is-ancestor", sha, ref) is not None for ref in self.refs)

    def first_tag(self, sha: str):
        if sha not in self._tag:
            tags = (git("tag", "--contains", sha, "--list", "v*") or "").split()
            versions = sorted((t for t in tags if version_tuple(t)), key=version_tuple)
            self._tag[sha] = versions[0] if versions else None
        return self._tag[sha]

    def deploy(self, version: str) -> str:
        """success / failure / unknown"""
        if version in self._deploy:
            return self._deploy[version]
        result = "unknown"
        if not os.environ.get("AUDIT_LEDGER_NO_GH") and shutil.which("gh"):
            for branch in (f"release/{version[1:]}", f"hotfix/{version[1:]}"):
                try:
                    out = subprocess.run(["gh", "run", "list", "--workflow", "release.yml", "--branch", branch,
                                          "--limit", "1", "--json", "conclusion"], cwd=repo_root(),
                                         capture_output=True, text=True, encoding="utf-8", timeout=20)
                    runs = json.loads(out.stdout or "[]") if out.returncode == 0 else []
                except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
                    runs = []
                if runs:
                    result = {"success": "success", "failure": "failure"}.get(runs[0].get("conclusion"), "unknown")
                    break
        self._deploy[version] = result
        return result


def terminal_state(r: dict):
    """最近一次判断节点：已关闭 / 不适用 / 暂缓；认领、接手后恢复、重开清除"""
    state = None
    for e in r.get("events", []):
        act = e.get("act")
        if act == "close":
            state = "已关闭"
        elif act == "na":
            state = "不适用"
        elif act == "defer":
            state = "暂缓"
        elif act in ("reopen", "start"):
            state = None
    return state


def owner(r: dict):
    """当前负责人 (dev, via, session)；关闭或不适用后为 None"""
    cur = None
    for e in r.get("events", []):
        act = e.get("act")
        if act in ("migrate", "start", "handover", "reopen"):
            if act == "migrate" and not e.get("owner", True):
                continue
            cur = (e.get("dev", ""), e.get("via", ""), e.get("session", ""))
        elif act in ("close", "na"):
            cur = None
    return cur


def derive(r: dict, d: Derivation) -> str:
    state = terminal_state(r)
    if state:
        return state
    items = [("PR #" + str(p), d.pr_commit(p)) for p in r.get("prs", [])]
    items += [(c[:8], c) for c in r.get("commits", [])]
    if not items:
        return "处理中" if owner(r) else "待认领"
    if not d.refs:
        return "处理中（本地没有 develop / main，无法推导）"
    pending = [label for label, sha in items if not d.merged(sha)]
    if pending:
        return "待合并（" + "、".join(pending) + "）"
    tags = [d.first_tag(sha) for _, sha in items]
    if any(t is None for t in tags):
        return "已合并待发布"
    version = max(tags, key=version_tuple)
    result = d.deploy(version)
    if result == "failure":
        return f"部署失败 {version}"
    return f"已发布待验收 {version}" + ("" if result == "success" else "（部署结果未核实）")


# ---------------------------------------------------------------- 操作人

def now() -> str:
    return datetime.now(BEIJING).replace(microsecond=0).isoformat()


def identity(args) -> dict:
    dev = (getattr(args, "dev", None) or git("config", "user.name") or "").strip()
    via = (getattr(args, "via", None) or "").strip()
    session = (getattr(args, "session", None) or "").strip()
    if not via and os.environ.get("CLAUDECODE"):
        via = "Claude Code"
    if via == "Claude Code" and not session:
        session = os.environ.get("CLAUDE_CODE_SESSION_ID", "")
    if not dev:
        raise Refused("取不到开发者姓名（git config user.name 为空）",
                      f"先设置 `git config user.name \"<你的名字>\"`，或在命令后加 `--dev \"<你的名字>\"`")
    if not via:
        raise Refused("无法识别你使用的工具",
                      f"Codex 请在命令后加 `--via Codex --session <当前任务ID>`，例如 "
                      f"`{PROG} start UI-104 --via Codex --session <任务ID>`；人工操作加 `--via 手工`")
    if via not in ("手工",) and not session:
        raise Refused(f"{via} 需要会话或任务 ID", f"在命令后加 `--session <当前会话或任务ID>`")
    branch = git("branch", "--show-current") or ""
    return {"dev": dev, "via": via, "session": session, "branch": branch}


def owner_label(o) -> str:
    if not o:
        return "无"
    dev, via, session = o
    return f"{dev}·{via}" + (f"·{session[:8]}" if session else "")


def event(who: dict, act: str, **extra) -> dict:
    e = {"at": now(), "act": act, **who}
    e.update({k: v for k, v in extra.items() if v not in (None, "", [])})
    return e


def claim(r: dict, who: dict, action: str, consent: str = "") -> list:
    """按负责人规则返回需要先追加的事件（自动接手）；不允许时抛出 Refused"""
    cur = owner(r)
    if cur is None:
        return [event(who, "start", text=f"{action}时认领")] if action not in ("认领", "重开") else []
    dev, via, session = cur
    if (dev, via, session) == (who["dev"], who["via"], who["session"]):
        return []
    if dev == who["dev"] and dev != HISTORY_DEV:
        text = f"自动接手：从 {via}{'·' + session[:8] if session else ''} 转到 {who['via']}" \
               f"{'·' + who['session'][:8] if who['session'] else ''}"
        return [event(who, "handover", text=text, auto=True)]
    if consent:
        return []
    if dev == HISTORY_DEV:
        raise Refused(f"{r['id']} 的负责人是迁移前的历史记录（{via}），无法确认是哪位开发者，你不能{action}",
                      f"先向你的用户确认这条是否由他负责（或对方已同意转交），确认后运行 "
                      f"`{PROG} handover {r['id']} --reason \"<原因>\" --consent \"<确认来源，例如：用户确认是本人的历史条目>\"`；"
                      f"只补充信息用 `{PROG} note {r['id']} \"<内容>\"`")
    raise Refused(f"{r['id']} 当前负责人是 {owner_label(cur)}，你不能{action}",
                  f"可以追加备注：`{PROG} note {r['id']} \"<内容>\"`；如需接手，先向你的用户确认对方已同意，再运行 "
                  f"`{PROG} handover {r['id']} --reason \"<原因>\" --consent \"<同意来源>\"`")


def save(records: list, r: dict, events: list, message: str):
    r.setdefault("events", []).extend(events)
    dump(records)
    print(message)


# ---------------------------------------------------------------- 命令

def task_summary(r: dict) -> str:
    task = r.get("task") or ""
    if not task:
        return "（没有任务记录）"
    path = ref_path(task)
    if not path.exists():
        return f"（任务记录不存在：{task}）"
    lines = path.read_text(encoding="utf-8").split("\n")
    for i, line in enumerate(lines):
        if re.match(r"^##\s*结论\s*/\s*下一步", line):
            end = next((j for j in range(i + 1, len(lines)) if lines[j].startswith("## ")), len(lines))
            return "\n".join(lines[i:end]).strip()
    return "\n".join(lines[:40]).strip() + "\n…（没有「结论 / 下一步」一节，以上为前 40 行）"


def last_update(r: dict) -> str:
    events = r.get("events", [])
    return events[-1]["at"][:10] if events else ""


def fetch_hint() -> str:
    head = git("rev-parse", "--git-path", "FETCH_HEAD")
    if head:
        p = Path(head) if Path(head).is_absolute() else repo_root() / head
        if p.exists():
            t = datetime.fromtimestamp(p.stat().st_mtime, BEIJING).strftime("%Y-%m-%d %H:%M")
            return f"合并、发布状态按本地 Git 推导（最近一次 fetch：{t}）；必要时先 git fetch。"
    return "合并、发布状态按本地 Git 推导；必要时先 git fetch。"


def cmd_open(args):
    records = load()
    d = Derivation()
    me = None
    if args.mine:
        me = (getattr(args, "dev", None) or git("config", "user.name") or "").strip()
    rows = []
    for r in records:
        state = terminal_state(r)
        if state in ("已关闭", "不适用"):
            continue
        o = owner(r)
        if me is not None and (not o or o[0] != me):
            continue
        status = derive(r, d)
        if args.status and not status.startswith(args.status):
            continue
        rows.append((status.startswith("暂缓"), r.get("pri", "P9"), sort_key(r["id"]), r, status, o))
    rows.sort(key=lambda x: (x[0], x[1], x[2]))
    for _, pri, _, r, status, o in rows:
        print(f"{r['id']}\t{pri}\t{status}\t{owner_label(o)}\t{last_update(r)}\t{r.get('title', '')}")
    print(f"共 {len(rows)} 条未关闭。{fetch_hint()}")
    print(f"使用规则：`{PROG} guide`")


def cmd_show(args):
    records = load()
    r = find(records, args.id)
    d = Derivation()
    print(f"{r['id']}  {r.get('pri', '')}  {r.get('title', '')}")
    print(f"状态：{derive(r, d)}")
    print(f"负责人：{owner_label(owner(r))}")
    if r.get("prs") or r.get("commits"):
        print("关联：" + "、".join([f"PR #{p}" for p in r.get("prs", [])] + [c[:8] for c in r.get("commits", [])]))
    print(f"原专题：{r.get('source', '')}")
    print(f"任务记录：{r.get('task', '') or '无'}")
    print("处理记录：")
    for e in r.get("events", []):
        who = " · ".join(x for x in (e.get("dev"), e.get("via"), (e.get("session") or "")[:8], e.get("branch")) if x)
        extra = []
        for k in ("method", "evidence", "until", "confirmer", "consent", "reason", "text"):
            if e.get(k):
                extra.append(f"{k}={e[k]}" if k not in ("text", "reason") else e[k])
        print(f"  {e.get('at', '')[:16].replace('T', ' ')}  {ACT_NAMES.get(e.get('act'), e.get('act'))}  {who}  "
              + "；".join(extra))
    print("任务记录摘要：")
    print(task_summary(r))
    print(fetch_hint())


def cmd_guide(_args):
    print(GUIDE)


def cmd_new(args):
    cat = args.category.upper()
    if cat not in CATEGORIES:
        raise Refused(f"类别 {cat} 不存在", f"可用类别：{'/'.join(CATEGORIES)}")
    if args.pri not in PRIORITIES:
        raise Refused(f"优先级 {args.pri} 不合法", "用 --pri P1、P2 或 P3")
    who = identity(args)
    records = load()
    nums = [int(ID_RE.match(r["id"]).group(2)) for r in records if ID_RE.match(r["id"]) and r["id"].startswith(cat + "-")]
    rid = f"{cat}-{(max(nums) + 1) if nums else 1:03d}"
    task = norm_ref(args.task or "")
    r = {"id": rid, "title": args.title.strip(), "pri": args.pri, "source": task, "task": task,
         "prs": [], "commits": [], "events": []}
    records.append(r)
    save(records, r, [event(who, "start", text="新建并认领")], f"已新建 {rid} 并由 {owner_label((who['dev'], who['via'], who['session']))} 认领")
    if task and not ref_path(task).exists():
        print(f"提醒：任务记录 {task} 还不存在，请按 audit/09-remediation/tasks/README.md 的模板创建，否则 check 会失败")


def ensure_open(r: dict, action: str):
    state = terminal_state(r)
    if state in ("已关闭", "不适用"):
        raise Refused(f"{r['id']} 已是「{state}」，不能{action}",
                      f"问题复发时运行 `{PROG} reopen {r['id']} --reason \"<原因>\"`；只补充信息用 `note`")


def cmd_start(args):
    who = identity(args)
    records = load()
    r = find(records, args.id)
    ensure_open(r, "认领")
    cur = owner(r)
    if cur == (who["dev"], who["via"], who["session"]) and terminal_state(r) != "暂缓":
        print(f"{r['id']} 已由你负责，无需认领")
        return
    pre = claim(r, who, "认领")
    events = pre if pre else [event(who, "start", text=args.text)]
    if pre and terminal_state(r) == "暂缓":
        events.append(event(who, "start", text=args.text or "恢复处理"))
    save(records, r, events, f"{r['id']} 由 {owner_label((who['dev'], who['via'], who['session']))} 负责")


def cmd_handover(args):
    who = identity(args)
    records = load()
    r = find(records, args.id)
    ensure_open(r, "接手")
    cur = owner(r)
    if cur and cur[0] != who["dev"] and not args.consent:
        claim(r, who, "接手")
    e = event(who, "handover", reason=args.reason, consent=args.consent,
              text=f"从 {owner_label(cur)} 接手")
    save(records, r, [e], f"{r['id']} 已由 {owner_label((who['dev'], who['via'], who['session']))} 接手")


def cmd_link(args):
    who = identity(args)
    records = load()
    r = find(records, args.id)
    prs = [p for p in (args.pr or []) if p not in r.setdefault("prs", [])]
    commits = [c for c in (args.commit or []) if c not in r.setdefault("commits", [])]
    if not prs and not commits:
        raise Refused("没有新的 PR 或提交需要关联", f"例如 `{PROG} link {r['id']} --pr 375`")
    r["prs"].extend(prs)
    r["commits"].extend(commits)
    save(records, r, [event(who, "link", prs=prs, commits=commits)],
         f"{r['id']} 已关联 " + "、".join([f"PR #{p}" for p in prs] + [c[:8] for c in commits]))


def cmd_note(args):
    who = identity(args)
    records = load()
    r = find(records, args.id)
    save(records, r, [event(who, "note", text=args.text)], f"{r['id']} 已追加备注")


def cmd_defer(args):
    who = identity(args)
    try:
        until = date.fromisoformat(args.until) if args.until else None
    except ValueError:
        until = None
    if not args.reason or until is None:
        raise Refused("暂缓需要原因与回顾日期（YYYY-MM-DD）",
                      f"例如 `{PROG} defer {args.id.upper()} --reason \"<原因与恢复条件>\" --until 2026-12-31`")
    records = load()
    r = find(records, args.id)
    ensure_open(r, "暂缓")
    pre = claim(r, who, "暂缓")
    save(records, r, pre + [event(who, "defer", reason=args.reason, until=until.isoformat())],
         f"{r['id']} 已暂缓，{until.isoformat()} 回顾")


def cmd_na(args):
    who = identity(args)
    if not args.reason or not args.confirmer:
        raise Refused("判为不适用需要理由与确认人",
                      f"例如 `{PROG} na {args.id.upper()} --reason \"<版本/环境/业务条件与证据>\" --confirmer \"<确认人>\"`")
    records = load()
    r = find(records, args.id)
    ensure_open(r, "判为不适用")
    pre = claim(r, who, "判为不适用")
    save(records, r, pre + [event(who, "na", reason=args.reason, confirmer=args.confirmer)], f"{r['id']} 已判为不适用")


def cmd_close(args):
    who = identity(args)
    if args.method not in CLOSE_METHODS or not args.evidence:
        raise Refused("关闭需要验收方式与证据",
                      f"例如 `{PROG} close {args.id.upper()} --method 本机|生产|自动化|文档 "
                      f"--evidence \"<PR、CI、测试或实测说明>\"`")
    records = load()
    r = find(records, args.id)
    ensure_open(r, "关闭")
    pre = claim(r, who, "关闭")
    status = derive(r, Derivation())
    save(records, r, pre + [event(who, "close", method=args.method, evidence=args.evidence)], f"{r['id']} 已关闭")
    if status.startswith("待合并"):
        print(f"提醒：关闭时关联的改动还是「{status}」，确认验收证据覆盖了实际要上线的代码")


def cmd_reopen(args):
    who = identity(args)
    if not args.reason:
        raise Refused("重开需要原因", f"例如 `{PROG} reopen {args.id.upper()} --reason \"<复发现象与证据>\"`")
    records = load()
    r = find(records, args.id)
    if terminal_state(r) not in ("已关闭", "不适用"):
        raise Refused(f"{r['id']} 不是已关闭或不适用，不需要重开", f"运行 `{PROG} show {r['id']}` 查看当前状态")
    save(records, r, [event(who, "reopen", reason=args.reason)],
         f"{r['id']} 已重开，由 {owner_label((who['dev'], who['via'], who['session']))} 负责")


REQUIRED_EVENT_FIELDS = ("at", "act", "dev", "via")


def cmd_check(_args):
    errors, warnings = [], []
    path = ledger_path()
    if not path.exists():
        print(f"✗ 找不到 {path}")
        return 1
    seen = set()
    records = []
    for no, line in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
        if not line.strip():
            continue
        try:
            records.append((no, json.loads(line)))
        except json.JSONDecodeError as e:
            errors.append(f"第 {no} 行不是合法 JSON：{e}")
    today = datetime.now(BEIJING).date()
    for no, r in records:
        rid = r.get("id", "")
        where = f"{rid or '第 ' + str(no) + ' 行'}"
        if not ID_RE.match(rid):
            errors.append(f"{where}：ID 不符合「类别-三位编号」")
        elif rid in seen:
            errors.append(f"{where}：ID 重复")
        seen.add(rid)
        for k in ("title", "pri", "events"):
            if not r.get(k):
                errors.append(f"{where}：缺少 {k}")
        if r.get("pri") and r["pri"] not in PRIORITIES:
            errors.append(f"{where}：优先级 {r['pri']} 不合法")
        for k in ("prs", "commits"):
            if not isinstance(r.get(k, []), list):
                errors.append(f"{where}：{k} 应为列表")
        if not all(isinstance(p, int) for p in r.get("prs", []) or []):
            errors.append(f"{where}：prs 应为 PR 编号")
        for k in ("task", "source"):
            ref = r.get(k) or ""
            if ref and not ref_path(ref).exists():
                errors.append(f"{where}：{k} 指向的文件不存在：{ref}")
        for i, e in enumerate(r.get("events", []) or [], 1):
            missing = [f for f in REQUIRED_EVENT_FIELDS if not e.get(f)]
            if missing:
                errors.append(f"{where}：第 {i} 条处理记录缺少 {'、'.join(missing)}")
            if e.get("act") not in ACT_NAMES:
                errors.append(f"{where}：第 {i} 条处理记录动作 {e.get('act')} 不认识")
            if e.get("act") == "defer" and not e.get("until"):
                errors.append(f"{where}：暂缓记录缺少回顾日期")
            if e.get("act") == "close" and not (e.get("method") and e.get("evidence")):
                errors.append(f"{where}：关闭记录缺少验收方式或证据")
        if terminal_state(r) == "暂缓":
            until = next((e.get("until") for e in reversed(r.get("events", [])) if e.get("act") == "defer"), None)
            try:
                if until and date.fromisoformat(until) < today:
                    warnings.append(f"{rid}：暂缓回顾日期 {until} 已过，需要重新评估")
            except ValueError:
                errors.append(f"{where}：暂缓回顾日期格式不对：{until}")
    ids = [r.get("id", "") for _, r in records]
    if ids != sorted(ids, key=sort_key):
        warnings.append(f"记录没有按类别与编号排序；任一写入命令都会重新排序")
    for w in warnings:
        print(f"提醒：{w}")
    for e in errors:
        print(f"✗ {e}")
    print(f"共 {len(records)} 条，{len(errors)} 个错误，{len(warnings)} 个提醒")
    return 1 if errors else 0


# ---------------------------------------------------------------- 入口

def build_parser():
    who = argparse.ArgumentParser(add_help=False)
    who.add_argument("--via", help="使用的工具：Claude Code 自动识别；Codex 传 Codex；人工传 手工")
    who.add_argument("--session", help="会话或任务 ID（Codex 传当前任务 ID）")
    who.add_argument("--dev", help="开发者姓名，默认取 git config user.name")

    p = argparse.ArgumentParser(prog=PROG, description="审查整改台账（只给 AI 用）。先看 `guide`。")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("open", help="列出未关闭条目", epilog=f"示例：{PROG} open --mine")
    s.add_argument("--mine", action="store_true", help="只看当前负责人是自己的")
    s.add_argument("--status", help="按状态前缀筛选，例如 暂缓、已发布待验收")
    s.add_argument("--dev", help=argparse.SUPPRESS)
    s.set_defaults(func=cmd_open)

    s = sub.add_parser("show", help="查看单个 ID", epilog=f"示例：{PROG} show UI-103")
    s.add_argument("id")
    s.set_defaults(func=cmd_show)

    s = sub.add_parser("guide", help="使用规则")
    s.set_defaults(func=cmd_guide)

    s = sub.add_parser("new", parents=[who], help="新建问题并认领",
                       epilog=f"示例：{PROG} new UI \"列表筛选丢失\" --pri P2 --task tasks/2026-11-20-UI-104-filter.md")
    s.add_argument("category", help="/".join(CATEGORIES))
    s.add_argument("title")
    s.add_argument("--pri", required=True, help="P1、P2 或 P3")
    s.add_argument("--task", help="任务记录，相对 audit/09-remediation/")
    s.set_defaults(func=cmd_new)

    s = sub.add_parser("start", parents=[who], help="认领", epilog=f"示例：{PROG} start UI-104")
    s.add_argument("id")
    s.add_argument("--text", help="说明")
    s.set_defaults(func=cmd_start)

    s = sub.add_parser("handover", parents=[who], help="接手（别人负责的须带 --consent）",
                       epilog=f"示例：{PROG} handover UI-104 --reason \"对方休假\" --consent \"用户转达对方同意\"")
    s.add_argument("id")
    s.add_argument("--reason", required=True)
    s.add_argument("--consent", help="对方同意的来源")
    s.set_defaults(func=cmd_handover)

    s = sub.add_parser("link", parents=[who], help="关联 PR 或提交", epilog=f"示例：{PROG} link UI-104 --pr 380")
    s.add_argument("id")
    s.add_argument("--pr", type=int, action="append")
    s.add_argument("--commit", action="append")
    s.set_defaults(func=cmd_link)

    s = sub.add_parser("note", parents=[who], help="追加备注（任何人可用）", epilog=f"示例：{PROG} note UI-104 \"补充复现步骤\"")
    s.add_argument("id")
    s.add_argument("text")
    s.set_defaults(func=cmd_note)

    s = sub.add_parser("defer", parents=[who], help="暂缓",
                       epilog=f"示例：{PROG} defer DEP-005 --reason \"随 Umi 升级处理\" --until 2026-12-31")
    s.add_argument("id")
    s.add_argument("--reason")
    s.add_argument("--until")
    s.set_defaults(func=cmd_defer)

    s = sub.add_parser("na", parents=[who], help="判为不适用",
                       epilog=f"示例：{PROG} na OPS-004 --reason \"生产未启用该迁移\" --confirmer Ezio")
    s.add_argument("id")
    s.add_argument("--reason")
    s.add_argument("--confirmer")
    s.set_defaults(func=cmd_na)

    s = sub.add_parser("close", parents=[who], help="关闭",
                       epilog=f"示例：{PROG} close UI-104 --method 本机 --evidence \"PR #380；BrowserOS 实测列表筛选保留\"")
    s.add_argument("id")
    s.add_argument("--method", help="/".join(CLOSE_METHODS))
    s.add_argument("--evidence")
    s.set_defaults(func=cmd_close)

    s = sub.add_parser("reopen", parents=[who], help="重开", epilog=f"示例：{PROG} reopen UI-104 --reason \"v1.33.0 复现\"")
    s.add_argument("id")
    s.add_argument("--reason")
    s.set_defaults(func=cmd_reopen)

    s = sub.add_parser("check", help="自检（CI 使用）")
    s.set_defaults(func=cmd_check)
    return p


def main(argv=None) -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass
    args = build_parser().parse_args(argv)
    try:
        return args.func(args) or 0
    except Refused as e:
        print(f"拒绝：{e}", file=sys.stderr)
        if e.hint:
            print(f"下一步：{e.hint}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
