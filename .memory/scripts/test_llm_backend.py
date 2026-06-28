from __future__ import annotations

from pathlib import Path

import llm_backend


def test_safe_env_removes_secret_values(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("API_TOKEN", "secret")
    monkeypatch.setenv("PATH", "/bin")

    env = llm_backend._safe_env(tmp_path)

    assert env["PATH"] == "/bin"
    assert env["HOME"] == str(tmp_path)
    assert env["USERPROFILE"] == str(tmp_path)
    assert env["CODEX_INVOKED_BY"] == "wiki"
    assert "API_TOKEN" not in env


def test_prepare_workspace_copies_memory_to_temp(tmp_path: Path):
    memory = tmp_path / ".memory"
    knowledge = memory / "knowledge"
    daily = memory / "daily"
    scripts = memory / "scripts"
    knowledge.mkdir(parents=True)
    daily.mkdir()
    scripts.mkdir()
    (knowledge / "index.md").write_text("index", encoding="utf-8")
    (daily / "today.md").write_text("daily", encoding="utf-8")
    (scripts / "flush.log").write_text("log", encoding="utf-8")

    run_cwd, sync_from = llm_backend._prepare_workspace(memory, "workspace-write", tmp_path / "run")

    assert run_cwd != memory
    assert sync_from == run_cwd
    assert (run_cwd / "knowledge" / "index.md").read_text(encoding="utf-8") == "index"
    assert (run_cwd / "daily" / "today.md").read_text(encoding="utf-8") == "daily"
    assert not (run_cwd / "scripts" / "flush.log").exists()


def test_sync_memory_output_limits_synced_directories(tmp_path: Path):
    source = tmp_path / "source"
    target = tmp_path / "target"
    (source / "knowledge").mkdir(parents=True)
    (source / "daily").mkdir()
    (source / "scripts").mkdir()
    (target / "knowledge").mkdir(parents=True)
    (target / "scripts").mkdir()
    (source / "knowledge" / "index.md").write_text("new", encoding="utf-8")
    (source / "daily" / "today.md").write_text("daily", encoding="utf-8")
    (source / "scripts" / "tool.py").write_text("changed", encoding="utf-8")
    (target / "knowledge" / "index.md").write_text("old", encoding="utf-8")
    (target / "scripts" / "tool.py").write_text("original", encoding="utf-8")

    llm_backend._sync_memory_output(source, target)

    assert (target / "knowledge" / "index.md").read_text(encoding="utf-8") == "new"
    assert (target / "daily" / "today.md").read_text(encoding="utf-8") == "daily"
    assert (target / "scripts" / "tool.py").read_text(encoding="utf-8") == "original"
