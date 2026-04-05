from __future__ import annotations

from pathlib import Path


class HostStoragePaths:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.tasks_root = self.root / "tasks"
        self.tasks_root.mkdir(parents=True, exist_ok=True)

    def task_root(self, task_id: str) -> Path:
        path = self.tasks_root / task_id
        path.mkdir(parents=True, exist_ok=True)
        return path

    def input_dir(self, task_id: str) -> Path:
        path = self.task_root(task_id) / "input"
        path.mkdir(parents=True, exist_ok=True)
        return path

    def output_dir(self, task_id: str) -> Path:
        path = self.task_root(task_id) / "output"
        path.mkdir(parents=True, exist_ok=True)
        return path

    def input_audio_path(self, task_id: str, suffix: str) -> Path:
        normalized_suffix = suffix if suffix.startswith(".") else f".{suffix}"
        return self.input_dir(task_id) / f"original_audio{normalized_suffix}"

    def accompaniment_path(self, task_id: str) -> Path:
        return self.output_dir(task_id) / "accompaniment.wav"

    def vocal_path(self, task_id: str) -> Path:
        return self.output_dir(task_id) / "vocal.wav"
