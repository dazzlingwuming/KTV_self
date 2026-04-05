from __future__ import annotations

import shutil
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass, field
from pathlib import Path
from threading import Lock

from separator_engine import VocalRemoverSeparatorEngine
from storage import HostStoragePaths


@dataclass
class SeparateTask:
    task_id: str
    song_id: str
    source_type: str
    source_id: str
    filename: str
    status: str = "pending"
    progress: int = 0
    error_message: str = ""
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    accompaniment_path: str = ""
    vocal_path: str = ""
    sample_rate: int = 0

    def to_dict(self) -> dict:
        return asdict(self)


class SeparateTaskStore:
    def __init__(self) -> None:
        self._lock = Lock()
        self._tasks: dict[str, SeparateTask] = {}

    def put(self, task: SeparateTask) -> None:
        with self._lock:
            self._tasks[task.task_id] = task

    def get(self, task_id: str) -> SeparateTask | None:
        with self._lock:
            return self._tasks.get(task_id)

    def update(self, task_id: str, **fields) -> SeparateTask | None:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return None
            for key, value in fields.items():
                setattr(task, key, value)
            task.updated_at = time.time()
            return task


class SeparateTaskManager:
    def __init__(self, storage_paths: HostStoragePaths, engine: VocalRemoverSeparatorEngine) -> None:
        self.storage_paths = storage_paths
        self.engine = engine
        self.store = SeparateTaskStore()
        self.executor = ThreadPoolExecutor(max_workers=1)

    def create_task(
        self,
        song_id: str,
        source_type: str,
        source_id: str,
        filename: str,
        input_file_path: Path,
    ) -> SeparateTask:
        task_id = uuid.uuid4().hex
        suffix = input_file_path.suffix or ".bin"
        task = SeparateTask(
            task_id=task_id,
            song_id=song_id,
            source_type=source_type,
            source_id=source_id,
            filename=filename,
        )
        self.store.put(task)

        target_input_path = self.storage_paths.input_audio_path(task_id, suffix)
        target_input_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(input_file_path, target_input_path)

        self.executor.submit(self._run_task, task_id, target_input_path)
        return task

    def get_task(self, task_id: str) -> SeparateTask | None:
        return self.store.get(task_id)

    def _run_task(self, task_id: str, input_audio_path: Path) -> None:
        self.store.update(task_id, status="processing", progress=5, error_message="")
        try:
            output_dir = self.storage_paths.output_dir(task_id)
            self.store.update(task_id, progress=25)
            result = self.engine.separate(input_audio_path, output_dir)
            self.store.update(
                task_id,
                status="success",
                progress=100,
                accompaniment_path=result["accompaniment_path"],
                vocal_path=result["vocal_path"],
                sample_rate=result["sample_rate"],
            )
        except Exception as ex:
            self.store.update(
                task_id,
                status="failed",
                progress=0,
                error_message=str(ex),
            )
