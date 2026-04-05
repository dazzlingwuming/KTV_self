from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class HostSeparatorConfig:
    host: str
    port: int
    storage_root: Path
    engine_root: Path
    model_weights_path: Path
    model_config_path: Path
    device: str
    chunk_duration_sec: float
    chunk_overlap_sec: float


def load_config() -> HostSeparatorConfig:
    service_root = Path(__file__).resolve().parent
    default_engine_root = service_root / "engine"

    return HostSeparatorConfig(
        host=os.getenv("HOST_SEPARATOR_HOST", "0.0.0.0"),
        port=int(os.getenv("HOST_SEPARATOR_PORT", "9090")),
        storage_root=Path(os.getenv("HOST_SEPARATOR_STORAGE_ROOT", str(service_root / "storage"))),
        engine_root=Path(os.getenv("HOST_SEPARATOR_ENGINE_ROOT", str(default_engine_root))),
        model_weights_path=Path(
            os.getenv(
                "HOST_SEPARATOR_MODEL_WEIGHTS",
                str(service_root / "models" / "inst_v1e.ckpt"),
            ),
        ),
        model_config_path=Path(
            os.getenv(
                "HOST_SEPARATOR_MODEL_CONFIG",
                str(service_root / "configs" / "inst_v1e.ckpt.yaml"),
            ),
        ),
        device=os.getenv("HOST_SEPARATOR_DEVICE", "auto"),
        chunk_duration_sec=float(os.getenv("HOST_SEPARATOR_CHUNK_DURATION_SEC", "20")),
        chunk_overlap_sec=float(os.getenv("HOST_SEPARATOR_CHUNK_OVERLAP_SEC", "2")),
    )
