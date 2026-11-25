"""台账工具的单元测试（openspec development/audit-ledger）：在临时 Git 仓库里运行，不联网、不改动真实台账。

运行：python -m unittest discover -s scripts/audit -v
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOL = HERE / "ledger.py"
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))
import ledger  # noqa: E402

CLAUDE = {"CLAUDECODE": "1", "CLAUDE_CODE_SESSION_ID": "aaaaaaaa-1111"}


def sh(cwd, *args):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True)


class LedgerTestBase(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="ledger-test-"))
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        sh(self.root, "git", "init", "-q", "-b", "main")
        self.user("Ezio")
        sh(self.root, "git", "config", "user.email", "t@example.com")
        self.dir = self.root / "audit" / "09-remediation"
        (self.dir / "tasks").mkdir(parents=True)
        (self.dir / "tasks" / "t.md").write_text("# T\n\n## 结论 / 下一步\n\n修好了，等发版。\n\n## 过程\n\n细节\n",
                                                 encoding="utf-8")
        self.write([
            self.rec("UI-100", closed=True),
            self.rec("UI-101"),
            self.rec("UI-102"),
            self.rec("BUS-001", deferred="2000-01-01", history_owner=True),
        ])
        self.commit("base")
        sh(self.root, "git", "branch", "develop")

    # ---- 工具

    def user(self, name):
        sh(self.root, "git", "config", "user.name", name)

    def rec(self, rid, closed=False, deferred=None, history_owner=False):
        """history_owner：迁移前的历史负责人（无法确认是谁）；否则为没有负责人的待认领条目"""
        base = {"at": "2026-11-01T00:00:00+08:00", "dev": "历史", "via": "Claude", "session": "", "branch": ""}
        events = [{**base, "act": "migrate", "text": "迁移", **({} if history_owner else {"owner": False})}]
        if closed:
            events.append({**base, "act": "close", "method": "历史记录", "evidence": "见任务记录"})
        if deferred:
            events.append({**base, "act": "defer", "reason": "等依赖", "until": deferred})
        return {"id": rid, "title": f"{rid} 标题", "pri": "P2", "source": "tasks/t.md", "task": "tasks/t.md",
                "prs": [], "commits": [], "events": events}

    def write(self, records):
        os.environ["AUDIT_LEDGER_ROOT"] = str(self.root)
        ledger.dump(records)

    def commit(self, msg):
        sh(self.root, "git", "add", "-A")
        sh(self.root, "git", "commit", "-q", "--allow-empty", "-m", msg)

    def run_tool(self, *args, env=None):
        e = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}
        e.update({"AUDIT_LEDGER_ROOT": str(self.root), "AUDIT_LEDGER_NO_GH": "1", "PYTHONIOENCODING": "utf-8"})
        e.update(env or {})
        out = subprocess.run([sys.executable, str(TOOL), *args], cwd=self.root, env=e, capture_output=True,
                             text=True, encoding="utf-8")
        return out.returncode, out.stdout + out.stderr

    def claude(self, *args, session="aaaaaaaa-1111"):
        return self.run_tool(*args, env={"CLAUDECODE": "1", "CLAUDE_CODE_SESSION_ID": session})

    def codex(self, *args, task="codex-task-9"):
        return self.run_tool(*args, "--via", "Codex", "--session", task)

    def record(self, rid):
        return next(r for r in self.load() if r["id"] == rid)

    def load(self):
        text = (self.dir / "ledger.jsonl").read_text(encoding="utf-8")
        return [json.loads(x) for x in text.split("\n") if x.strip()]

    def merge_pr(self, branch, number, target="develop"):
        """在 branch 上提交后，以「Merge pull request #N from」合并提交并入 target"""
        sh(self.root, "git", "switch", "-q", "-c", branch, target)
        (self.root / f"{branch.replace('/', '_')}.txt").write_text(branch, encoding="utf-8")
        self.commit(f"fix {branch}")
        sh(self.root, "git", "switch", "-q", target)
        sh(self.root, "git", "merge", "-q", "--no-ff", branch, "-m", f"Merge pull request #{number} from org/{branch}")


class ReadTest(LedgerTestBase):
    def test_open_lists_only_unclosed(self):
        code, out = self.run_tool("open")
        self.assertEqual(code, 0)
        ids = re.findall(r"^([A-Z]+-\d+)\t", out, re.M)
        self.assertEqual(sorted(ids), ["BUS-001", "UI-101", "UI-102"])
        self.assertIn("共 3 条未关闭", out)
        self.assertIn("guide", out)

    def test_show_includes_summary(self):
        code, out = self.run_tool("show", "UI-101")
        self.assertEqual(code, 0)
        self.assertIn("状态：待认领", out)
        self.assertIn("修好了，等发版。", out)
        self.assertNotIn("细节", out)

    def test_unknown_id(self):
        code, out = self.run_tool("show", "UI-999")
        self.assertNotEqual(code, 0)
        self.assertIn("UI-999 不存在", out)
        self.assertIn("下一步", out)


class IdentityTest(LedgerTestBase):
    def test_claude_code_recorded(self):
        sh(self.root, "git", "switch", "-q", "-c", "fix/ui-101")
        code, out = self.claude("start", "UI-101")
        self.assertEqual(code, 0, out)
        e = self.record("UI-101")["events"][-1]
        self.assertEqual((e["act"], e["dev"], e["via"], e["session"], e["branch"]),
                         ("start", "Ezio", "Claude Code", "aaaaaaaa-1111", "fix/ui-101"))
        self.assertRegex(e["at"], r"\+08:00$")

    def test_missing_via_gives_full_command(self):
        code, out = self.run_tool("start", "UI-101")
        self.assertEqual(code, 2)
        self.assertIn("--via Codex --session", out)
        self.assertEqual(self.record("UI-101")["events"][-1]["act"], "migrate", "被拒绝时台账不变")


class OwnerTest(LedgerTestBase):
    def test_same_dev_switching_tool_hands_over_automatically(self):
        self.claude("start", "UI-101")
        code, out = self.codex("close", "UI-101", "--method", "本机", "--evidence", "PR #1 实测")
        self.assertEqual(code, 0, out)
        acts = [(e["act"], e["via"], e.get("auto")) for e in self.record("UI-101")["events"]]
        self.assertEqual(acts[-2:], [("handover", "Codex", True), ("close", "Codex", None)])

    def test_other_developer_cannot_close(self):
        self.claude("start", "UI-101")
        self.user("烛龙开源")
        code, out = self.codex("close", "UI-101", "--method", "本机", "--evidence", "x")
        self.assertEqual(code, 2)
        self.assertIn("当前负责人是 Ezio·Claude Code", out)
        self.assertIn("note UI-101", out)
        self.assertIn("--consent", out)
        code, _ = self.codex("note", "UI-101", "补充复现步骤")
        self.assertEqual(code, 0, "别人负责的条目可以追加备注")

    def test_handover_with_consent_then_close(self):
        self.claude("start", "UI-101")
        self.user("烛龙开源")
        code, out = self.codex("handover", "UI-101", "--reason", "Ezio 休假")
        self.assertEqual(code, 2, "没有对方同意不能接手")
        code, out = self.codex("handover", "UI-101", "--reason", "Ezio 休假", "--consent", "用户转达 Ezio 同意")
        self.assertEqual(code, 0, out)
        code, out = self.codex("close", "UI-101", "--method", "本机", "--evidence", "实测")
        self.assertEqual(code, 0, out)

    def test_history_owner_needs_consent(self):
        code, out = self.claude("defer", "BUS-001", "--reason", "继续等", "--until", "2099-01-01")
        self.assertEqual(code, 2)
        self.assertIn("迁移前的历史记录", out)
        self.assertIn("用户确认", out)

    def test_closed_item_can_be_reopened_by_anyone(self):
        code, out = self.codex("reopen", "UI-100", "--reason", "v1.2.0 复现")
        self.assertEqual(code, 0, out)
        self.assertIn("UI-100", self.run_tool("open")[1])


class ValidationTest(LedgerTestBase):
    def test_close_requires_evidence(self):
        self.claude("start", "UI-101")
        code, out = self.claude("close", "UI-101", "--method", "本机")
        self.assertEqual(code, 2)
        self.assertIn("--evidence", out)

    def test_defer_requires_date(self):
        self.claude("start", "UI-102")
        code, out = self.claude("defer", "UI-102", "--reason", "等依赖")
        self.assertEqual(code, 2)
        self.assertIn("--until", out)

    def test_new_assigns_next_number(self):
        code, out = self.claude("new", "UI", "新问题", "--pri", "P2", "--task", "tasks/t.md")
        self.assertEqual(code, 0, out)
        self.assertIn("UI-103", out)
        code, out = self.claude("new", "SEC", "第一个安全问题", "--pri", "P1")
        self.assertIn("SEC-001", out)
        ids = [r["id"] for r in self.load()]
        self.assertEqual(ids, sorted(ids, key=ledger.sort_key), "写入后按类别与编号排序")

    def test_new_task_from_repo_root_is_normalized(self):
        code, out = self.claude("new", "UI", "新问题", "--pri", "P2", "--task", "audit/09-remediation/tasks/t.md")
        self.assertEqual(code, 0, out)
        self.assertNotIn("还不存在", out)
        r = next(x for x in self.load() if x["title"] == "新问题")
        self.assertEqual(r["task"], "tasks/t.md")

    def test_closed_item_cannot_be_closed_again(self):
        code, out = self.claude("close", "UI-100", "--method", "本机", "--evidence", "x")
        self.assertEqual(code, 2)
        self.assertIn("reopen UI-100", out)


class DerivationTest(LedgerTestBase):
    def test_states_follow_git(self):
        self.claude("start", "UI-101")
        self.claude("link", "UI-101", "--pr", "7")
        self.assertIn("状态：待合并（PR #7）", self.run_tool("show", "UI-101")[1])
        self.merge_pr("fix/ui-101", 7)
        self.assertIn("状态：已合并待发布", self.run_tool("show", "UI-101")[1])
        sh(self.root, "git", "switch", "-q", "main")
        sh(self.root, "git", "merge", "-q", "--no-ff", "develop", "-m", "Merge pull request #8 from org/release/1.2.0")
        sh(self.root, "git", "tag", "v1.2.0")
        out = self.run_tool("show", "UI-101")[1]
        self.assertIn("状态：已发布待验收 v1.2.0（部署结果未核实）", out)
        before = (self.dir / "ledger.jsonl").read_text(encoding="utf-8")
        self.run_tool("open")
        self.assertEqual(before, (self.dir / "ledger.jsonl").read_text(encoding="utf-8"), "推导不改动台账")

    def test_earliest_version(self):
        self.claude("start", "UI-102")
        self.merge_pr("fix/ui-102", 9)
        sh(self.root, "git", "tag", "v1.3.0")
        sh(self.root, "git", "tag", "v1.10.0")
        self.claude("link", "UI-102", "--pr", "9")
        self.assertIn("已发布待验收 v1.3.0", self.run_tool("show", "UI-102")[1])

    def test_squash_merge_recognized(self):
        """压缩合并没有「Merge pull request」提交，标题以「(#N)」结尾；#1 不能误认 #17"""
        self.claude("start", "UI-101")
        self.claude("link", "UI-101", "--pr", "1")
        sh(self.root, "git", "switch", "-q", "develop")
        (self.root / "squash.txt").write_text("x", encoding="utf-8")
        self.commit("fix: 压缩合并的修复 (#17)")
        self.assertIn("状态：待合并（PR #1）", self.run_tool("show", "UI-101")[1])
        (self.root / "squash2.txt").write_text("y", encoding="utf-8")
        self.commit("fix: 压缩合并的修复 (#1)")
        self.assertIn("状态：已合并待发布", self.run_tool("show", "UI-101")[1])


class ConcurrencyTest(LedgerTestBase):
    def test_two_developers_edit_adjacent_items_without_conflict(self):
        self.commit("ledger")
        sh(self.root, "git", "switch", "-q", "-c", "a", "main")
        self.claude("start", "UI-101")
        self.commit("a")
        sh(self.root, "git", "switch", "-q", "-c", "b", "main")
        self.user("烛龙开源")
        self.codex("start", "UI-102")
        self.commit("b")
        sh(self.root, "git", "switch", "-q", "main")
        sh(self.root, "git", "merge", "-q", "a", "-m", "merge a")
        merged = subprocess.run(["git", "merge", "-q", "b", "-m", "merge b"], cwd=self.root, capture_output=True)
        self.assertEqual(merged.returncode, 0, merged.stdout + merged.stderr)
        self.assertEqual(self.record("UI-101")["events"][-1]["dev"], "Ezio")
        self.assertEqual(self.record("UI-102")["events"][-1]["dev"], "烛龙开源")


class CheckTest(LedgerTestBase):
    def test_passes_and_reminds_overdue_defer(self):
        code, out = self.run_tool("check")
        self.assertEqual(code, 0, out)
        self.assertIn("BUS-001：暂缓回顾日期 2000-01-01 已过", out)

    def test_missing_task_file_fails(self):
        (self.dir / "tasks" / "t.md").unlink()
        code, out = self.run_tool("check")
        self.assertEqual(code, 1)
        self.assertIn("指向的文件不存在", out)

    def test_task_written_from_repo_root_passes(self):
        path = self.dir / "ledger.jsonl"
        rec = self.rec("UI-120")
        rec["task"] = rec["source"] = "audit/09-remediation/tasks/t.md"
        path.write_text(path.read_text(encoding="utf-8") + json.dumps(rec, ensure_ascii=False) + "\n", encoding="utf-8")
        code, out = self.run_tool("check")
        self.assertNotIn("UI-120：task 指向的文件不存在", out)

    def test_duplicate_id_fails(self):
        path = self.dir / "ledger.jsonl"
        path.write_text(path.read_text(encoding="utf-8") + "\n" + json.dumps(self.rec("UI-101"), ensure_ascii=False)
                        + "\n", encoding="utf-8")
        code, out = self.run_tool("check")
        self.assertEqual(code, 1)
        self.assertIn("UI-101：ID 重复", out)


class GuideTest(unittest.TestCase):
    def test_agents_md_matches_guide(self):
        text = (REPO / "AGENTS.md").read_text(encoding="utf-8")
        m = re.search(r"<!-- audit-guide:start -->\n(.*?)\n<!-- audit-guide:end -->", text, re.S)
        self.assertIsNotNone(m, "AGENTS.md 缺少 audit-guide 标记")
        self.assertEqual(m.group(1).strip(), ledger.GUIDE.strip(),
                         "AGENTS.md 的台账规则与 `ledger.py guide` 不一致：改规则时改 ledger.py 的 GUIDE，再同步到 AGENTS.md")


if __name__ == "__main__":
    unittest.main()
