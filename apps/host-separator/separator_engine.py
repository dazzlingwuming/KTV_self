from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from threading import Lock

import soundfile as sf
import torch

from config import HostSeparatorConfig


class VocalRemoverSeparatorEngine:
    def __init__(self, config: HostSeparatorConfig) -> None:
        self.config = config
        self._lock = Lock()
        self._module = None
        self._model = None
        self._model_config = None
        self._device = None

    def separate(self, input_audio_path: Path, output_dir: Path) -> dict[str, str | int]:
        self._ensure_model()
        assert self._module is not None
        assert self._model is not None
        assert self._model_config is not None
        assert self._device is not None

        audio_np, sample_rate = self._module.load_audio_ffmpeg(  # type: ignore[attr-defined]
            str(input_audio_path),
            target_sr=self._model_config.audio.sample_rate,
        )
        audio_tensor = torch.from_numpy(audio_np).float().unsqueeze(0)
        accompaniment_tensor, vocal_tensor = self._module.process_chunks(  # type: ignore[attr-defined]
            self._model,
            audio_tensor,
            self._device,
            chunk_duration=self.config.chunk_duration_sec,
            overlap_duration=self.config.chunk_overlap_sec,
            sr=sample_rate,
        )

        accompaniment_path = output_dir / "accompaniment.wav"
        vocal_path = output_dir / "vocal.wav"

        sf.write(
            str(accompaniment_path),
            accompaniment_tensor.squeeze(0).numpy().T,
            sample_rate,
        )
        sf.write(
            str(vocal_path),
            vocal_tensor.squeeze(0).numpy().T,
            sample_rate,
        )

        return {
            "sample_rate": sample_rate,
            "accompaniment_path": str(accompaniment_path),
            "vocal_path": str(vocal_path),
        }

    def _ensure_model(self) -> None:
        with self._lock:
            if self._model is not None:
                return

            module = self._load_model_module()
            device = self._resolve_device()
            model, model_config = module.get_model_from_config(  # type: ignore[attr-defined]
                str(self.config.model_config_path),
                weights_path=str(self.config.model_weights_path),
                device=device,
            )
            self._module = module
            self._device = device
            self._model = model
            self._model_config = model_config

    def _load_model_module(self):
        root = self.config.engine_root
        if not root.exists():
            raise FileNotFoundError(f"engine root not found: {root}")
        if not self.config.model_weights_path.exists():
            raise FileNotFoundError(f"model weights not found: {self.config.model_weights_path}")
        if not self.config.model_config_path.exists():
            raise FileNotFoundError(f"model config not found: {self.config.model_config_path}")

        root_str = str(root)
        if root_str not in sys.path:
            sys.path.insert(0, root_str)

        module_path = root / "model_inst_v1e.py"
        spec = importlib.util.spec_from_file_location("vocal_remover_model_inst_v1e", module_path)
        if spec is None or spec.loader is None:
            raise RuntimeError(f"unable to load model module from: {module_path}")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def _resolve_device(self):
        if self.config.device == "cpu":
            return torch.device("cpu")
        if self.config.device == "cuda":
            return torch.device("cuda")
        return torch.device("cuda" if torch.cuda.is_available() else "cpu")
